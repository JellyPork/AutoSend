package com.autosend.sender

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * Invisible activity whose only job is to bring the device to a state where the target app can be
 * opened, then launch it:
 *   - turns the screen on and shows over the lock screen (API 27+ flags),
 *   - asks the keyguard to dismiss (auto-dismisses a NON-secure lock; prompts auth on a secure one),
 *   - once past the keyguard, starts [PendingSend.targetIntent].
 *
 * With a secure lock (PIN/pattern/fingerprint) Android will not let any app bypass it — the user
 * must authenticate. If they don't, [SendService]'s timeout marks the message FAILED.
 */
class WakeActivity : Activity() {

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWhenLockedAndTurnScreenOn()

        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard != null && keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { launchTargetThenFinish() }
                override fun onDismissError() {
                    Log.w(TAG, "Keyguard dismiss error")
                    finish()
                }
                override fun onDismissCancelled() {
                    Log.w(TAG, "Keyguard dismiss cancelled by user")
                    finish() // leave the full-screen notification for a manual retry
                }
            })
        } else {
            launchTargetThenFinish()
        }
    }

    private fun launchTargetThenFinish() {
        if (launched) return
        launched = true
        val intent = PendingSend.targetIntent
        if (intent != null) {
            runCatching { startActivity(intent) }
                .onFailure { Log.e(TAG, "Failed to start target app", it) }
        } else {
            Log.w(TAG, "No target intent set")
        }
        // Give the launch a moment, then get out of the way so the target app is on top.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 500)
    }

    @Suppress("DEPRECATION")
    private fun showWhenLockedAndTurnScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val TAG = "WakeActivity"
    }
}
