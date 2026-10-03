package io.github.geekhonize.shakeoff.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.github.geekhonize.shakeoff.strategy.ControlMode
import io.github.geekhonize.shakeoff.strategy.ModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 监控设置持久化。
 */
object MonitorPrefs {

    private const val PREF_NAME = "shakeoff_monitor"

    /** 是否开启实时监控 */
    const val KEY_MONITOR_ENABLED = "monitor_enabled"

    /** 是否自动拦截（检测到广告后自动禁用其传感器权限） */
    const val KEY_AUTO_BLOCK = "auto_block"

    /** 是否启用 Shell 守护进程 */
    const val KEY_DAEMON_ENABLED = "daemon_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 实时监控是否开启。
     */
    fun isMonitorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MONITOR_ENABLED, false)

    fun setMonitorEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITOR_ENABLED, value).apply()
    }

    /**
     * 自动拦截是否开启。
     */
    fun isAutoBlockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_BLOCK, false)

    fun setAutoBlockEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_BLOCK, value).apply()
    }

    /**
     * Shell 守护进程是否开启。
     */
    fun isDaemonEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DAEMON_ENABLED, false)

    fun setDaemonEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_DAEMON_ENABLED, value).apply()
    }
}

/**
 * 监听新应用安装。
 *
 * 收到 [Intent.ACTION_PACKAGE_ADDED] 后与广告特征库匹配，
 * 命中则发通知；若用户开启了自动拦截，还会在后台禁用其传感器权限。
 */
class AdwareMonitorReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AdwareMonitor"

        /**
         * 开始监听（在应用启动时调用）。
         */
        fun register(context: Context) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            context.registerReceiver(
                AdwareMonitorReceiver(),
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED &&
            intent.action != Intent.ACTION_PACKAGE_REPLACED
        ) {
            return
        }

        val packageName = intent.data?.schemeSpecificPart ?: return
        Log.i(TAG, "检测到安装/更新: $packageName")

        // 动态注册的广播没有 goAsync 的必要上下文，这里直接起协程处理
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                handlePackageAdded(appContext, packageName)
            } catch (e: Exception) {
                Log.e(TAG, "处理安装事件失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 处理新安装的应用。
     */
    private suspend fun handlePackageAdded(context: Context, packageName: String) {
        if (!MonitorPrefs.isMonitorEnabled(context)) return

        val matcher = AdwareMatcher.getInstance(context)
        val entry = matcher.match(packageName) ?: return

        Log.w(TAG, "命中广告特征库: $packageName (${entry.name})")

        // 记录并通知
        EventLog.getInstance().add(packageName, "检测到疑似广告应用", true)
        AdwareNotifier.notifyAdwareDetected(context, entry)

        // 自动拦截
        if (MonitorPrefs.isAutoBlockEnabled(context)) {
            autoBlock(context, packageName, entry)
        }
    }

    /**
     * 自动禁用该应用的传感器权限。
     */
    private suspend fun autoBlock(context: Context, packageName: String, entry: AdwareEntry) {
        val modeManager = ModeManager(context)
        val result = modeManager.resolve()
        val strategy = modeManager.strategyFor(result.activeMode)

        // 无障碍模式无法真正禁用权限
        if (result.activeMode == ControlMode.ACCESSIBILITY) {
            EventLog.getInstance().add(packageName, "无障碍模式无法自动禁用权限", false)
            return
        }

        val success = try {
            strategy.setBlocked(packageName, true)
        } catch (e: Exception) {
            false
        }

        EventLog.getInstance().add(
            target = packageName,
            action = "自动禁用传感器权限",
            success = success
        )
        Log.i(TAG, "自动禁用 $packageName 权限: $success")
    }
}
