package io.github.geekhonize.shakeoff.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.geekhonize.shakeoff.util.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 appops 读取/修改目标应用的传感器权限。
 *
 * 核心命令：
 * - 查询：appops get &lt;pkg&gt; OP_MOTION_SENSORS
 * - 屏蔽：appops set &lt;pkg&gt; OP_MOTION_SENSORS ignore
 * - 恢复：appops set &lt;pkg&gt; OP_MOTION_SENSORS allow
 */
class AppOpsRepository(private val context: Context) {

    companion object {
        private const val OP_MOTION_SENSORS = "OP_MOTION_SENSORS"
        private const val SELF_PACKAGE = "io.github.geekhonize.shakeoff"
    }

    /**
     * 查询指定包名的传感器权限是否已被屏蔽。
     *
     * @return true 表示已屏蔽（ignore）
     */
    suspend fun isBlocked(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val output = ShizukuManager.exec(
            listOf("appops", "get", packageName, OP_MOTION_SENSORS)
        ) ?: return@withContext false

        // 输出形如 "OP_MOTION_SENSORS: ignore" 或 "OP_MOTION_SENSORS: allow"
        output.lineSequence()
            .firstOrNull { it.contains(OP_MOTION_SENSORS) }
            ?.substringAfter(":")
            ?.trim()
            ?.equals("ignore", ignoreCase = true) == true
    }

    /**
     * 设置指定包名的传感器权限。
     *
     * @param blocked true 屏蔽摇一摇，false 恢复
     * @return 操作是否成功
     */
    suspend fun setBlocked(packageName: String, blocked: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val mode = if (blocked) "ignore" else "allow"
            val output = ShizukuManager.exec(
                listOf("appops", "set", packageName, OP_MOTION_SENSORS, mode)
            )
            output != null
        }

    /**
     * 批量查询所有包的传感器权限状态。
     */
    suspend fun queryAll(packageNames: List<String>): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            val result = HashMap<String, Boolean>(packageNames.size)
            for (pkg in packageNames) {
                result[pkg] = isBlocked(pkg)
            }
            result
        }

    /**
     * 加载已安装应用列表，过滤掉没有启动 Intent 的应用（后台服务/无界面组件）。
     *
     * @param includeSystem 是否包含系统应用
     * @param sensorState 传感器权限状态表，为空时默认全部未屏蔽
     */
    suspend fun loadInstalledApps(
        includeSystem: Boolean,
        sensorState: Map<String, Boolean> = emptyMap()
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
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

            // 过滤无启动 Intent 的应用
            val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
            if (launchIntent == null) return@mapNotNull null

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
                sensorBlocked = sensorState[pkg] ?: false
            )
        }.sortedBy { it.label.lowercase() }
    }
}
