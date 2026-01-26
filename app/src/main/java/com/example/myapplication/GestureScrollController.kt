package com.example.myapplication

import android.util.Log
import kotlin.math.abs

class GestureScrollController {
    
    private var previousFinger1Y: Float? = null
    private var previousFinger2Y: Float? = null
    private var lastScrollTime = 0L
    private val scrollThreshold = 0.005f // Even lower threshold for high sensitivity
    private val scrollCooldown = 30L // Faster polling for smooth video app scrolling
    
    data class FingerPosition(val x: Float, val y: Float)
    
    fun processFingerMovement(
        fingers: List<FingerPosition>?
    ): Int? {
        val currentTime = System.currentTimeMillis()
        
        // PAUSE gesture (empty list from analyzer)
        if (fingers != null && fingers.isEmpty()) {
            reset()
            return null
        }

        // Need exactly one finger (index finger) to scroll
        if (fingers == null || fingers.size != 1) {
            return null
        }
        
        val currentFinger = fingers[0]
        
        // Initialize previous position if needed
        if (previousFinger1Y == null) {
            previousFinger1Y = currentFinger.y
            lastScrollTime = currentTime
            return null
        }
        
        val deltaY = currentFinger.y - previousFinger1Y!!
        
        // Check threshold and cooldown
        if (abs(deltaY) < scrollThreshold) {
            return null
        }
        
        if (currentTime - lastScrollTime < scrollCooldown) {
            return null
        }
        
        // Amplify movement for YouTube Shorts/TikTok
        // 5000f makes it extremely snappy for distance flicking
        val scrollAmount = (deltaY * 5000f).toInt().coerceIn(-1000, 1000)
        
        // Update previous position
        previousFinger1Y = currentFinger.y
        lastScrollTime = currentTime
        
        Log.d("GestureScroll", "DeltaY: $deltaY, ScrollAmount: $scrollAmount")
        
        return scrollAmount
    }
    
    private fun calculateScrollAmount(deltaY: Float): Int {
        // Convert normalized movement (0-1) to scroll amount
        // Negative deltaY (fingers moving up) = fingers moving AWAY from bottom = scroll DOWN
        // Positive deltaY (fingers moving down) = fingers moving TOWARDS bottom = scroll UP
        
        // We need to amplify the movement. 2000f gives more responsiveness.
        val scrollAmount = (deltaY * 2000f).toInt()
        
        // Clamp scroll amount to reasonable range
        return scrollAmount.coerceIn(-500, 500)
    }
    
    fun reset() {
        previousFinger1Y = null
        previousFinger2Y = null
        lastScrollTime = 0L
    }
}
