package com.autosend.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.autosend.data.Attachment
import com.autosend.data.AttachmentKind
import java.io.File

/**
 * Copies picked content:// URIs into the app's private files dir so we keep access after the
 * temporary SAF grant expires, and later serves those copies to the target app via FileProvider.
 */
object AttachmentStorage {

    private fun dir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    /** Copies [source] into private storage and returns an [Attachment] (messageId set later). */
    fun copyIn(context: Context, source: Uri, messageId: Long): Attachment {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, source) ?: "archivo_${System.nanoTime()}"
        val mime = resolver.getType(source)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(displayName.substringAfterLast('.', ""))
            ?: "application/octet-stream"

        val safeName = "${System.nanoTime()}_${displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
        val outFile = File(dir(context), safeName)
        resolver.openInputStream(source).use { input ->
            requireNotNull(input) { "No se pudo abrir el adjunto: $source" }
            outFile.outputStream().use { output -> input.copyTo(output) }
        }

        val kind = if (mime.startsWith("image/")) AttachmentKind.IMAGE else AttachmentKind.FILE
        return Attachment(
            messageId = messageId,
            localPath = outFile.absolutePath,
            displayName = displayName,
            mimeType = mime,
            kind = kind,
        )
    }

    /** A content:// URI (via FileProvider) usable in EXTRA_STREAM for the target app. */
    fun shareUri(context: Context, attachment: Attachment): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(attachment.localPath)
        )

    fun deleteFiles(attachments: List<Attachment>) {
        attachments.forEach { runCatching { File(it.localPath).delete() } }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
}
