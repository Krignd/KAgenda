package com.kstudio.agenda.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kstudio.agenda.R
import com.kstudio.agenda.i18n.AppText
import com.kstudio.agenda.ui.MainActivity

/** 通知渠道与通知展示 */
object Notifier {

    const val CHANNEL_ID = "course_reminder"

    /**
     * AI 识别完成通知渠道。
     *
     * IMPORTANCE_HIGH → 系统以「浮动通知 / Heads-up 横幅」的形式从屏幕上方弹出（即用户说的浮窗提醒），
     * 与课程提醒分开建渠道，用户可以单独静音 AI 结果而不影响上课提醒。
     */
    const val AI_CHANNEL_ID = "ai_result"

    /** AI 结果通知 id（与常驻通知 925200 / 925210 错开，避免互相覆盖） */
    const val AI_RESULT_NOTIF_ID = 925220

    /** 通知点进来后直接打开 AI 结果（MainActivity 读取该 extra） */
    const val EXTRA_OPEN_AI_RESULT = "kagenda_open_ai_result"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // 同 id 重复创建会更新渠道名称/描述（语言切换后保持一致）
        val t = AppText.current
        val channel = NotificationChannel(
            CHANNEL_ID,
            t.channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = t.channelDesc
        }
        nm.createNotificationChannel(channel)
    }

    /** AI 结果通知渠道（与课程提醒同档：IMPORTANCE_HIGH → 浮动通知） */
    fun ensureAiChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val t = AppText.current
        val channel = NotificationChannel(
            AI_CHANNEL_ID,
            t.channelAiName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = t.channelAiDesc
        }
        nm.createNotificationChannel(channel)
    }

    /** 点通知 → 打开应用并直接展示这次的 AI 识别结果 */
    private fun aiContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_AI_RESULT, true)
        }
        return PendingIntent.getActivity(
            context,
            AI_RESULT_NOTIF_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** AI 识别完成：浮动通知（Heads-up），[count] = 识别出的条数 */
    fun showAiDone(context: Context, count: Int) {
        ensureAiChannel(context)
        if (!hasPermission(context)) return
        val t = AppText.current
        val body = t.aiDoneBody(count)
        val notification = NotificationCompat.Builder(context, AI_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t.aiDoneTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(aiContentIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(AI_RESULT_NOTIF_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    /** AI 识别失败：同样用浮动通知告知（用户可能已离开 AI 界面） */
    fun showAiFailed(context: Context, reason: String) {
        ensureAiChannel(context)
        if (!hasPermission(context)) return
        val t = AppText.current
        val notification = NotificationCompat.Builder(context, AI_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t.aiFailedTitle)
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(aiContentIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(AI_RESULT_NOTIF_ID + 1, notification)
        } catch (_: SecurityException) {
        }
    }

    fun hasPermission(context: Context): Boolean =
        // Android 13 以下无需通知运行时权限，直接视为已授予
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, title: String, room: String, timeRange: String, periodLabel: String, dateIso: String) {
        ensureChannel(context)
        if (!hasPermission(context)) return

        val text = buildString {
            append(timeRange)
            if (periodLabel.isNotBlank()) append(" · ").append(periodLabel)
            if (room.isNotBlank()) append(" · ").append(room)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            title.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(AppText.current.notifClassSoon(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify((dateIso + title).hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }
}
