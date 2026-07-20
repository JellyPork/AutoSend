package com.autosend.sender

import android.content.Intent

/**
 * Process-wide handoff between [SendService] (which prepares a send) and both [WakeActivity]
 * (which launches the target app) and the accessibility service (which taps Send).
 *
 * Only one send runs at a time. [job] is non-null exactly while a send is in flight.
 */
object PendingSend {

    data class Job(
        val messageId: Long,
        val targetPackage: String,
        val contactName: String,
        val hasAttachments: Boolean,
        val autoSend: Boolean,
    )

    @Volatile var job: Job? = null
        private set

    /** Intent that opens the target app (chat deep link or share sheet). Read by [WakeActivity]. */
    @Volatile var targetIntent: Intent? = null
        private set

    /**
     * Increments on every [begin]. Lets the accessibility service tell two sends apart even when
     * they carry the same messageId (e.g. the user re-sends the same scheduled message).
     */
    @Volatile var generation: Long = 0L
        private set

    private var onComplete: ((Boolean, String?) -> Unit)? = null

    @Synchronized
    fun begin(job: Job, targetIntent: Intent, onComplete: (success: Boolean, reason: String?) -> Unit) {
        this.job = job
        this.targetIntent = targetIntent
        this.onComplete = onComplete
        generation++
    }

    /** Invoked once by whoever finishes the send (accessibility success, or service timeout). */
    @Synchronized
    fun complete(success: Boolean, reason: String?) {
        val cb = onComplete ?: return  // already completed
        onComplete = null
        job = null
        targetIntent = null
        cb(success, reason)
    }

    fun isActive(): Boolean = job != null
}
