package io.github.geekhonize.shakeoff.monitor

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 拦截记录条目。
 *
 * @param timestamp 时间戳
 * @param target 目标（包名或模式名）
 * @param action 执行的动作
 * @param success 是否成功
 */
data class EventEntry(
    val timestamp: Long,
    val target: String,
    val action: String,
    val success: Boolean
) {
    /**
     * 格式化后的时间文本。
     */
    fun formattedTime(): String {
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        return format.format(Date(timestamp))
    }
}

/**
 * 拦截记录内存存储。
 *
 * 使用单例 + 有界队列，最多保留 [MAX_ENTRIES] 条，
 * 避免内存无限增长。Shell 守护进程的日志由 [ShellDaemonManager] 单独合并进来。
 */
class EventLog private constructor() {

    companion object {
        private const val TAG = "EventLog"
        private const val MAX_ENTRIES = 200

        @Volatile
        private var instance: EventLog? = null

        /**
         * 获取单例。
         */
        fun getInstance(): EventLog = instance ?: synchronized(this) {
            instance ?: EventLog().also { instance = it }
        }
    }

    private val entries = ArrayDeque<EventEntry>(MAX_ENTRIES)

    /**
     * 追加一条记录。
     */
    @Synchronized
    fun add(target: String, action: String, success: Boolean) {
        // 避免无障碍服务高频触发导致刷屏
        if (target == "无障碍" && entries.size > 2) {
            val last = entries.lastOrNull()
            if (last != null && last.target == "无障碍" &&
                System.currentTimeMillis() - last.timestamp < 1000
            ) {
                return
            }
        }

        if (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(EventEntry(System.currentTimeMillis(), target, action, success))
    }

    /**
     * 读取全部记录（按时间倒序）。
     */
    @Synchronized
    fun getAll(): List<EventEntry> = entries.toList().reversed()

    /**
     * 清空记录。
     */
    @Synchronized
    fun clear() {
        entries.clear()
    }
}
