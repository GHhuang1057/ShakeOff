package io.github.geekhonize.shakeoff.strategy

import android.content.Context
import io.github.geekhonize.shakeoff.util.ShizukuManager
import io.github.geekhonize.shakeoff.util.ShizukuStatus

/**
 * Shizuku 策略：以 shell 权限执行 appops 命令。
 *
 * 这是最可靠的模式——appops 可精准控制 OP_MOTION_SENSORS，
 * 且不依赖界面自动化，不会误点。
 */
class ShizukuStrategy(private val context: Context) : SensorControlStrategy {

    override val mode: ControlMode = ControlMode.SHIZUKU

    override suspend fun isAvailable(): Boolean =
        ShizukuManager.status(context) == ShizukuStatus.GRANTED

    override suspend fun unavailableReason(): String =
        when (ShizukuManager.status(context)) {
            ShizukuStatus.NOT_INSTALLED -> "未安装 Shizuku"
            ShizukuStatus.NOT_RUNNING -> "Shizuku 服务未运行"
            ShizukuStatus.NOT_GRANTED -> "Shizuku 未授权 ShakeOff"
            ShizukuStatus.GRANTED -> ""
        }

    override suspend fun isBlocked(packageName: String): Boolean {
        val output = ShizukuManager.exec(
            listOf("appops", "get", packageName, OP_MOTION_SENSORS)
        ) ?: return false

        // 输出形如 "OP_MOTION_SENSORS: ignore"
        return output.lineSequence()
            .firstOrNull { it.contains(OP_MOTION_SENSORS) }
            ?.substringAfter(":")
            ?.trim()
            ?.equals("ignore", ignoreCase = true) == true
    }

    override suspend fun setBlocked(packageName: String, blocked: Boolean): Boolean {
        val mode = if (blocked) "ignore" else "allow"
        val output = ShizukuManager.exec(
            listOf("appops", "set", packageName, OP_MOTION_SENSORS, mode)
        )
        return output != null
    }

    companion object {
        private const val OP_MOTION_SENSORS = "OP_MOTION_SENSORS"
    }
}
