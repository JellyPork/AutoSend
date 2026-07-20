package com.autosend.sender

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.autosend.data.MessageWithAttachments
import com.autosend.data.TargetApp
import com.autosend.util.AttachmentStorage
import java.net.URLEncoder
import java.util.ArrayList

/**
 * Builds the Intent that opens the target app at the right place with the message preloaded.
 *
 *  - Text only  -> wa.me deep link opens the exact chat with the text already typed.
 *  - Attachments -> ACTION_SEND(_MULTIPLE) hands the files to the app's share sheet; the
 *    accessibility service then picks [contactName] and taps Send.
 *
 * Note: Messenger has no equivalent to wa.me for preloading text, so for MESSENGER we can only
 * open the app / share attachments and rely entirely on the accessibility service.
 */
object WhatsAppSender {

    class NotInstalledException(pkg: String) : Exception("La app destino no está instalada: $pkg")

    fun isInstalled(context: Context, pkg: String): Boolean =
        try {
            context.packageManager.getPackageInfo(pkg, 0); true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    /** Returns an intent that opens the target app ready to send, or throws if it isn't installed. */
    fun buildIntent(context: Context, data: MessageWithAttachments): Intent {
        val msg = data.message
        val pkg = msg.targetApp.packageName
        if (!isInstalled(context, pkg)) throw NotInstalledException(pkg)

        return if (data.attachments.isEmpty()) {
            buildTextIntent(msg.targetApp, pkg, msg.phoneE164, msg.text)
        } else {
            buildShareIntent(context, data)
        }
    }

    private fun buildTextIntent(target: TargetApp, pkg: String, phoneE164: String, text: String): Intent {
        return if (target != TargetApp.MESSENGER && phoneE164.isNotBlank()) {
            // wa.me opens the specific chat with the text pre-filled in the input box.
            val encoded = URLEncoder.encode(text, "UTF-8")
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phoneE164?text=$encoded")).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            // Fallback: plain share of the text into the app's contact picker.
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun buildShareIntent(context: Context, data: MessageWithAttachments): Intent {
        val pkg = data.message.targetApp.packageName
        val uris = ArrayList(data.attachments.map { AttachmentStorage.shareUri(context, it) })
        val mime = commonMime(data.attachments.map { it.mimeType })

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        return intent.apply {
            if (data.message.text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, data.message.text)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // WhatsApp is happy with a broad type; use the shared prefix, else a wildcard type.
    private fun commonMime(mimes: List<String>): String {
        if (mimes.isEmpty()) return "*/*"
        val prefixes = mimes.map { it.substringBefore('/') }.toSet()
        return if (prefixes.size == 1) "${prefixes.first()}/*" else "*/*"
    }
}
