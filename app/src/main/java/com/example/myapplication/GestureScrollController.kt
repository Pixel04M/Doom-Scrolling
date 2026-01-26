package com.example.myapplication

import android.util.Log
import kotlin.math.abs

class GestureScrollController {
    
    private var previousFinger1Y: Float? = null
    private var previousFinger2Y: Float? = null
    private var lastScrollTime = 0L
    private val scrollThreshold = 0.02f // Minimum movement to trigger scroll (normalized coordinates)
    private val scrollCooldown = 50L // Minimum time between scrolls (ms)
    
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
            previousFinger1Y = finger1.y
            previousFinger2Y = finger2.y
            return null
        }
        
        if (currentTime - lastScrollTime < scrollCooldown) {
            previousFinger1Y = finger1.y
            previousFinger2Y = finger2.y
            return null
        }
        
        // Calculate scroll amount based on movement speed and distance
        val scrollAmount = calculateScrollAmount(deltaY)
        
        // Update previous positions
        previousFinger1Y = finger1.y
        previousFinger2Y = finger2.y
        lastScrollTime = currentTime
        
        Log.d("GestureScroll", "DeltaY: $deltaY, ScrollAmount: $scrollAmount")
        
        return scrollAmount
    }
    
    private fun calculateScrollAmount(deltaY: Float): Int {
        // Convert normalized movement (0-1) to scroll amount
        // Negative deltaY (fingers moving up) = scroll down (positive scroll)
        // Positive deltaY (fingers moving down) = scroll up (negative scroll)
        // Scale normalized movement to scroll pixels (multiply by screen height equivalent)
        val baseScroll = (deltaY * 1000f).toInt()
        
        // Clamp scroll amount to reasonable range
        return baseScroll.coerceIn(-200, 200)
    }
    
    fun reset() {
        previousFinger1Y = null
        previousFinger2Y = null
        lastScrollTime = 0L
    }
}
