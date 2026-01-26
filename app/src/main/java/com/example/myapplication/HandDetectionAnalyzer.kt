package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
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

class HandDetectionAnalyzer(
    private val context: Context,
    private val onHandsDetected: (List<GestureScrollController.FingerPosition>?) -> Unit
) : ImageAnalysis.Analyzer {

    private var handLandmarker: HandLandmarker? = null
    private val isProcessing = AtomicBoolean(false)

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
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.3f) // Lowered for distance detection
                .setMinHandPresenceConfidence(0.3f) // Lowered for distance detection
                .setMinTrackingConfidence(0.3f) // Lowered for distance detection
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult, mpImage: MPImage ->
                    try {
                        processHandLandmarks(result)
                    } finally {
                        // Crucial: Close the image associated with this result
                        // and reset the processing flag here, NOT in analyze()
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
        } catch (e: Exception) {
            Log.e("HandDetection", "Error initializing hand landmarker", e)
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        // Use a thread-safe check to prevent overlapping analysis
        if (handLandmarker == null || isProcessing.get()) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        
        try {
            // Convert to Bitmap. CameraX 1.3+ toBitmap() is efficient.
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            
            // IMPORTANT: CameraX provides timestamp in NANOSECONDS.
            // MediaPipe LIVE_STREAM mode expects MILLISECONDS.
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
            // Re-adding the close here for the imageProxy itself
            // The mpImage has its own copy of the bitmap
            imageProxy.close()
        }
    }

    private fun processHandLandmarks(result: HandLandmarkerResult) {
        try {
            val hands = result.landmarks()
            
            if (hands.isEmpty()) {
                onHandsDetected(null)
                return
            }

            // Check for wide open hand (pause gesture - 5 fingers)
            val isWideOpen = hands.any { hand ->
                countRaisedFingers(hand) >= 5
            }

            // Check for closed fist (unpause gesture - 0 fingers)
            val isFist = hands.any { hand ->
                countRaisedFingers(hand) == 0
            }

            if (isWideOpen) {
                onHandsDetected(emptyList()) // Special case: empty list means PAUSE
                return
            }

            if (isFist) {
                onHandsDetected(listOf(GestureScrollController.FingerPosition(-1f, -1f))) // Special case: -1 means UNPAUSE
                return
            }

            // Find index finger position for scrolling or palm for horizontal swipes
            val indexFingerPos = mutableListOf<GestureScrollController.FingerPosition>()
            val palmPos = mutableListOf<GestureScrollController.FingerPosition>()
            
            for (hand in hands) {
                val raisedCount = countRaisedFingers(hand)
                
                // Index finger only for vertical scrolling (Unpauses if was paused)
                if (raisedCount == 1 && isIndexFinger(hand)) {
                    indexFingerPos.add(GestureScrollController.FingerPosition(hand[8].x(), hand[8].y()))
                    break
                }
                
                // Palm/Open hand but not necessarily 5 fingers (e.g. 4 fingers) for horizontal
                if (raisedCount >= 4) {
                    palmPos.add(GestureScrollController.FingerPosition(hand[9].x(), hand[9].y())) 
                    break
                }
            }
            
            if (palmPos.isNotEmpty()) {
                onHandsDetected(palmPos)
            } else if (indexFingerPos.isNotEmpty()) {
                onHandsDetected(indexFingerPos)
            } else {
                onHandsDetected(null)
            }
        } catch (e: Exception) {
            Log.e("HandDetection", "Error processing hand landmarks", e)
            onHandsDetected(null)
        }
    }

    private fun countRaisedFingers(hand: List<NormalizedLandmark>): Int {
        var count = 0
        val fingerTipIndices = listOf(4, 8, 12, 16, 20)
        for (index in fingerTipIndices) {
            if (isFingerRaised(hand, index)) count++
        }
        return count
    }

    private fun isIndexFinger(hand: List<NormalizedLandmark>): Boolean {
        // Index finger tip is 8, base is 6
        // Very strict check: Index raised, all other fingers closed
        return isFingerRaised(hand, 8) && 
               !isFingerRaised(hand, 12, 0.05f) && 
               !isFingerRaised(hand, 16, 0.05f) && 
               !isFingerRaised(hand, 20, 0.05f) &&
               !isFingerRaised(hand, 4, 0.05f) // Include thumb for stability
    }

    private fun isFingerRaised(hand: List<NormalizedLandmark>, tipIndex: Int, threshold: Float = 0.03f): Boolean {
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
        return tipY < baseY - threshold
    }

    private fun extractRaisedFingers(hand: List<NormalizedLandmark>): List<GestureScrollController.FingerPosition> {
        val fingerTips = mutableListOf<GestureScrollController.FingerPosition>()
        val fingerTipIndices = listOf(8) // Only look for index finger tip
        
        for (index in fingerTipIndices) {
            if (isFingerRaised(hand, index)) {
                fingerTips.add(GestureScrollController.FingerPosition(hand[index].x(), hand[index].y()))
            }
        }
        return fingerTips
    }

    fun release() {
        try {
            handLandmarker?.close()
        } catch (e: Exception) {
            Log.e("HandDetection", "Error releasing hand landmarker", e)
        }
        handLandmarker = null
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
