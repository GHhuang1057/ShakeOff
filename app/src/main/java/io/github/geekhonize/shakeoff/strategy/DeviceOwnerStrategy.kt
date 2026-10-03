package io.github.geekhonize.shakeoff.strategy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.geekhonize.shakeoff.deviceowner.DeviceAdminReceiver

/**
 * Device Owner 策略：通过设备管理员管理传感器权限。
 *
 * 优先级低于 Shizuku，但不需要 Shizuku 即可工作。
 * 使用 [DevicePolicyManager.setPermissionGrantState] 授予/撤销
 * `android.permission.BODY_SENSORS`，从源头切断加速度传感器数据。
 */
class DeviceOwnerStrategy(private val context: Context) : SensorControlStrategy {

    override val mode: ControlMode = ControlMode.DEVICE_OWNER

    private val dpm: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private val adminReceiver: ComponentName =
        ComponentName(context, DeviceAdminReceiver::class.java)

    /**
     * 是否已是设备所有者（而非仅设备管理员）。
     *
     * 只有 Device Owner 才有权调用 setPermissionGrantState。
     */
    override suspend fun isAvailable(): Boolean = try {
        dpm?.isDeviceOwnerApp(context.packageName) == true
    } catch (e: Exception) {
        false
    }

    override suspend fun unavailableReason(): String {
        if (dpm == null) return "设备策略服务不可用"
        val isAdmin = dpm.isAdminActive(adminReceiver)
        return if (isAdmin) {
            "ShakeOff 仅为设备管理员，需执行：\n${DeviceAdminReceiver.SETUP_COMMAND}"
        } else {
            "未设为设备所有者，请执行：\n${DeviceAdminReceiver.SETUP_COMMAND}"
        }
    }

    /**
     * 设备所有者模式下，通过权限授予状态判断传感器权限是否可用。
     *
     * 注意：Device Owner 撤销权限后，PackageManager 查询仍会返回 granted，
     * 因为 BODY_SENSORS 属于「运行时权限 + appops」双重控制，
     * 这里以权限授予状态为准，授予则视为未屏蔽。
     */
    override suspend fun isBlocked(packageName: String): Boolean = try {
        val state = dpm?.getPermissionGrantState(
            adminReceiver,
            packageName,
            PERMISSION_BODY_SENSORS
        )
        // PERMISSION_GRANTED 表示权限可用（即未屏蔽）
        state == PackageManager.PERMISSION_DENIED
    } catch (e: Exception) {
        false
    }

    /**
     * 授予或撤销目标应用的传感器权限。
     */
    override suspend fun setBlocked(packageName: String, blocked: Boolean): Boolean = try {
        val manager = dpm ?: return false
        if (!manager.isDeviceOwnerApp(context.packageName)) return false

        // blocked=true 表示要屏蔽摇一摇，因此需撤销传感器权限
        val targetState = if (blocked) {
            PackageManager.PERMISSION_DENIED
        } else {
            PackageManager.PERMISSION_GRANTED
        }

        manager.setPermissionGrantState(
            adminReceiver,
            packageName,
            PERMISSION_BODY_SENSORS,
            targetState
        )
        true
    } catch (e: Exception) {
        false
    }

    companion object {
        /**
         * 设备所有者可管理的传感器权限。
         */
        const val PERMISSION_BODY_SENSORS = "android.permission.BODY_SENSORS"
    }
}
