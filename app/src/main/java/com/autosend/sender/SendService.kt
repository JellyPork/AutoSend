package com.autosend.sender

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.autosend.data.MessageRepository
import com.autosend.data.SendStatus
import com.autosend.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that executes one scheduled send:
 *  1. Loads the message + attachments.
 *  2. Registers the job in [PendingSend] so the accessibility service knows what to do.
 *  3. Wakes the screen and launches the target app (via [WakeActivity], reachable even when locked
 *     through the notification's full-screen intent).
 *  4. Waits for the accessibility service to confirm the tap, or times out.
 */
class SendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repo: MessageRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = MessageRepository.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val messageId = intent?.getLongExtra(EXTRA_MESSAGE_ID, -1L) ?: -1L
        startAsForeground("Preparando envío…")
        if (messageId <= 0L) {
            stopEverything()
            return START_NOT_STICKY
        }
        run(messageId)
        return START_NOT_STICKY
    }

    private fun run(messageId: Long) = scope.launch {
        val data = repo.getWithAttachments(messageId)
        if (data == null) {
            Log.w(TAG, "Message $messageId not found")
            stopEverything()
            return@launch
        }
        val msg = data.message
        repo.markStatus(messageId, SendStatus.SENDING)

        val targetIntent = try {
            WhatsAppSender.buildIntent(this@SendService, data)
        } catch (e: Exception) {
            finish(messageId, success = false, reason = e.message ?: "No se pudo construir el envío")
            return@launch
        }

        val job = PendingSend.Job(
            messageId = messageId,
            targetPackage = msg.targetApp.packageName,
            contactName = msg.contactName,
            hasAttachments = data.attachments.isNotEmpty(),
            autoSend = msg.autoSend,
        )
        PendingSend.begin(job, targetIntent) { success, reason ->
            // Called by the accessibility service (success) or the timeout below.
            scope.launch { finish(messageId, success, reason) }
        }

        // Wake the screen / show over lock screen and launch the target app.
        launchWake()

        if (!msg.autoSend) {
            // Semi-automatic: we only opened the chat; assume the user taps Send.
            delay(SEMI_AUTO_ASSUME_MS)
            PendingSend.complete(success = true, reason = "semiauto")
            return@launch
        }

        // Automatic: give the accessibility service time to pick the contact and tap Send.
        delay(AUTO_TIMEOUT_MS)
        PendingSend.complete(success = false, reason = "Tiempo agotado esperando el botón Enviar")
    }

    private fun launchWake() {
        val wakeIntent = Intent(this, WakeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        // Refresh the ongoing notification with a full-screen intent — this launches WakeActivity
        // even from the background when the device is locked.
        val fsPending = PendingIntent.getActivity(
            this, 1, wakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startAsForeground("Enviando tu mensaje programado…", fsPending)
        // Also try a direct launch (works when the app is visible / has a background-start grant).
        runCatching { startActivity(wakeIntent) }
            .onFailure { Log.d(TAG, "Direct WakeActivity start not allowed; relying on full-screen intent") }
    }

    private suspend fun finish(messageId: Long, success: Boolean, reason: String?) {
        val status = if (success) SendStatus.SENT else SendStatus.FAILED
        repo.markStatus(messageId, status, reason)
        val title = if (success) "Mensaje enviado" else "No se pudo enviar"
        val text = when {
            success && reason == "semiauto" -> "Chat abierto — toca Enviar para completar."
            success -> "Tu mensaje programado se envió."
            else -> reason ?: "Ocurrió un error."
        }
        Notifications.showResult(this, messageId.toInt(), title, text)
        stopEverything()
    }

    private fun startAsForeground(text: String, fullScreen: PendingIntent? = null) {
        val notification = Notifications.buildSendingNotification(this, text, fullScreen)
        // The specialUse FGS type is only defined on API 34+; on older versions the type is
        // taken from the manifest and the two-arg call is correct.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notifications.SENDING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notifications.SENDING_NOTIFICATION_ID, notification)
        }
    }

    private fun stopEverything() {
        if (PendingSend.isActive()) PendingSend.complete(false, "Servicio detenido")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "com.autosend.extra.MESSAGE_ID"
        private const val TAG = "SendService"
        private const val AUTO_TIMEOUT_MS = 45_000L
        private const val SEMI_AUTO_ASSUME_MS = 5_000L

        fun start(context: Context, messageId: Long) {
            val intent = Intent(context, SendService::class.java)
                .putExtra(EXTRA_MESSAGE_ID, messageId)
            context.startForegroundService(intent)
        }
    }
}
