package io.github.geekhonize.shakeoff.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.geekhonize.shakeoff.monitor.AdwareMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用列表加载与传感器状态查询。
 *
 * 传感器权限的读写已抽象到 [io.github.geekhonize.shakeoff.strategy.SensorControlStrategy]，
 * 由 Shizuku / Device Owner / 无障碍三种模式分别实现。
 */
class AppOpsRepository(private val context: Context) {

    companion object {
        private const val SELF_PACKAGE = "io.github.geekhonize.shakeoff"
    }

    /**
     * 加载已安装应用列表。
     *
     * 过滤规则：
     * - 排除 ShakeOff 自身
     * - 系统应用按开关过滤
     * - 命中广告特征库的应用即使没有启动 Intent 也保留（很多广告 SDK 是无界面的）
     * - 其余无启动 Intent 的应用过滤掉
     *
     * @param includeSystem 是否包含系统应用
     * @param sensorState 传感器权限状态表
     * @return 应用列表，按名称排序
     */
    suspend fun loadInstalledApps(
        includeSystem: Boolean,
        sensorState: Map<String, Boolean> = emptyMap()
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val matcher = AdwareMatcher.getInstance(context)

        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        packages.mapNotNull { info ->
            val pkg = info.packageName ?: return@mapNotNull null
            if (pkg == SELF_PACKAGE) return@mapNotNull null

            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) return@mapNotNull null

            // 广告特征库匹配：无启动 Intent 的广告组件也要保留
            val adEntry = matcher.match(pkg)
            if (adEntry == null) {
                // 非广告应用，过滤掉没有启动 Intent 的后台组件
                val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                if (launchIntent == null) return@mapNotNull null
            }

            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg
            }

            val icon = try {
                pm.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                null
            }

            AppInfo(
                packageName = pkg,
                label = label,
                icon = icon,
                isSystem = isSystem,
                sensorBlocked = sensorState[pkg] ?: false,
                isAdware = adEntry != null,
                adCategory = adEntry?.category.orEmpty()
            )
        }.sortedWith(
            // 广告应用排前面，方便用户优先处理
            compareByDescending<AppInfo> { it.isAdware }
                .thenBy { it.label.lowercase() }
        )
    }

    /**
     * 提取设备上所有疑似广告应用包名，供监控与守护进程使用。
     */
    suspend fun findAdwarePackages(): List<String> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val matcher = AdwareMatcher.getInstance(context)

        val packages = try {
            pm.getInstalledPackages(0)
        } catch (e: Exception) {
            emptyList()
        }

        packages.map { it.packageName }.filter { matcher.isAdware(it) }
    }
}
