package com.autosend.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.autosend.sender.SendService

/**
 * Fires at the scheduled time. Kicks off the foreground [SendService], which does the real work.
 * Kept intentionally tiny so it returns fast (broadcast receivers must not block).
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val messageId = intent.getLongExtra(AlarmScheduler.EXTRA_MESSAGE_ID, -1L)
        Log.d(TAG, "Alarm fired for message $messageId")
        if (messageId <= 0L) return
        SendService.start(context, messageId)
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}
