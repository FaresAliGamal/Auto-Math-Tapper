package com.example.automathtapper.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class MathTapAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            Log.d("MathTapService", "Event: ${event.eventType}")
        }
    }

    override fun onInterrupt() {}
}
