package io.github.geekhonize.shakeoff.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.geekhonize.shakeoff.R

/**
 * 广告应用检测通知。
 */
object AdwareNotifier {

    private const val CHANNEL_ID = "shakeoff_adware"
    private const val CHANNEL_NAME = "广告应用提醒"
    private const val NOTIFICATION_ID_BASE = 8000

    /**
     * 确保通知渠道已创建（Android 8+ 必需）。
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "检测到疑似广告应用时提醒"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 发送「检测到疑似广告应用」高优先级通知。
     *
     * @param entry 命中的广告条目
     * @return 通知 id
     */
    fun notifyAdwareDetected(context: Context, entry: AdwareEntry): Int {
        ensureChannel(context)

        val label = if (entry.name != entry.packageName) {
            "${entry.name}（${entry.category}）"
        } else {
            entry.category
        }

        // 点击跳转到该应用详情页
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", entry.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, entry.packageName.hashCode(), intent, flags)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.adware_notify_title))
            .setContentText(
                context.getString(R.string.adware_notify_text, label)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(Notification.PRIORITY_DEFAULT)

        val id = NOTIFICATION_ID_BASE + (entry.packageName.hashCode() and 0xFFF)

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Android 13+ 未授予 POST_NOTIFICATIONS 时会失败，静默处理
        }
        return id
    }
}
