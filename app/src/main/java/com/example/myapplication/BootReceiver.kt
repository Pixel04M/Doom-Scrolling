package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("ScrollablePrefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("gesture_scrolling_enabled", false)
        if (!enabled) return

        // Cannot auto-enable Accessibility permission; user must enable it once in Settings.
        // But we can restore the app's own enabled state and restart background detection.
        ScrollAccessibilityService.setEnabled(true)
        GestureDetectionService.start(context)
    }
}
