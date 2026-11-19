package com.focusstudy.poc

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * One place for every alert that is meant to reach the screen, not just the shade.
 *
 * Android will not raise a heads-up banner for a channel created at IMPORTANCE_DEFAULT, and the
 * importance of an existing channel cannot be lowered or raised later. That is why the alert
 * channels below have new ids: upgrading the app creates them correctly even on a device that
 * already has the old, quiet channels. Low-priority channels (focus session, session summary)
 * deliberately stay outside this helper and remain silent.
 */
internal data class AlertChannel(
    val id: String,
    val name: String,
    val description: String,
    val soundRes: Int,
    val highPriority: Boolean = true,
    val category: String = NotificationCompat.CATEGORY_REMINDER,
    val alarmSound: Boolean = false
)

internal object AppNotifications {

    val PRAYER = AlertChannel(
        id = "prayer_adhan_v3",
        name = "الأذان ومواقيت الصلاة",
        description = "صوت أذان كامل وظهور عاجل على الشاشة عند دخول وقت الصلاة.",
        soundRes = R.raw.adhan,
        category = NotificationCompat.CATEGORY_ALARM,
        alarmSound = true
    )
    val TASK = AlertChannel(
        id = "task_alerts_v3",
        name = "تنبيهات المهام العاجلة",
        description = "ظهور فوري على الشاشة مع صوت مميز عند استحقاق مهمة أو تأخرها.",
        soundRes = R.raw.notification_chime
    )
    val EXAM = AlertChannel(
        id = "exam_alerts_v3",
        name = "تنبيهات الامتحانات",
        description = "عدّ تنازلي واضح قبل الامتحانات بالصوت والظهور على الشاشة.",
        soundRes = R.raw.notification_chime
    )
    val TEAM = AlertChannel(
        id = "team_alerts_v3",
        name = "رسائل الفريق العاجلة",
        description = "تنبيه فوري بصوت مميز عند وصول رسالة جديدة من الفريق.",
        soundRes = R.raw.notification_chime
    )
    val UPDATE = AlertChannel(
        id = "update_alerts_v3",
        name = "تحديثات مُنظِّم",
        description = "إشعار واضح عند توفر إصدار جديد.",
        soundRes = R.raw.notification_chime
    )
    val DAILY = AlertChannel(
        id = "daily_encouragement_v3",
        name = "رسائل التشجيع",
        description = "رسائل صباحية ومسائية اختيارية بالصوت نفسه دون إزعاج الشاشة.",
        soundRes = R.raw.notification_chime,
        highPriority = false,
        category = NotificationCompat.CATEGORY_MESSAGE
    )

    fun soundUri(context: Context, resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    fun canPost(context: Context): Boolean {
        if (!manager(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensure(context: Context, channel: AlertChannel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val attributes = AudioAttributes.Builder()
            .setUsage(if (channel.alarmSound) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val nativeChannel = NotificationChannel(
            channel.id,
            channel.name,
            if (channel.highPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = channel.description
            setSound(soundUri(context, channel.soundRes), attributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 120, 260)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            if (channel.highPriority) {
                enableLights(true)
                lightColor = 0x5FE0CE
            }
        }
        manager(context).createNotificationChannel(nativeChannel)
    }

    fun builder(
        context: Context,
        channel: AlertChannel,
        title: String,
        message: String,
        contentIntent: PendingIntent,
        fullScreenIntent: PendingIntent? = null,
        largeIcon: Bitmap? = null
    ): NotificationCompat.Builder {
        ensure(context, channel)
        val sound = soundUri(context, channel.soundRes)
        return NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(channel.category)
            .setPriority(
                if (channel.highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setSound(sound)
            .setVibrate(longArrayOf(0, 180, 120, 260))
            .setAutoCancel(true)
            .setShowWhen(true)
            .apply {
                if (fullScreenIntent != null) setFullScreenIntent(fullScreenIntent, true)
                if (largeIcon != null) setLargeIcon(largeIcon)
            }
    }
}
