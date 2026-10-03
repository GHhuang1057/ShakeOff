package io.github.geekhonize.shakeoff.strategy

import android.content.Context
import android.provider.Settings
import io.github.geekhonize.shakeoff.accessibility.AdSkipAccessibilityService

/**
 * 无障碍策略：自动点击「跳过」按钮。
 *
 * 优先级最低，作为保底方案。
 *
 * 局限：无障碍模式**无法真正禁用传感器权限**——它只能在广告弹窗出现后
 * 点击关闭按钮。因此 [isBlocked] 只能反映"是否已拦截过"，
 * [setBlocked] 仅用于记录用户意图并提示实际行为。
 */
class AccessibilityStrategy(private val context: Context) : SensorControlStrategy {

    override val mode: ControlMode = ControlMode.ACCESSIBILITY

    /**
     * 无障碍服务是否已启用。
     *
     * 双重判断：
     * 1. 服务内部标记的运行状态
     * 2. 系统设置中该服务是否处于开启状态（服务刚启用时内部标记可能未就绪）
     */
    override suspend fun isAvailable(): Boolean {
        if (AdSkipAccessibilityService.isRunning) return true

        val enabled = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            enabled.split(":").any {
                it.substringAfterLast("/").substringBefore(":")
                    .equals(
                        AdSkipAccessibilityService::class.java.name,
                        ignoreCase = true
                    )
            }
        } catch (e: Exception) {
            false
        }
        return enabled
    }

    override suspend fun unavailableReason(): String = "无障碍服务未启用，请在系统设置中开启"

    /**
     * 无障碍模式下无法查询 appops 状态，
     * 此处返回是否有拦截记录，避免误导用户。
     */
    override suspend fun isBlocked(packageName: String): Boolean =
        AdSkipAccessibilityService.interceptCount > 0

    /**
     * 无障碍模式不支持直接设置权限。
     *
     * 这里返回 true 表示"操作已被受理"，
     * 实际拦截由无障碍服务在广告弹出时自动完成。
     */
    override suspend fun setBlocked(packageName: String, blocked: Boolean): Boolean = true
}
