package com.autosend.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autosend.sender.PendingSend
import java.text.Normalizer

/**
 * Taps the Send button in the target app when — and only when — [PendingSend] has an active,
 * automatic job. Passive otherwise, so it never interferes with normal use.
 *
 *  - text only: the wa.me link opened the chat with the text typed → just tap Send.
 *  - attachments: the "Send to…" share picker opened → select the contact by name, then tap Send
 *    (WhatsApp may need two taps: next arrow → final send).
 *
 * Two hard-won details:
 *  1. WhatsApp contact rows report clickable=false; we tap via a real gesture at the node bounds.
 *  2. The contact list can live in a window that isn't rootInActiveWindow (the search bar grabs
 *     active focus), so we scan ALL windows, not just the active one.
 */
class AutoSendAccessibilityService : AccessibilityService() {

    private enum class Phase { PICK_CONTACT, CLICK_SEND, DONE }

    @Volatile private var handledGeneration: Long = -1L
    @Volatile private var phase: Phase = Phase.DONE
    @Volatile private var processing: Boolean = false
    @Volatile private var sendTaps: Int = 0
    private var lastScanLog = 0L

    private val main = Handler(Looper.getMainLooper())
    private var pendingCompletion: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val job = PendingSend.job
        if (job == null) {
            resetState()
            return
        }
        if (!job.autoSend) return
        if (event?.packageName?.toString() != job.targetPackage) return

        // Key on the send generation, not the messageId: re-sending the same message must reset.
        val generation = PendingSend.generation
        if (generation != handledGeneration) {
            handledGeneration = generation
            phase = if (job.hasAttachments) Phase.PICK_CONTACT else Phase.CLICK_SEND
            sendTaps = 0
            cancelPendingCompletion()
        }
        if (processing || phase == Phase.DONE) return

        processing = true
        try {
            step(job)
        } catch (e: Exception) {
            Log.e(TAG, "step failed", e)
        } finally {
            processing = false
        }
    }

    private fun step(job: PendingSend.Job) {
        val roots = collectRoots()
        if (roots.isEmpty()) {
            Log.d(TAG, "No window roots available")
            return
        }
        when (phase) {
            Phase.PICK_CONTACT -> {
                val node = findContactNode(roots, job.contactName)
                if (node != null) {
                    val ok = tapNode(node)
                    Log.d(TAG, "Contact '${job.contactName}' matched -> tap ok=$ok")
                    if (ok) phase = Phase.CLICK_SEND
                } else {
                    logScan(roots, normalize(job.contactName))
                    scrollContacts(roots)
                }
            }
            Phase.CLICK_SEND -> handleSend(roots, job)
            Phase.DONE -> {}
        }
    }

    private fun handleSend(roots: List<AccessibilityNodeInfo>, job: PendingSend.Job) {
        cancelPendingCompletion()
        val send = findSendNode(roots, job.targetPackage)
        if (send != null) {
            val ok = tapNode(send)
            sendTaps++
            Log.d(TAG, "Tapped Send #$sendTaps (ok=$ok)")
        } else if (sendTaps > 0) {
            scheduleCompletion(job)
        }
    }

    private fun scheduleCompletion(job: PendingSend.Job) {
        val r = Runnable {
            if (PendingSend.job?.messageId == job.messageId && phase == Phase.CLICK_SEND) {
                Log.d(TAG, "Send confirmed for message ${job.messageId}")
                phase = Phase.DONE
                PendingSend.complete(success = true, reason = null)
            }
        }
        pendingCompletion = r
        main.postDelayed(r, COMPLETION_DEBOUNCE_MS)
    }

    private fun cancelPendingCompletion() {
        pendingCompletion?.let { main.removeCallbacks(it) }
        pendingCompletion = null
    }

    private fun resetState() {
        handledGeneration = -1L
        phase = Phase.DONE
        sendTaps = 0
        cancelPendingCompletion()
    }

    // --- Window roots (active window + all interactive windows) ---

    private fun collectRoots(): List<AccessibilityNodeInfo> {
        val roots = LinkedHashSet<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try {
            for (w in windows) w.root?.let { roots.add(it) }
        } catch (e: Exception) {
            Log.d(TAG, "windows unavailable: ${e.message}")
        }
        return roots.toList()
    }

    // --- Contact selection ---

    /**
     * Framework-backed search (like uiautomator) — reliable across RecyclerView virtualization,
     * unlike a manual getChild() walk which does NOT reach the list rows.
     */
    private fun findContactNode(
        roots: List<AccessibilityNodeInfo>,
        contactName: String
    ): AccessibilityNodeInfo? {
        val target = normalize(contactName)
        if (target.length < 2) return null

        val candidates = LinkedHashSet<AccessibilityNodeInfo>()
        for (root in roots) {
            root.findAccessibilityNodeInfosByText(contactName)?.let { candidates.addAll(it) }
            for (id in CONTACT_NAME_IDS) {
                root.findAccessibilityNodeInfosByViewId(id)?.let { candidates.addAll(it) }
            }
        }

        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        for (node in candidates) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: continue
            val norm = normalize(text)
            if (norm.isEmpty()) continue
            val score = matchScore(norm, target, node)
            if (score > bestScore) {
                bestScore = score
                best = node
            }
        }
        if (best != null) Log.d(TAG, "Best contact match score=$bestScore for '$contactName'")
        return best
    }

    private fun matchScore(norm: String, target: String, node: AccessibilityNodeInfo): Int {
        var score = when {
            norm == target -> 100
            norm.startsWith(target) || target.startsWith(norm) -> 60
            norm.contains(target) || target.contains(norm) -> 30
            else -> 0
        }
        if (score > 0) {
            val id = node.viewIdResourceName ?: ""
            if (id.endsWith("contactpicker_row_name") || id.endsWith("contact_name")) score += 10
        }
        return score
    }

    private fun scrollContacts(roots: List<AccessibilityNodeInfo>) {
        for (root in roots) {
            val scrollable = findNode(root) { it.isScrollable }
            if (scrollable != null) {
                scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return
            }
        }
    }

    /** Throttled diagnostic: what contact-name nodes does the framework search actually see? */
    private fun logScan(roots: List<AccessibilityNodeInfo>, target: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScanLog < SCAN_LOG_INTERVAL_MS) return
        lastScanLog = now
        val names = ArrayList<String>()
        for (root in roots) {
            for (id in CONTACT_NAME_IDS) {
                root.findAccessibilityNodeInfosByViewId(id)?.forEach { node ->
                    val t = node.text?.toString()?.trim()
                    if (!t.isNullOrEmpty() && names.size < 12) names.add(t)
                }
            }
        }
        Log.d(TAG, "No match for '$target'. roots=${roots.size} visibleNames=$names")
    }

    // --- Send button ---

    private fun findSendNode(roots: List<AccessibilityNodeInfo>, pkg: String): AccessibilityNodeInfo? {
        val ids = when (pkg) {
            "com.whatsapp", "com.whatsapp.w4b" -> listOf("$pkg:id/send")
            "com.facebook.orca" -> listOf(
                "com.facebook.orca:id/composer_send_button",
                "com.facebook.orca:id/send"
            )
            else -> emptyList()
        }
        for (root in roots) {
            for (id in ids) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                val hit = nodes?.firstOrNull { it.isEnabled && it.isVisibleToUser }
                if (hit != null) return hit
            }
            val byDesc = findNode(root) { node ->
                if (!node.isEnabled || !node.isVisibleToUser) return@findNode false
                val desc = node.contentDescription?.toString()?.lowercase() ?: return@findNode false
                desc == "send" || desc == "enviar"
            }
            if (byDesc != null) return byDesc
        }
        return null
    }

    // --- Tapping (gesture-based, since target rows are clickable=false) ---

    private fun tapNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.isEnabled) {
                if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            current = current.parent
        }
        return tapAtBounds(node)
    }

    private fun tapAtBounds(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val path = Path().apply { moveTo(rect.exactCenterX(), rect.exactCenterY()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    // --- Tree helpers ---

    private fun traverse(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(root)
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            traverse(child, visit)
        }
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNode(child, predicate)
            if (found != null) return found
        }
        return null
    }

    /** Lowercase, strip accents and parenthetical annotations like "(You)"/"(Tú)", keep a-z0-9. */
    private fun normalize(s: String): String {
        val noParen = s.replace(Regex("\\(.*?\\)"), " ")
        val nfd = Normalizer.normalize(noParen, Normalizer.Form.NFD)
        val noMarks = nfd.replace(Regex("\\p{Mn}+"), "")
        return noMarks.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        cancelPendingCompletion()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoSendA11y"
        private const val COMPLETION_DEBOUNCE_MS = 900L
        private const val SCAN_LOG_INTERVAL_MS = 1500L

        /** Contact-name view ids across WhatsApp screens (share picker + home list). */
        private val CONTACT_NAME_IDS = listOf(
            "com.whatsapp:id/contactpicker_row_name",
            "com.whatsapp:id/conversations_row_contact_name",
            "com.whatsapp.w4b:id/contactpicker_row_name",
            "com.whatsapp.w4b:id/conversations_row_contact_name",
        )
    }
}
