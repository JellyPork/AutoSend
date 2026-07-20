package com.autosend.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.autosend.MainActivity
import com.autosend.data.ScheduledMessage

/**
 * Schedules exact, wake-the-device alarms for scheduled messages.
 *
 * Uses [AlarmManager.setAlarmClock], which is treated as exact and is allowed to fire in Doze
 * WITHOUT the SCHEDULE_EXACT_ALARM permission gate. Its only side effect is a status-bar alarm icon,
 * which is acceptable for an app whose core purpose is timed sending.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(message: ScheduledMessage) {
        if (message.id == 0L) {
            Log.w(TAG, "schedule() called with unsaved message (id=0); ignoring")
            return
        }
        val operation = alarmPendingIntent(message.id)
        val showIntent = showPendingIntent()
        val info = AlarmManager.AlarmClockInfo(message.scheduleAtMillis, showIntent)
        alarmManager.setAlarmClock(info, operation)
        Log.d(TAG, "Scheduled message ${message.id} at ${message.scheduleAtMillis}")
    }

    fun cancel(messageId: Long) {
        alarmManager.cancel(alarmPendingIntent(messageId))
        Log.d(TAG, "Canceled alarm for message $messageId")
    }

    /** True on Android 12+ only if the user granted exact-alarm capability (informational). */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    private fun alarmPendingIntent(messageId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        return PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_FIRE = "com.autosend.action.FIRE"
        const val EXTRA_MESSAGE_ID = "com.autosend.extra.MESSAGE_ID"
        private const val TAG = "AlarmScheduler"
    }
}
