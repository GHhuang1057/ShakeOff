package io.github.geekhonize.shakeoff.monitor

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.geekhonize.shakeoff.strategy.ControlMode
import io.github.geekhonize.shakeoff.strategy.ModeManager
import java.util.concurrent.TimeUnit

/**
 * 周期性广告巡检。
 *
 * 广播接收器依赖进程存活，而系统可能在后台杀进程，
 * 因此额外用 WorkManager 定期全量扫描已安装应用作为兜底。
 */
class AdwareScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AdwareScanWorker"
        private const val WORK_NAME = "shakeoff_adware_scan"

        /** 最小执行间隔受系统限制，实际至少 15 分钟 */
        private const val INTERVAL_MINUTES = 30L

        /**
         * 开启周期性巡检。
         */
        fun schedule(context: Context) {
            if (!MonitorPrefs.isMonitorEnabled(context)) {
                cancel(context)
                return
            }

            val request = PeriodicWorkRequestBuilder<AdwareScanWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                Log.e(TAG, "注册周期任务失败", e)
            }
        }

        /**
         * 取消周期性巡检。
         */
        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!MonitorPrefs.isMonitorEnabled(context)) return Result.success()

        return try {
            scanInstalledApps(context)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "巡检失败", e)
            Result.retry()
        }
    }

    /**
     * 扫描已安装应用，命中特征库的未处理项发通知。
     */
    private suspend fun scanInstalledApps(context: Context) {
        val pm = context.packageManager
        val packages = try {
            pm.getInstalledPackages(0)
        } catch (e: Exception) {
            return
        }

        val matcher = AdwareMatcher.getInstance(context)
        val names = packages.map { it.packageName }

        val hits = matcher.matchAll(names)
        if (hits.isEmpty()) return

        // 已有记录的包不再重复通知
        val recorded = EventLog.getInstance().getAll()
            .map { it.target }
            .toSet()

        val modeManager = ModeManager(context)
        val result = modeManager.resolve()
        val strategy = modeManager.strategyFor(result.activeMode)
        val autoBlock = MonitorPrefs.isAutoBlockEnabled(context) &&
            result.activeMode != ControlMode.ACCESSIBILITY

        for ((pkg, entry) in hits) {
            if (recorded.contains(pkg)) continue

            EventLog.getInstance().add(pkg, "巡检发现广告应用", true)
            AdwareNotifier.notifyAdwareDetected(context, entry)

            if (autoBlock) {
                val ok = try {
                    strategy.setBlocked(pkg, true)
                } catch (e: Exception) {
                    false
                }
                EventLog.getInstance().add(pkg, "自动禁用传感器权限", ok)
            }
        }
    }
}
