package io.github.geekhonize.shakeoff.monitor

import android.content.Context
import android.util.Log
import io.github.geekhonize.shakeoff.util.ShizukuManager
import io.github.geekhonize.shakeoff.util.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shell 守护进程管理器。
 *
 * **仅在 Shizuku 模式下可用**——需要 shell 权限才能写 /data/local/tmp
 * 并执行 appops。Device Owner 与无障碍模式下不启用。
 *
 * 实现说明：Shizuku 13.1.1 起 `Shizuku.newProcess` 已废弃并转为 private，
 * 因此改用项目内已实现的 UserService（[ShizukuManager.exec]）通道，
 * 该通道同样以 shell 权限执行命令，效果等价。
 */
class ShellDaemonManager(private val context: Context) {

    companion object {
        private const val TAG = "ShellDaemonManager"

        /** 脚本部署路径 */
        const val SCRIPT_PATH = "/data/local/tmp/shakeoff_monitor.sh"

        /** 事件日志路径 */
        const val EVENT_LOG_PATH = "/data/local/tmp/shakeoff_events.log"

        /** PID 文件路径 */
        const val PID_PATH = "/data/local/tmp/shakeoff_monitor.pid"

        private const val ASSET_SCRIPT = "shakeoff_monitor.sh"
    }

    /**
     * 守护进程当前是否可用（仅 Shizuku 模式）。
     */
    fun isAvailable(): Boolean =
        ShizukuManager.status(context) == ShizukuStatus.GRANTED

    /**
     * 将脚本从 assets 部署到 /data/local/tmp 并赋予执行权限。
     *
     * @return 是否部署成功
     */
    suspend fun deployScript(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false

        return@withContext try {
            val script = context.assets.open(ASSET_SCRIPT)
                .bufferedReader().use { it.readText() }

            // 通过 shell 写入文件：先删除再重建，避免残留旧版本
            ShizukuManager.exec(listOf("rm", "-f", SCRIPT_PATH))

            // 用 base64 传输避免换行与转义问题
            val encoded = android.util.Base64.encodeToString(
                script.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )

            // 分块写入，规避单次命令长度限制
            val chunkSize = 2048
            var first = true
            var index = 0
            while (index < encoded.length) {
                val end = minOf(index + chunkSize, encoded.length)
                val chunk = encoded.substring(index, end)
                val cmd = if (first) {
                    listOf("sh", "-c", "echo -n '${chunk}' > ${SCRIPT_PATH}.b64")
                } else {
                    listOf("sh", "-c", "echo -n '${chunk}' >> ${SCRIPT_PATH}.b64")
                }
                if (ShizukuManager.exec(cmd) == null) {
                    Log.e(TAG, "写入脚本分块失败，index=$index")
                    return@withContext false
                }
                first = false
                index = end
            }

            // 解码并赋权
            ShizukuManager.exec(
                listOf("sh", "-c", "base64 -d ${SCRIPT_PATH}.b64 > ${SCRIPT_PATH} && rm -f ${SCRIPT_PATH}.b64 && chmod 755 ${SCRIPT_PATH}")
            )

            Log.i(TAG, "守护脚本已部署")
            true
        } catch (e: Exception) {
            Log.e(TAG, "部署脚本失败", e)
            false
        }
    }

    /**
     * 启动守护进程。
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        if (isRunning()) return@withContext true

        // 先确保脚本存在
        val exists = ShizukuManager.exec(listOf("sh", "-c", "test -f $SCRIPT_PATH && echo yes"))
        if (!exists?.contains("yes")!!) {
            if (!deployScript()) return@withContext false
        }

        // 后台启动，日志重定向到 dev/null 防止阻塞
        val result = ShizukuManager.exec(
            listOf("sh", "-c", "nohup sh $SCRIPT_PATH >/dev/null 2>&1 &")
        )
        // exec 返回 null 也可能已成功启动（后台进程立即结束）
        val started = result != null || isRunning()
        Log.i(TAG, "启动守护进程: $started")
        started
    }

    /**
     * 停止守护进程。
     */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        ShizukuManager.exec(listOf("sh", "-c", "pkill -f shakeoff_monitor.sh"))
        ShizukuManager.exec(listOf("sh", "-c", "rm -f $PID_PATH"))
        true
    }

    /**
     * 守护进程是否在运行。
     */
    suspend fun isRunning(): Boolean = withContext(Dispatchers.IO) {
        val output = ShizukuManager.exec(
            listOf("sh", "-c", "pidof -s shakeoff_monitor.sh || echo none")
        )
        output != null && !output.contains("none") && output.isNotBlank()
    }

    /**
     * 读取守护进程产生的事件日志。
     *
     * @return 事件列表（时间|类型|目标|详情）
     */
    suspend fun readEvents(): List<String> = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext emptyList()
        try {
            val output = ShizukuManager.exec(
                listOf("sh", "-c", "cat $EVENT_LOG_PATH 2>/dev/null | tail -n 100")
            ) ?: return@withContext emptyList()

            output.lines()
                .filter { it.contains("|") }
                .map { it.trim() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 清空事件日志。
     */
    suspend fun clearEvents(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false
        ShizukuManager.exec(listOf("sh", "-c", "rm -f $EVENT_LOG_PATH")) != null
    }

    /**
     * 检查 Doze 状态，Doze 下建议暂停守护进程以省电。
     */
    fun isInDozeMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE)
            as? android.os.PowerManager ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            powerManager.isInteractive
        }
    }
}
