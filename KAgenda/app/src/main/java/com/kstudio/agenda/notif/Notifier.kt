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
