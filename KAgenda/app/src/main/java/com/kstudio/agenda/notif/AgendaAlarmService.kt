package com.kstudio.agenda.notif

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.kstudio.agenda.R
import com.kstudio.agenda.i18n.AppText

/** A bounded, stoppable alarm. A reminder notification alone must never be treated as a ringing alarm. */
class AgendaAlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        val channel = "agenda_alarm"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channel, AppText.current.alarmEvent, NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
        })
        val stop = PendingIntent.getService(this, 925310, Intent(this, AgendaAlarmService::class.java).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 925311, Intent(this, com.kstudio.agenda.ui.MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent?.getStringExtra(ReminderReceiver.EXTRA_TITLE) ?: AppText.current.alarmEvent)
            .setContentText(intent?.getStringExtra(ReminderReceiver.EXTRA_TIME_RANGE).orEmpty())
            .setCategory(NotificationCompat.CATEGORY_ALARM).setOngoing(true)
            .addAction(0, AppText.current.stopAlarm, stop).setContentIntent(open).build()
        startForeground(925310, notification)
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AgendaAlarmService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 120_000)
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        player?.release()
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
