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
        private var enabled = false

        private var gestureInProgress = false
        private sealed class PendingCommand {
            data class Scroll(val deltaY: Int) : PendingCommand()
            data object Tap : PendingCommand()
        }

        private var pendingCommand: PendingCommand? = null

        private val gestureHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        fun getInstance(): ScrollAccessibilityService? = instance
        
        fun performScroll(deltaY: Int) {
            val service = instance
            if (service == null) {
                android.util.Log.w("ScrollService", "Service instance is null")
                return
            }

            if (!enabled) {
                android.util.Log.d("ScrollService", "Scrolling disabled")
                return
            }

            if (gestureInProgress) {
                pendingCommand = PendingCommand.Scroll(deltaY)
                android.util.Log.d("ScrollService", "Gesture in progress, pending scroll delta: $deltaY")
                return
            }

            gestureInProgress = true
            service.scroll(deltaY)
        }

        fun performTap() {
            val service = instance
            if (service == null) {
                android.util.Log.w("ScrollService", "Service instance is null")
                return
            }

            if (!enabled) {
                android.util.Log.d("ScrollService", "Tap ignored: scrolling disabled")
                return
            }

            if (gestureInProgress) {
                pendingCommand = PendingCommand.Tap
                android.util.Log.d("ScrollService", "Gesture in progress, pending tap")
                return
            }

            gestureInProgress = true
            service.tapCenter()
        }
        
        fun setEnabled(enabled: Boolean) {
            android.util.Log.d("ScrollService", "Static setEnabled: $enabled")
            this.enabled = enabled
            instance?.isEnabled = enabled
        }

        fun isEnabled(): Boolean = enabled
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        this.isEnabled = enabled
        android.util.Log.d("ScrollService", "Accessibility service connected, enabled: $enabled")
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
            gestureInProgress = false
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
                        gestureInProgress = false
                        flushPending(delayMs = 90)
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        android.util.Log.w("ScrollService", "Scroll gesture cancelled")
                        gestureInProgress = false
                        flushPending(delayMs = 150)
                    }
                }, null)
            } catch (e: Exception) {
                android.util.Log.e("ScrollService", "Error performing scroll", e)
                gestureInProgress = false
                flushPending(delayMs = 150)
            }
        }
    }

    private fun tapCenter() {
        if (!isEnabled) {
            android.util.Log.w("ScrollService", "Tap called but service is disabled")
            gestureInProgress = false
            return
        }

        serviceScope.launch {
            try {
                val gesture = createTapGesture()
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        android.util.Log.d("ScrollService", "Tap gesture completed")
                        gestureInProgress = false
                        flushPending(delayMs = 120)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        android.util.Log.w("ScrollService", "Tap gesture cancelled")
                        gestureInProgress = false
                        flushPending(delayMs = 180)
                    }
                }, null)
            } catch (e: Exception) {
                android.util.Log.e("ScrollService", "Error performing tap", e)
                gestureInProgress = false
                flushPending(delayMs = 180)
            }
        }
    }

    private fun flushPending(delayMs: Long) {
        val pending = pendingCommand
        pendingCommand = null
        if (pending == null) return

        gestureHandler.postDelayed({
            when (pending) {
                is PendingCommand.Scroll -> performScroll(pending.deltaY)
                is PendingCommand.Tap -> performTap()
            }
        }, delayMs)
    }

    // Removed horizontal swipe - not needed per requirements


    private fun createScrollGesture(deltaY: Int): GestureDescription {
        // Get screen dimensions for better gesture accuracy
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        
        val startX = screenWidth / 2f // Center of screen
        val startY = screenHeight * 0.82f
        
        // Android swipe direction is inverted for scrolling
        // To SCROLL DOWN -> Swipe UP (startY -> smaller endY)
        // To SCROLL UP -> Swipe DOWN (startY -> larger endY)
        
        val minSwipe = screenHeight * 0.55f
        val swipeAmplify = (kotlin.math.abs(deltaY) * 2).toFloat()
        val swipeDistance = swipeAmplify.coerceIn(minSwipe, screenHeight * 0.75f)
        val direction = if (deltaY >= 0) -1f else 1f
        val endY = (startY + direction * swipeDistance).coerceIn(screenHeight * 0.1f, screenHeight * 0.95f)
        
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(startX, endY)
        
        val duration = 280L
        
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()
        
        return gestureDescription
    }

    private fun createTapGesture(): GestureDescription {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val x = screenWidth * 0.5f
        val y = screenHeight * 0.5f

        val path = Path()
        path.moveTo(x, y)

        val duration = 50L
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(strokeDescription).build()
    }

    // Removed horizontal swipe - not needed per requirements
}
