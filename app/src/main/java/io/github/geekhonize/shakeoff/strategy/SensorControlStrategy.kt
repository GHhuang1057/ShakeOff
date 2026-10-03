package io.github.geekhonize.shakeoff.strategy

/**
 * 传感器控制策略抽象。
 *
 * 三种模式共享同一套应用列表与 UI，仅底层实现不同：
 * - [ShizukuStrategy]        以 shell 权限执行 appops
 * - [DeviceOwnerStrategy]    以设备管理员身份管理权限
 * - [AccessibilityStrategy]  自动点击「跳过」按钮
 */
interface SensorControlStrategy {

    /**
     * 该策略对应的模式。
     */
    val mode: ControlMode

    /**
     * 当前策略是否可用。
     */
    suspend fun isAvailable(): Boolean

    /**
     * 不可用时的原因说明。
     */
    suspend fun unavailableReason(): String

    /**
     * 查询指定应用的传感器权限是否已被屏蔽。
     *
     * @return true 表示已屏蔽
     */
    suspend fun isBlocked(packageName: String): Boolean

    /**
     * 设置指定应用的传感器权限。
     *
     * @param blocked true 屏蔽（禁用摇一摇），false 恢复
     * @return 操作是否成功
     */
    suspend fun setBlocked(packageName: String, blocked: Boolean): Boolean
}
