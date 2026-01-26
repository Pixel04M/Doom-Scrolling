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
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: HandLandmarkerResult, _: MPImage ->
                    try {
                        processHandLandmarks(result)
                    } finally {
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
            // Incorrect timestamps (nanos instead of millis) can cause the pipeline to stall or flicker.
            val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
            
            handLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e("HandDetection", "Error analyzing image", e)
            isProcessing.set(false)
        } finally {
            // Always close the imageProxy to return the buffer to the camera pipeline.
            // Failing to close this promptly is the #1 cause of camera freezing/blinking.
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

            val validFingers = mutableListOf<GestureScrollController.FingerPosition>()
            
            for (hand in hands) {
                val fingerTips = extractFingerTips(hand)
                if (fingerTips.size >= 2) {
                    validFingers.addAll(fingerTips.take(2))
                }
            }
            
            if (validFingers.size == 2) {
                onHandsDetected(validFingers)
            } else {
                onHandsDetected(null)
            }
        } catch (e: Exception) {
            Log.e("HandDetection", "Error processing hand landmarks", e)
            onHandsDetected(null)
        }
    }

    private fun extractFingerTips(hand: List<NormalizedLandmark>): List<GestureScrollController.FingerPosition> {
        val fingerTips = mutableListOf<GestureScrollController.FingerPosition>()
        val fingerTipIndices = listOf(4, 8, 12, 16, 20)
        
        for (index in fingerTipIndices) {
            if (index < hand.size) {
                val landmark = hand[index]
                val tipY = landmark.y()
                val baseY = when (index) {
                    4 -> if (hand.size > 3) hand[3].y() else tipY
                    8 -> if (hand.size > 6) hand[6].y() else tipY
                    12 -> if (hand.size > 10) hand[10].y() else tipY
                    16 -> if (hand.size > 14) hand[14].y() else tipY
                    20 -> if (hand.size > 18) hand[18].y() else tipY
                    else -> tipY
                }
                
                if (tipY < baseY - 0.05f) {
                    fingerTips.add(GestureScrollController.FingerPosition(landmark.x(), landmark.y()))
                }
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
}
