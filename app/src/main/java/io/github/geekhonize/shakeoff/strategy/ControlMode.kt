package io.github.geekhonize.shakeoff.strategy

/**
 * 控制模式。
 *
 * 三种模式按优先级从高到低排列，可用性由 [ModeManager] 探测；
 * 当用户选定的模式不可用时，会自动降级到下一个可用模式。
 *
 * 优先级顺序（数值越小优先级越高）：
 * 1. [SHIZUKU] —— shell 权限，最可靠
 * 2. [DEVICE_OWNER] —— 设备管理员，可管理运行时权限
 * 3. [ACCESSIBILITY] —— 无障碍服务，点击"跳过"按钮，保底方案
 */
enum class ControlMode(
    /** 中文名称 */
    val label: String,

    /** 优先级，数值越小越优先 */
    val priority: Int,

    /** 能力说明 */
    val description: String
) {
    /** Shizuku 模式：以 shell 权限执行 appops */
    SHIZUKU(
        label = "Shizuku 模式",
        priority = 1,
        description = "通过 Shizuku 以 shell 权限执行 appops 命令，可精准开关传感器权限。"
    ),

    /** Device Owner 模式：通过设备管理员管理权限 */
    DEVICE_OWNER(
        label = "Device Owner 模式",
        priority = 2,
        description = "通过设备管理员授予/撤销传感器权限，无需 Shizuku，但需将 ShakeOff 设为设备所有者。"
    ),

    /** 无障碍模式：自动点击跳过按钮 */
    ACCESSIBILITY(
        label = "无障碍模式",
        priority = 3,
        description = "读取界面节点自动点击「跳过」「关闭」等按钮，仅能在广告弹出时拦截，属保底方案。"
    );

    companion object {
        /**
         * 按优先级从高到低排序。
         */
        val byPriority: List<ControlMode> = entries.sortedBy { it.priority }
    }
}

/**
 * 模式可用性探测结果。
 *
 * @param mode 模式
 * @param available 当前是否可用
 * @param reason 不可用原因（可用时为空）
 */
data class ModeAvailability(
    val mode: ControlMode,
    val available: Boolean,
    val reason: String = ""
)
