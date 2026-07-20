package com.autosend.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.autosend.MainActivity
import com.autosend.R

object Notifications {

    const val CHANNEL_SENDING = "sending"
    const val CHANNEL_RESULT = "result"
    const val SENDING_NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val sending = NotificationChannel(
            CHANNEL_SENDING,
            context.getString(R.string.channel_sending_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_sending_desc)
            setSound(null, null)
        }

        val result = NotificationChannel(
            CHANNEL_RESULT,
            context.getString(R.string.channel_result_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.channel_result_desc) }

        nm.createNotificationChannel(sending)
        nm.createNotificationChannel(result)
    }

    /**
     * Ongoing notification for the foreground SendService. [fullScreenIntent] launches WakeActivity
     * over the lock screen — the reliable way to bring UI to the front from the background.
     */
    fun buildSendingNotification(
        context: Context,
        text: String,
        fullScreenIntent: PendingIntent?,
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_SENDING)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("AutoSend")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .apply { if (fullScreenIntent != null) setFullScreenIntent(fullScreenIntent, true) }
            .build()

    fun showResult(context: Context, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}
