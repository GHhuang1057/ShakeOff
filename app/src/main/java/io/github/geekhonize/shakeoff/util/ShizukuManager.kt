package io.github.geekhonize.shakeoff.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import io.github.geekhonize.shakeoff.ICommandService
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
 * 以 shell 权限执行命令的 UserService 实现。
 *
 * Shizuku 13.1.1 起 `Shizuku.newProcess` 已被废弃并转为私有，
 * 因此必须通过 UserService 在特权进程中执行命令。
 */
class CommandService : ICommandService.Stub() {

    override fun exec(cmd: Array<String?>?): String? {
        if (cmd == null || cmd.isEmpty()) return "ERROR:empty command"
        return try {
            val builder = ProcessBuilder()
            cmd.forEach { builder.command(it) }
            val process = builder.redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            output
        } catch (e: Exception) {
            "ERROR:${e.message}"
        }
    }
}

/**
 * Shizuku 封装：负责状态检测、权限请求与通过 UserService 执行命令。
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_DOWNLOAD_URL =
        "https://github.com/RikkaApps/Shizuku/releases/latest"

    /** 权限请求码 */
    const val REQUEST_CODE = 1001

    /**
     * 权限请求结果回调。
     */
    var onPermissionResult: ((Boolean) -> Unit)? = null

    private val permissionListener =
        rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            onPermissionResult?.invoke(grantResult == PackageManager.PERMISSION_GRANTED)
        }

    @Volatile
    private var remoteService: ICommandService? = null

    @Volatile
    private var appContext: Context? = null

    private val serviceConnection = ServiceConnection { _, binder ->
        remoteService = binder?.let { ICommandService.Stub.asInterface(it) }
    }

    /**
     * 保存应用上下文，UserServiceArgs 绑定时需要。
     */
    fun init(context: Context) {
        appContext = context.applicationContext
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
     * 注册权限回调。
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
     * 确保 UserService 已绑定。
     *
     * 绑定是异步的，首次调用时可能尚未建立连接，
     * 此时会等待 [BIND_TIMEOUT_MS] 毫秒。
     */
    private fun ensureService(): ICommandService? {
        if (!isRunning() || !hasPermission()) return null
        remoteService?.let { return it }

        val context = appContext ?: return null
        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, CommandService::class.java.name)
        )
            .daemon(true)
            .tag("shakeoff.command")
            .version(1)

        try {
            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Throwable) {
            Log.e(TAG, "bindUserService failed", e)
            return null
        }

        // 等待异步回调
        val deadline = System.currentTimeMillis() + BIND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            remoteService?.let { return it }
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return remoteService
    }

    /**
     * 以 shell 权限执行一条命令。
     *
     * @param cmd 命令与参数
     * @return 标准输出；失败时返回 null
     */
    fun exec(cmd: List<String>): String? {
        val service = ensureService() ?: return null
        return try {
            val result = service.exec(cmd.toTypedArray())
            if (result != null && result.startsWith("ERROR:")) null else result
        } catch (e: Throwable) {
            Log.e(TAG, "exec failed", e)
            null
        }
    }

    /**
     * 打开 Shizuku 下载页（未安装时）。
     */
    fun openShizukuDownload(context: Context) {
        openUrl(context, SHIZUKU_DOWNLOAD_URL)
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
     * 跳转到目标应用详情页，引导用户手动关闭传感器权限。
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

    private fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    private const val BIND_TIMEOUT_MS = 3000L
}
