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
        private var isServiceEnabled = false
        
        fun getInstance(): ScrollAccessibilityService? = instance
        
        fun performScroll(deltaY: Int) {
            android.util.Log.d("ScrollService", "Static performScroll called, instance: ${instance != null}")
            instance?.scroll(deltaY)
        }
        
        fun setEnabled(enabled: Boolean) {
            android.util.Log.d("ScrollService", "Static setEnabled: $enabled")
            isServiceEnabled = enabled
            instance?.isEnabled = enabled
        }

        fun isEnabled(): Boolean = isServiceEnabled
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        this.isEnabled = isServiceEnabled
        android.util.Log.d("ScrollService", "Accessibility service connected, enabled: $isServiceEnabled")
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
        if (!isEnabled) {
            android.util.Log.w("ScrollService", "Scroll called but service is disabled")
            return
        }
        
        android.util.Log.d("ScrollService", "Performing scroll: $deltaY")
        serviceScope.launch {
            try {
                // Perform scroll gesture directly without finding scrollable node
                // The gesture will work on the current active window
                val gesture = createScrollGesture(deltaY)
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        android.util.Log.d("ScrollService", "Scroll gesture completed")
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        android.util.Log.w("ScrollService", "Scroll gesture cancelled")
                    }
                }, null)
            } catch (e: Exception) {
                android.util.Log.e("ScrollService", "Error performing scroll", e)
            }
        }
    }

    // Removed horizontal swipe - not needed per requirements


    private fun createScrollGesture(deltaY: Int): GestureDescription {
        // Get screen dimensions for better gesture accuracy
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        
        val startX = screenWidth / 2f // Center of screen
        val startY = screenHeight * 0.7f // Start at 70% down the screen
        
        // Android swipe direction is inverted for scrolling
        // To SCROLL DOWN -> Swipe UP (startY -> smaller endY)
        // To SCROLL UP -> Swipe DOWN (startY -> larger endY)
        
        // Amplify deltaY for standard scrolling
        val swipeAmplify = deltaY * 2
        val endY = (startY - swipeAmplify).coerceIn(screenHeight * 0.1f, screenHeight * 0.9f)
        
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)
        
        val duration = 150L // Smooth scroll duration
        
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()
        
        return gestureDescription
    }

    // Removed horizontal swipe - not needed per requirements
}
