package com.autosend

import android.app.Application
import com.autosend.util.Notifications

class AutoSendApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
