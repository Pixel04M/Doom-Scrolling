package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScrollAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isEnabled = false

    companion object {
        private var instance: ScrollAccessibilityService? = null
        
        fun getInstance(): ScrollAccessibilityService? = instance
        
        fun performScroll(deltaY: Int) {
            instance?.scroll(deltaY)
        }
        
        fun setEnabled(enabled: Boolean) {
            instance?.isEnabled = enabled
        }

        fun isEnabled(): Boolean = instance?.isEnabled ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for gesture-based scrolling
    }

    override fun onInterrupt() {
        // Not needed
    }

    private fun scroll(deltaY: Int) {
        if (!isEnabled) return
        
        serviceScope.launch {
            try {
                // Perform scroll gesture directly without finding scrollable node
                // The gesture will work on the current active window
                val gesture = createScrollGesture(deltaY)
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                    }
                }, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun createScrollGesture(deltaY: Int): GestureDescription {
        val startX = 540f // Center of 1080p screen
        val startY = 1500f // Start lower for more swipe range
        
        // Android swipe direction is inverted for scrolling
        // To SCROLL DOWN -> Swipe UP (startY -> smaller endY)
        // To SCROLL UP -> Swipe DOWN (startY -> larger endY)
        
        // We amplify deltaY for standard scrolling to make it more definitive
        val swipeAmplify = if (Math.abs(deltaY) < 500) deltaY * 2 else deltaY
        val endY = (startY - swipeAmplify).coerceIn(200f, 2200f)
        
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)
        
        // YouTube Shorts/TikTok require very fast, short duration swipes to trigger navigation
        val duration = if (Math.abs(deltaY) > 500) 40L else 100L 
        
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()
        
        return gestureDescription
    }
}
