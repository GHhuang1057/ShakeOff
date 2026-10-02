package io.github.geekhonize.shakeoff.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import rikka.shizuku.Shizuku

/**
 * Shizuku 状态。
 */
enum class ShizukuStatus {
    /** 未安装 Shizuku */
    NOT_INSTALLED,

    /** 已安装但服务未运行 */
    NOT_RUNNING,

    /** 服务运行中但未授予 ShakeOff 权限 */
    NOT_GRANTED,

    /** 已授权，可用 */
    GRANTED
}

/**
 * Shizuku 封装：负责状态检测、权限请求与提权执行命令。
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /** 权限请求码 */
    const val REQUEST_CODE = 1001

    /**
     * Shizuku 授权结果回调，由 Activity 注册后转发。
     */
    var onPermissionResult: ((Boolean) -> Unit)? = null

    private val permissionListener =
        rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            onPermissionResult?.invoke(granted)
        }

    /**
     * 获取当前 Shizuku 状态。
     */
    fun status(context: Context): ShizukuStatus {
        if (!isInstalled(context)) return ShizukuStatus.NOT_INSTALLED
        if (!isRunning()) return ShizukuStatus.NOT_RUNNING
        if (!hasPermission()) return ShizukuStatus.NOT_GRANTED
        return ShizukuStatus.GRANTED
    }

    /**
     * Shizuku 管理器是否已安装。
     */
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }

    /**
     * Shizuku 服务是否已连接。
     */
    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    /**
     * 是否已获得 Shizuku 授权。
     */
    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /**
     * 注册权限回调，需在 Activity 生命周期内调用。
     */
    fun registerPermissionListener() {
        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (e: Throwable) {
            // 服务未运行时忽略
        }
    }

    /**
     * 注销权限回调。
     */
    fun unregisterPermissionListener() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (e: Throwable) {
            // ignore
        }
    }

    /**
     * 向 Shizuku 请求授权。
     *
     * @return 是否成功发起请求
     */
    fun requestPermission(): Boolean = try {
        if (isRunning()) {
            Shizuku.requestPermission(REQUEST_CODE)
            true
        } else {
            false
        }
    } catch (e: Throwable) {
        false
    }

    /**
     * 以 shell 身份执行一条命令并返回标准输出。
     *
     * @param cmd 命令与参数，例如 ["appops", "get", pkg, "OP_MOTION_SENSORS"]
     * @return 命令输出，失败时返回 null
     */
    fun exec(cmd: List<String>): String? = try {
        if (!isRunning() || !hasPermission()) return null

        val process = Shizuku.newProcess(cmd.toTypedArray(), null, null)
        process.inputStream.bufferedReader().use { it.readText() }.also {
            // 等待进程结束，避免管道未排空
            process.waitFor()
        }
    } catch (e: Throwable) {
        null
    }

    /**
     * 打开 Shizuku 的下载页（未安装时）。
     */
    fun openShizukuDownload(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 打开 Shizuku 应用（已安装但未运行）。
     */
    fun openShizukuApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            intent?.let { context.startActivity(it) }
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 跳转到目标应用的详情页，引导用户手动关闭传感器权限。
     */
    fun openAppDetails(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    private const val SHIZUKU_DOWNLOAD_URL =
        "https://github.com/RikkaApps/Shizuku/releases/latest"
}
