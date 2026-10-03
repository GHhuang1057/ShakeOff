package io.github.geekhonize.shakeoff.strategy

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import io.github.geekhonize.shakeoff.accessibility.AdSkipAccessibilityService
import io.github.geekhonize.shakeoff.deviceowner.DeviceAdminReceiver

/**
 * 模式结果。
 *
 * @param activeMode 当前实际生效的模式
 * @param requestedMode 用户期望的模式
 * @param degraded 是否发生了自动降级
 * @param degradedTo 降级到的模式（未降级时为 null）
 * @param reason 降级原因（未降级时为空）
 */
data class ModeResult(
    val activeMode: ControlMode,
    val requestedMode: ControlMode,
    val degraded: Boolean,
    val degradedTo: ControlMode? = null,
    val reason: String = ""
)

/**
 * 模式管理器：负责探测可用模式、执行自动降级、持久化用户选择。
 *
 * 优先级：Shizuku > Device Owner > 无障碍。
 * 应用启动时调用 [resolve]，若用户选定的模式不可用，
 * 会自动降级到下一个可用模式并提示。
 */
class ModeManager(private val context: Context) {

    companion object {
        private const val PREF_NAME = "shakeoff_mode"
        private const val KEY_MODE = "control_mode"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 用户期望的模式。未设置时默认为最高优先级的 Shizuku。
     */
    var requestedMode: ControlMode
        get() {
            val name = prefs.getString(KEY_MODE, null) ?: return ControlMode.SHIZUKU
            return ControlMode.entries.firstOrNull { it.name == name } ?: ControlMode.SHIZUKU
        }
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    /**
     * 依据当前偏好创建三种策略实例。
     */
    private fun strategies(): Map<ControlMode, SensorControlStrategy> = mapOf(
        ControlMode.SHIZUKU to ShizukuStrategy(context),
        ControlMode.DEVICE_OWNER to DeviceOwnerStrategy(context),
        ControlMode.ACCESSIBILITY to AccessibilityStrategy(context)
    )

    /**
     * 探测所有模式的可用性。
     */
    suspend fun checkAvailability(): List<ModeAvailability> {
        val map = strategies()
        return ControlMode.byPriority.map { mode ->
            val strategy = map.getValue(mode)
            val available = strategy.isAvailable()
            ModeAvailability(
                mode = mode,
                available = available,
                reason = if (available) "" else strategy.unavailableReason()
            )
        }
    }

    /**
     * 解析当前应使用的模式，含自动降级逻辑。
     *
     * 降级规则：
     * 1. 先取用户请求的模式
     * 2. 若可用则直接使用
     * 3. 若不可用，按优先级顺序（从高到低，且不高于当前请求的优先级）
     *    找到第一个可用的模式
     * 4. 若全部不可用，退回无障碍模式（即使尚未开启，UI 会引导用户去开启）
     */
    suspend fun resolve(): ModeResult {
        val availability = checkAvailability()
        val map = strategies()
        val requested = requestedMode

        val requestedInfo = availability.first { it.mode == requested }
        if (requestedInfo.available) {
            return ModeResult(
                activeMode = requested,
                requestedMode = requested,
                degraded = false
            )
        }

        // 按优先级降级：只考虑优先级 >= requested 的模式（即不高于用户期望的层级）
        val fallback = ControlMode.byPriority
            .filter { it.priority <= requested.priority }
            .firstOrNull { mode -> availability.first { it.mode == mode }.available }

        if (fallback != null) {
            return ModeResult(
                activeMode = fallback,
                requestedMode = requested,
                degraded = true,
                degradedTo = fallback,
                reason = requestedInfo.reason
            )
        }

        // 全部不可用：若请求的是无障碍则保持（引导开启），否则降级到无障碍
        return if (requested == ControlMode.ACCESSIBILITY) {
            ModeResult(
                activeMode = ControlMode.ACCESSIBILITY,
                requestedMode = requested,
                degraded = false
            )
        } else {
            ModeResult(
                activeMode = ControlMode.ACCESSIBILITY,
                requestedMode = requested,
                degraded = true,
                degradedTo = ControlMode.ACCESSIBILITY,
                reason = requestedInfo.reason
            )
        }
    }

    /**
     * 获取指定模式的策略实例，用于执行实际控制。
     */
    fun strategyFor(mode: ControlMode): SensorControlStrategy = when (mode) {
        ControlMode.SHIZUKU -> ShizukuStrategy(context)
        ControlMode.DEVICE_OWNER -> DeviceOwnerStrategy(context)
        ControlMode.ACCESSIBILITY -> AccessibilityStrategy(context)
    }

    /**
     * 切换用户期望的模式。返回解析后的结果，供 UI 判断是否需要提示降级。
     */
    suspend fun selectMode(mode: ControlMode): ModeResult {
        requestedMode = mode
        return resolve()
    }

    /**
     * 打开无障碍设置页。
     */
    fun openAccessibilitySettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Device Owner 配置引导命令。
     */
    fun deviceOwnerSetupCommand(): String = DeviceAdminReceiver.SETUP_COMMAND

    /**
     * 无障碍拦截统计。
     */
    fun accessibilityStats(): Pair<Int, Long> =
        AdSkipAccessibilityService.interceptCount to AdSkipAccessibilityService.lastInterceptTime
}
