package com.example.myapplication

import android.util.Log
import kotlin.math.abs

class GestureScrollController {
    
    private var previousFinger1Y: Float? = null
    private var previousFinger1X: Float? = null
    private val horizontalThreshold = 0.08f // Threshold for horizontal shake/swipe
    private val scrollThreshold = 0.005f // Even lower threshold for high sensitivity
    private val scrollCooldown = 30L // Faster polling for smooth video app scrolling
    private var lastScrollTime = 0L
    
    enum class ScrollDirection { VERTICAL, LEFT, RIGHT }
    data class ScrollResult(val direction: ScrollDirection, val amount: Int)
    
    data class FingerPosition(val x: Float, val y: Float)
    
    fun processFingerMovement(
        fingers: List<FingerPosition>?
    ): ScrollResult? {
        val currentTime = System.currentTimeMillis()
        
        // PAUSE gesture (empty list from analyzer)
        if (fingers != null && fingers.isEmpty()) {
            reset()
            return null
        }

        // Need exactly one finger (index finger) to scroll or a palm for horizontal
        if (fingers == null || fingers.size != 1) {
            return null
        }
        
        val currentFinger = fingers[0]
        
        // Initialize previous positions if needed
        if (previousFinger1Y == null || previousFinger1X == null) {
            previousFinger1Y = currentFinger.y
            previousFinger1X = currentFinger.x
            lastScrollTime = currentTime
            return null
        }
        
        val deltaY = currentFinger.y - previousFinger1Y!!
        val deltaX = currentFinger.x - previousFinger1X!!
        
        // Prioritize Horizontal "Shake" for Next/Prev
        if (abs(deltaX) > horizontalThreshold) {
            if (currentTime - lastScrollTime < 400) return null
            
            // In normalized coordinates, X increases left to right.
            // Shake RIGHT (hand moves right) = deltaX is POSITIVE
            // Shake LEFT (hand moves left) = deltaX is NEGATIVE
            val direction = if (deltaX > 0) ScrollDirection.RIGHT else ScrollDirection.LEFT
            
            previousFinger1X = currentFinger.x
            previousFinger1Y = currentFinger.y
            lastScrollTime = currentTime
            return ScrollResult(direction, 0)
        }

        // Handle Vertical Scroll
        if (abs(deltaY) > scrollThreshold) {
            if (currentTime - lastScrollTime < scrollCooldown) return null
            
            // Further amplify for distance stability
            val scrollAmount = (deltaY * 6000f).toInt().coerceIn(-1200, 1200)
            
            previousFinger1Y = currentFinger.y
            previousFinger1X = currentFinger.x
            lastScrollTime = currentTime
            return ScrollResult(ScrollDirection.VERTICAL, scrollAmount)
        }
        
        return null
    }

    fun reset() {
        previousFinger1Y = null
        previousFinger1X = null
        lastScrollTime = 0L
    }
}
