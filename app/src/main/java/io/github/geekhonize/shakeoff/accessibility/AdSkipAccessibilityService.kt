package io.github.geekhonize.shakeoff.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.geekhonize.shakeoff.monitor.EventLog

/**
 * 广告跳过无障碍服务。
 *
 * 读取屏幕节点，当检测到「跳过」「关闭」「不再提示」等按钮时自动点击。
 *
 * 隐私声明：**不收集、不上传任何屏幕内容**。
 * 所有判断均在本地内存中完成，仅在命中关键词时记录一条不含文本内容的日志。
 */
class AdSkipAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipService"

        /**
         * 需要自动点击的按钮关键词。
         */
        val SKIP_KEYWORDS = listOf(
            "跳过", "关闭", "不再提示", "以后再说", "取消", "我知道了",
            "跳过广告", "skip", "close", "dismiss", "cancel", "no thanks"
        )

        /**
         * 标记服务是否已启用。
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * 最近一次拦截时间戳，供 UI 展示。
         */
        @Volatile
        var lastInterceptTime: Long = 0L
            private set

        /**
         * 累计拦截次数。
         */
        @Volatile
        var interceptCount: Int = 0
            private set

        /**
         * 由设置页调用，触发一次主动扫描（用于服务刚启用时）。
         */
        fun resetStats() {
            interceptCount = 0
            lastInterceptTime = 0L
        }

        /** 单次遍历的最大节点数，防止卡顿 */
        const val MAX_NODES = 300

        /** 按钮文本最大长度，过长说明是正文而非按钮 */
        const val MAX_TEXT_LENGTH = 20
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "广告跳过无障碍服务已启用")
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val root = event.source ?: return
        try {
            scanAndClick(root)
        } catch (e: Exception) {
            Log.w(TAG, "扫描节点失败", e)
        } finally {
            // 及时回收，避免节点泄漏
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                root.recycle()
            }
        }
    }

    /**
     * 广度优先遍历节点树，命中关键词则点击。
     */
    private fun scanAndClick(root: AccessibilityNodeInfo) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++

            if (isSkipButton(node)) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    interceptCount++
                    lastInterceptTime = System.currentTimeMillis()
                    Log.i(TAG, "已拦截广告按钮")
                    EventLog.getInstance().add(
                        packageName = "无障碍",
                        action = "点击跳过按钮",
                        success = true
                    )
                    return
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
    }

    /**
     * 判断节点是否为「跳过/关闭」类按钮。
     */
    private fun isSkipButton(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (text.length > MAX_TEXT_LENGTH) return false

        val lower = text.lowercase()
        return SKIP_KEYWORDS.any { keyword ->
            if (keyword.any { it.code > 127 }) {
                text.contains(keyword)
            } else {
                lower.contains(keyword)
            }
        }
    }
}
