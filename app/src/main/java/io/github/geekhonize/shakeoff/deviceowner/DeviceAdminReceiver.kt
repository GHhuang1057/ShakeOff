package io.github.geekhonize.shakeoff.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备管理员接收器。
 *
 * 启用方式（需通过 ADB，手机不能有已登录账户，必要时需恢复出厂设置）：
 * ```
 * adb shell dpm set-device-owner io.github.geekhonize.shakeoff/.DeviceAdminReceiver
 * ```
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        // 授予设备所有者权限后无需额外操作
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // 权限被撤销时无需处理
    }

    companion object {
        /** 供设置页展示的配置命令 */
        const val SETUP_COMMAND =
            "adb shell dpm set-device-owner io.github.geekhonize.shakeoff/.DeviceAdminReceiver"
    }
}
