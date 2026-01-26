package com.example.myapplication

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hand Detection Analyzer using MediaPipe Hand Landmarker
 * Detects index finger position for scrolling and 5-finger gestures for pause/unpause
 */
class HandDetectionAnalyzer(
    private val context: Context,
    private val onHandsDetected: (HandGesture?) -> Unit
) : ImageAnalysis.Analyzer {

    private var handLandmarker: HandLandmarker? = null
    private val isProcessing = AtomicBoolean(false)

    private val pinchDistanceSquaredOnThreshold = 0.0025f
    private val pinchDistanceSquaredOffThreshold = 0.0045f
    private var lastPinching = false
    private val thumbRaisedStrictThreshold = 0.06f

    /**
     * Represents detected hand gesture state
     */
    data class HandGesture(
        val isFiveFingersOpen: Boolean,    // True if all 5 fingers are open (pause)
        val isFiveFingersClosed: Boolean,  // True if all 5 fingers are closed (unpause)
        val isFourFingersOpen: Boolean,    // True if index+middle+ring+pinky are open and thumb is not
        val isPinching: Boolean,           // True if thumb tip and index tip are touching
        val pinchDistanceSquared: Float?,  // Thumb tip (4) to index tip (8) distance squared
        val indexFingerAngleDeg: Float?,   // Angle from wrist(0) to index tip(8), in degrees. +90=up, -90=down
        val indexFingerPosition: IndexFingerPosition?, // Index finger position for scrolling (null if not available)
        val landmarks: List<NormalizedPoint> // Full set of landmarks for visualization
    )

    data class NormalizedPoint(
        val x: Float,
        val y: Float
    )

    /**
     * Index finger tip position for tracking movement
     */
    data class IndexFingerPosition(
        val x: Float,  // Normalized X coordinate (0-1)
        val y: Float   // Normalized Y coordinate (0-1)
    )

    init {
        initializeHandLandmarker()
    }

    private fun initializeHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val handLandmarkerOptions = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands(1) // Only need one hand
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult, mpImage: MPImage ->
                    try {
                        processHandLandmarks(result)
                    } finally {
                        mpImage.close()
                        isProcessing.set(false)
                    }
                }
                .setErrorListener { error ->
                    Log.e("HandDetection", "MediaPipe Error: ${error.message}")
                    isProcessing.set(false)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, handLandmarkerOptions)
            Log.d("HandDetection", "Hand landmarker initialized successfully")
        } catch (e: Exception) {
            Log.e("HandDetection", "Error initializing hand landmarker", e)
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (handLandmarker == null || isProcessing.get()) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        
        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // CameraX timestamp in nanoseconds, MediaPipe expects milliseconds
            val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
            
            if (timestampMs > 0) {
                handLandmarker?.detectAsync(mpImage, timestampMs)
            } else {
                imageProxy.close()
                isProcessing.set(false)
            }
        } catch (e: Exception) {
            Log.e("HandDetection", "Error analyzing image", e)
            imageProxy.close()
            isProcessing.set(false)
        } finally {
            imageProxy.close()
        }
    }

    private fun processHandLandmarks(result: HandLandmarkerResult) {
        try {
            val hands = result.landmarks()
            
            Log.d("HandDetection", "Processing ${hands.size} hand(s)")
            
            if (hands.isEmpty()) {
                Log.d("HandDetection", "No hands detected")
                onHandsDetected(null)
                return
            }

            // Use the first detected hand
            val hand = hands[0]
            
            if (hand.size < 21) {
                Log.w("HandDetection", "Hand has insufficient landmarks: ${hand.size}")
                onHandsDetected(null)
                return
            }

            // Detect gesture types
            val isFiveFingersOpen = isFiveFingersOpenGesture(hand)
            val isFiveFingersClosed = isFiveFingersClosedGesture(hand)
            val isFourFingersOpen = isFourFingersOpenGesture(hand)
            val isPinching = isPinchingGesture(hand)
            
            Log.d(
                "HandDetection",
                "Gesture detection - 5FingersOpen: $isFiveFingersOpen, 5FingersClosed: $isFiveFingersClosed, 4FingersOpen: $isFourFingersOpen, pinching: $isPinching"
            )
            
            // Get tracking position for scrolling (only if not pinching)
            val indexFingerPosition = if (!isPinching) {
                val position = IndexFingerPosition(
                    x = hand[0].x(),
                    y = hand[0].y()
                )
                Log.d("HandDetection", "Tracking position: (${position.x}, ${position.y})")
                position
            } else null

            val indexFingerAngleDeg = if (!isPinching) {
                val wrist = hand[0]
                val indexTip = hand[8]
                val dx = indexTip.x() - wrist.x()
                val dy = wrist.y() - indexTip.y()
                kotlin.math.atan2(dy, dx) * (180f / kotlin.math.PI.toFloat())
            } else null

            val pinchDistanceSquared = run {
                val thumbTip = hand[4]
                val indexTip = hand[8]
                val dx = thumbTip.x() - indexTip.x()
                val dy = thumbTip.y() - indexTip.y()
                dx * dx + dy * dy
            }

            val points = hand.map { lm ->
                NormalizedPoint(lm.x(), lm.y())
            }

            val gesture = HandGesture(
                isFiveFingersOpen = isFiveFingersOpen,
                isFiveFingersClosed = isFiveFingersClosed,
                isFourFingersOpen = isFourFingersOpen,
                isPinching = isPinching,
                pinchDistanceSquared = pinchDistanceSquared,
                indexFingerAngleDeg = indexFingerAngleDeg,
                indexFingerPosition = indexFingerPosition,
                landmarks = points
            )

            onHandsDetected(gesture)
            
        } catch (e: Exception) {
            Log.e("HandDetection", "Error processing hand landmarks", e)
            onHandsDetected(null)
        }
    }

    /**
     * Detects if all 5 fingers are open (pause gesture)
     */
    private fun isFiveFingersOpenGesture(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false
        
        val thumbRaised = isFingerRaised(hand, 4)
        val indexRaised = isFingerRaised(hand, 8)
        val middleRaised = isFingerRaised(hand, 12)
        val ringRaised = isFingerRaised(hand, 16)
        val pinkyRaised = isFingerRaised(hand, 20)
        
        // All 5 fingers must be raised
        val allRaised = thumbRaised && indexRaised && middleRaised && ringRaised && pinkyRaised
        
        if (allRaised) {
            Log.d("HandDetection", "5 fingers open detected")
        }
        
        return allRaised
    }

    private fun isFourFingersOpenGesture(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false

        val thumbRaised = isFingerRaised(hand, 4, threshold = thumbRaisedStrictThreshold)
        val indexRaised = isFingerRaised(hand, 8)
        val middleRaised = isFingerRaised(hand, 12)
        val ringRaised = isFingerRaised(hand, 16)
        val pinkyRaised = isFingerRaised(hand, 20)

        if (!thumbRaised && indexRaised && middleRaised && ringRaised && pinkyRaised) {
            Log.d("HandDetection", "4 fingers open detected")
            return true
        }

        return false
    }

    private fun isPinchingGesture(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false

        val thumbTip = hand[4]
        val indexTip = hand[8]
        val dx = thumbTip.x() - indexTip.x()
        val dy = thumbTip.y() - indexTip.y()
        val dist2 = dx * dx + dy * dy

        lastPinching = if (lastPinching) {
            dist2 < pinchDistanceSquaredOffThreshold
        } else {
            dist2 < pinchDistanceSquaredOnThreshold
        }

        return lastPinching
    }

    /**
     * Detects if all 5 fingers are closed (unpause gesture)
     */
    private fun isFiveFingersClosedGesture(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false
        
        val thumbClosed = isFingerClosed(hand, 4)
        val indexClosed = isFingerClosed(hand, 8)
        val middleClosed = isFingerClosed(hand, 12)
        val ringClosed = isFingerClosed(hand, 16)
        val pinkyClosed = isFingerClosed(hand, 20)
        
        // All 5 fingers must be closed
        val allClosed = thumbClosed && indexClosed && middleClosed && ringClosed && pinkyClosed
        
        if (allClosed) {
            Log.d("HandDetection", "5 fingers closed detected")
        }
        
        return allClosed
    }

    /**
     * Checks if index finger is raised (for scrolling)
     */
    private fun isIndexFingerRaised(hand: List<NormalizedLandmark>): Boolean {
        return isFingerRaised(hand, 8)
    }

    /**
     * Checks if a finger is raised (extended)
     */
    private fun isFingerRaised(hand: List<NormalizedLandmark>, tipIndex: Int, threshold: Float = 0.03f): Boolean {
        if (tipIndex >= hand.size) return false
        
        val tipY = hand[tipIndex].y()
        val baseY = when (tipIndex) {
            4 -> if (hand.size > 3) hand[3].y() else tipY // Thumb: tip(4) vs base(3)
            8 -> if (hand.size > 6) hand[6].y() else tipY // Index: tip(8) vs base(6)
            12 -> if (hand.size > 10) hand[10].y() else tipY // Middle: tip(12) vs base(10)
            16 -> if (hand.size > 14) hand[14].y() else tipY // Ring: tip(16) vs base(14)
            20 -> if (hand.size > 18) hand[18].y() else tipY // Pinky: tip(20) vs base(18)
            else -> tipY
        }
        
        // In normalized coordinates, Y increases downward
        // Finger is raised if tip Y is less than base Y (tip is higher on screen)
        return tipY < baseY - threshold
    }

    /**
     * Checks if a finger is closed (bent)
     */
    private fun isFingerClosed(hand: List<NormalizedLandmark>, tipIndex: Int, threshold: Float = 0.02f): Boolean {
        if (tipIndex >= hand.size) return false
        
        val tipY = hand[tipIndex].y()
        val baseY = when (tipIndex) {
            4 -> if (hand.size > 3) hand[3].y() else tipY // Thumb
            8 -> if (hand.size > 6) hand[6].y() else tipY // Index
            12 -> if (hand.size > 10) hand[10].y() else tipY // Middle
            16 -> if (hand.size > 14) hand[14].y() else tipY // Ring
            20 -> if (hand.size > 18) hand[18].y() else tipY // Pinky
            else -> tipY
        }
        
        // Finger is closed if tip is at or below base level
        return tipY >= baseY - threshold
    }

    fun release() {
        try {
            handLandmarker?.close()
        } catch (e: Exception) {
            Log.e("HandDetection", "Error releasing hand landmarker", e)
        }
        handLandmarker = null
    }
}
