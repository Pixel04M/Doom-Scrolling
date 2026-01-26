package com.example.myapplication

import android.util.Log
import kotlin.math.abs

/**
 * Gesture Scroll Controller
 * Handles gesture recognition and scroll command generation
 * 
 * Gestures:
 * - Move hand RIGHT → Scroll Up
 * - Move hand LEFT → Scroll Down
 * - Pinch (thumb tip touches index tip) → Pause
 * - Release pinch → Resume
 */
class GestureScrollController {
    
    // Previous tracking for stability
    private var previousIndexX: Float? = null
    private var previousIndexY: Float? = null

    private var lastAngleTriggeredSign: Int = 0
    
    // Pause state
    private var isPaused = false

    // Pause/resume debounce
    private val pauseToggleCooldown = 800L
    private var lastPauseToggleTime = 0L

    private val pinchStableMs = 160L
    private var pinchStartTimeMs: Long? = null
    private var unpinchStartTimeMs: Long? = null

    private val postToggleScrollSuppressMs = 650L
    private var suppressScrollUntilMs = 0L

    private val pinchCandidateDistanceSquared = 0.010f
    private val pinchCandidateSuppressMs = 550L
    
    // Movement thresholds
    private val scrollThreshold = 0.02f // Minimum horizontal movement to trigger scroll
    private val scrollCooldown = 140L // Minimum time between scrolls (ms)

    private val minAngleDeg = 18f
    private val resetAngleDeg = 10f
    
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

        val pinchDist2 = gesture.pinchDistanceSquared
        if (pinchDist2 != null && pinchDist2 < pinchCandidateDistanceSquared) {
            suppressScrollUntilMs = maxOf(suppressScrollUntilMs, currentTime + pinchCandidateSuppressMs)
        }
        
        if (gesture.isPinching) {
            unpinchStartTimeMs = null
            if (pinchStartTimeMs == null) pinchStartTimeMs = currentTime

            val stableFor = currentTime - (pinchStartTimeMs ?: currentTime)
            if (!isPaused && stableFor >= pinchStableMs && currentTime - lastPauseToggleTime >= pauseToggleCooldown) {
                isPaused = true
                lastPauseToggleTime = currentTime
                suppressScrollUntilMs = currentTime + postToggleScrollSuppressMs
                resetTracking()
                Log.d("GestureController", "Pause activated (pinch stable)")
                return GestureResult(GestureAction.PAUSE)
            }

            return GestureResult(GestureAction.NONE)
        } else {
            pinchStartTimeMs = null
        }

        if (!gesture.isPinching && isPaused) {
            if (unpinchStartTimeMs == null) unpinchStartTimeMs = currentTime
            val stableFor = currentTime - (unpinchStartTimeMs ?: currentTime)

            if (stableFor >= pinchStableMs && currentTime - lastPauseToggleTime >= pauseToggleCooldown) {
                isPaused = false
                lastPauseToggleTime = currentTime
                suppressScrollUntilMs = currentTime + postToggleScrollSuppressMs
                resetTracking()
                Log.d("GestureController", "Resume activated (unpinch stable)")
                return GestureResult(GestureAction.RESUME)
            }

            return GestureResult(GestureAction.NONE)
        } else {
            unpinchStartTimeMs = null
        }
        
        if (!isPaused) {
            if (currentTime < suppressScrollUntilMs) {
                return GestureResult(GestureAction.NONE)
            }
            val angle = gesture.indexFingerAngleDeg
            if (angle != null) {
                return handleAngleScroll(angle, currentTime)
            }
        }
        
        resetTracking()
        return GestureResult(GestureAction.NONE)
    }

    private fun handleAngleScroll(angleDeg: Float, currentTime: Long): GestureResult {
        // User mapping:
        // 0..+90 degrees => Scroll Up
        // 0..-90 degrees => Scroll Down
        // Ignore anything outside [-90, +90] to avoid accidental triggers.
        if (angleDeg > 90f || angleDeg < -90f) {
            return GestureResult(GestureAction.NONE)
        }

        val absAngle = abs(angleDeg)

        if (absAngle <= resetAngleDeg) {
            lastAngleTriggeredSign = 0
        }

        if (currentTime - lastScrollTime < scrollCooldown) {
            return GestureResult(GestureAction.NONE)
        }

        if (absAngle < minAngleDeg) {
            return GestureResult(GestureAction.NONE)
        }

        val sign = if (angleDeg >= 0f) 1 else -1
        if (lastAngleTriggeredSign == sign) {
            return GestureResult(GestureAction.NONE)
        }

        val scrollAmount = ((absAngle / 90f) * 1200f).toInt().coerceIn(120, 1400)
        val action = if (sign > 0) GestureAction.SCROLL_UP else GestureAction.SCROLL_DOWN

        lastAngleTriggeredSign = sign
        lastScrollTime = currentTime

        Log.d("GestureController", "Angle scroll: angle=$angleDeg action=$action amount=$scrollAmount")
        return GestureResult(action, scrollAmount)
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
        val deltaY = indexPosition.y - previousIndexY!!
        val deltaXAbs = abs(deltaX)
        val deltaYAbs = abs(deltaY)
        
        // Check cooldown
        if (currentTime - lastScrollTime < scrollCooldown) {
            // Update position but don't scroll yet
            previousIndexX = indexPosition.x
            previousIndexY = indexPosition.y
            return GestureResult(GestureAction.NONE)
        }
        
        // Horizontal control:
        // - Move RIGHT -> Scroll Up
        // - Move LEFT  -> Scroll Down
        // Require vertical movement to be relatively small to avoid accidental triggers.
        if (deltaXAbs > scrollThreshold && deltaYAbs < scrollThreshold * 2) {
            val scrollAmount = (deltaXAbs * 10000f).toInt().coerceIn(80, 1400)
            val direction = if (deltaX > 0) {
                GestureAction.SCROLL_UP
            } else {
                GestureAction.SCROLL_DOWN
            }
            
            previousIndexX = indexPosition.x
            previousIndexY = indexPosition.y
            lastScrollTime = currentTime
            
            Log.d("GestureController", "Scroll detected: $direction, amount: $scrollAmount, deltaX: $deltaX")
            return GestureResult(direction, scrollAmount)
        } else {
            Log.d(
                "GestureController",
                "Movement too small: deltaX=$deltaX (threshold=$scrollThreshold), deltaYAbs=$deltaYAbs"
            )
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
        lastAngleTriggeredSign = 0
    }
    
    /**
     * Reset all state including pause state
     */
    fun reset() {
        resetTracking()
        isPaused = false
        lastScrollTime = 0L
        lastPauseToggleTime = 0L
        pinchStartTimeMs = null
        unpinchStartTimeMs = null
    }
    
    /**
     * Get current pause state
     */
    fun isPaused(): Boolean = isPaused
}
