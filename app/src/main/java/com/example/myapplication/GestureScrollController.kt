package com.example.myapplication

import android.util.Log
import kotlin.math.abs

class GestureScrollController {
    
    private var previousFinger1Y: Float? = null
    private var previousFinger2Y: Float? = null
    private var lastScrollTime = 0L
    private val scrollThreshold = 0.01f // Lowered threshold for distance detection (smaller movements)
    private val scrollCooldown = 40L // Slightly faster scrolling
    
    data class FingerPosition(val x: Float, val y: Float)
    
    fun processFingerMovement(
        finger1: FingerPosition?,
        finger2: FingerPosition?
    ): Int? {
        val currentTime = System.currentTimeMillis()
        
        // Need both fingers to scroll
        if (finger1 == null || finger2 == null) {
            previousFinger1Y = null
            previousFinger2Y = null
            return null
        }
        
        // Calculate average Y position of both fingers
        val currentAvgY = (finger1.y + finger2.y) / 2f
        
        // Initialize previous positions if needed
        if (previousFinger1Y == null || previousFinger2Y == null) {
            previousFinger1Y = finger1.y
            previousFinger2Y = finger2.y
            return null
        }
        
        val previousAvgY = (previousFinger1Y!! + previousFinger2Y!!) / 2f
        val deltaY = currentAvgY - previousAvgY
        
        // Check threshold and cooldown
        if (abs(deltaY) < scrollThreshold) {
            // Don't update previous positions here to allow accumulation of small movements
            return null
        }
        
        if (currentTime - lastScrollTime < scrollCooldown) {
            return null
        }
        
        // Calculate scroll amount based on movement speed and distance
        // We use 3000f to make it very sensitive for distance
        val scrollAmount = (deltaY * 3000f).toInt().coerceIn(-600, 600)
        
        // Update previous positions
        previousFinger1Y = finger1.y
        previousFinger2Y = finger2.y
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
