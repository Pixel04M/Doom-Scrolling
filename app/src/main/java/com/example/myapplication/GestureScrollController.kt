package com.example.myapplication

import android.util.Log
import kotlin.math.abs

/**
 * Gesture Scroll Controller
 * Handles gesture recognition and scroll command generation
 * 
 * Gestures:
 * - Index finger moved RIGHT → Scroll Down
 * - Index finger moved LEFT → Scroll Up
 * - 5 fingers open → Pause
 * - 5 fingers closed → Unpause
 */
class GestureScrollController {
    
    // Previous index finger position for movement tracking
    private var previousIndexX: Float? = null
    private var previousIndexY: Float? = null
    
    // Pause state
    private var isPaused = false
    
    // Movement thresholds
    private val scrollThreshold = 0.02f // Minimum horizontal movement to trigger scroll
    private val scrollCooldown = 100L // Minimum time between scrolls (ms)
    
    // Timing
    private var lastScrollTime = 0L
    
    enum class GestureAction {
        SCROLL_DOWN,
        SCROLL_UP,
        PAUSE,
        RESUME,
        NONE
    }
    
    data class GestureResult(
        val action: GestureAction,
        val scrollAmount: Int = 0 // Only used for scroll actions
    )
    
    /**
     * Process hand gesture and return action
     */
    fun processGesture(gesture: HandDetectionAnalyzer.HandGesture?): GestureResult {
        val currentTime = System.currentTimeMillis()
        
        // No hand detected
        if (gesture == null) {
            Log.d("GestureController", "No gesture detected")
            resetTracking()
            return GestureResult(GestureAction.NONE)
        }
        
        // Handle 5 fingers open (pause)
        if (gesture.isFiveFingersOpen) {
            if (!isPaused) {
                isPaused = true
                resetTracking()
                Log.d("GestureController", "Pause activated (5 fingers open)")
                return GestureResult(GestureAction.PAUSE)
            }
            return GestureResult(GestureAction.NONE)
        }
        
        // Handle 5 fingers closed (unpause)
        if (gesture.isFiveFingersClosed) {
            if (isPaused) {
                isPaused = false
                resetTracking()
                Log.d("GestureController", "Resume activated (5 fingers closed)")
                return GestureResult(GestureAction.RESUME)
            }
            return GestureResult(GestureAction.NONE)
        }
        
        // Handle index finger movement for scrolling (only if not paused)
        if (gesture.indexFingerPosition != null && !isPaused) {
            return handleIndexFingerScroll(gesture.indexFingerPosition, currentTime)
        }
        
        // Unknown gesture state
        resetTracking()
        return GestureResult(GestureAction.NONE)
    }
    
    /**
     * Handle index finger horizontal movement for scrolling
     */
    private fun handleIndexFingerScroll(indexPosition: HandDetectionAnalyzer.IndexFingerPosition, currentTime: Long): GestureResult {
        // Initialize if first detection
        if (previousIndexX == null || previousIndexY == null) {
            previousIndexX = indexPosition.x
            previousIndexY = indexPosition.y
            lastScrollTime = currentTime
            return GestureResult(GestureAction.NONE)
        }
        
        val deltaX = indexPosition.x - previousIndexX!!
        val deltaY = abs(indexPosition.y - previousIndexY!!)
        
        // Check cooldown
        if (currentTime - lastScrollTime < scrollCooldown) {
            // Update position but don't scroll yet
            previousIndexX = indexPosition.x
            previousIndexY = indexPosition.y
            return GestureResult(GestureAction.NONE)
        }
        
        // Only scroll if horizontal movement is significant and vertical movement is minimal
        // Index finger moved RIGHT (positive deltaX) → Scroll Down
        // Index finger moved LEFT (negative deltaX) → Scroll Up
        if (abs(deltaX) > scrollThreshold && deltaY < scrollThreshold * 2) {
            // Calculate scroll amount based on horizontal movement distance
            val scrollAmount = (abs(deltaX) * 10000f).toInt().coerceIn(50, 1000)
            val direction = if (deltaX > 0) {
                GestureAction.SCROLL_DOWN // Right = Scroll Down
            } else {
                GestureAction.SCROLL_UP // Left = Scroll Up
            }
            
            previousIndexX = indexPosition.x
            previousIndexY = indexPosition.y
            lastScrollTime = currentTime
            
            Log.d("GestureController", "Scroll detected: $direction, amount: $scrollAmount, deltaX: $deltaX")
            return GestureResult(direction, scrollAmount)
        } else {
            Log.d("GestureController", "Movement too small: deltaX=$deltaX (threshold=$scrollThreshold), deltaY=$deltaY")
        }
        
        // Update position even if no scroll
        previousIndexX = indexPosition.x
        previousIndexY = indexPosition.y
        
        return GestureResult(GestureAction.NONE)
    }
    
    /**
     * Reset tracking state (called when hand is lost or gesture changes)
     */
    private fun resetTracking() {
        previousIndexX = null
        previousIndexY = null
    }
    
    /**
     * Reset all state including pause state
     */
    fun reset() {
        resetTracking()
        isPaused = false
        lastScrollTime = 0L
    }
    
    /**
     * Get current pause state
     */
    fun isPaused(): Boolean = isPaused
}
