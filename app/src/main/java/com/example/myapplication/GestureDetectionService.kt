package com.example.myapplication

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureDetectionService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private lateinit var cameraExecutor: ExecutorService
    private var handDetectionAnalyzer: HandDetectionAnalyzer? = null
    private val gestureScrollController = GestureScrollController()
    private var isCameraBound = false
    private var cameraProvider: ProcessCameraProvider? = null

    interface StatusCallback {
        fun onStatusChanged(status: String)
    }

    companion object {
        private const val CHANNEL_ID = "GestureDetectionChannel"
        private const val NOTIFICATION_ID = 1
        private var instance: GestureDetectionService? = null
        private var statusCallback: StatusCallback? = null

        private const val STATUS_THROTTLE_MS = 200L
        private var lastStatusTimeMs = 0L
        private var lastStatusValue: String? = null

        fun start(context: Context) {
            val intent = Intent(context, GestureDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GestureDetectionService::class.java)
            context.stopService(intent)
        }

        fun setStatusCallback(callback: StatusCallback?) {
            statusCallback = callback
        }

        private fun updateStatus(status: String) {
            val now = System.currentTimeMillis()
            if (status == lastStatusValue && now - lastStatusTimeMs < STATUS_THROTTLE_MS) {
                return
            }
            if (now - lastStatusTimeMs < STATUS_THROTTLE_MS) {
                return
            }
            lastStatusTimeMs = now
            lastStatusValue = status
            android.util.Log.d("GestureService", "Updating status: $status, callback: ${statusCallback != null}")
            statusCallback?.onStatusChanged(status)
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        android.util.Log.d("GestureService", "Service started, callback set: ${statusCallback != null}")
        updateStatus("Service starting...")
        startBackgroundDetection()
        return START_STICKY
    }

    private fun startBackgroundDetection() {
        // Don't start if already bound
        if (isCameraBound) {
            android.util.Log.d("GestureService", "Camera already bound, skipping")
            return
        }
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                // Add a longer delay to ensure MainActivity releases camera first
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (isCameraBound) {
                            android.util.Log.d("GestureService", "Camera already bound, skipping")
                            return@postDelayed
                        }
                        
                        cameraProvider?.unbindAll()

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        handDetectionAnalyzer = HandDetectionAnalyzer(this) { gesture ->
                            if (gesture == null) {
                                // No hand detected
                                if (ScrollAccessibilityService.isEnabled()) {
                                    updateStatus("WAITING FOR GESTURE")
                                }
                                gestureScrollController.reset()
                                return@HandDetectionAnalyzer
                            }
                            
                            // Process gesture
                            val result = gestureScrollController.processGesture(gesture)
                            
                            when (result.action) {
                                GestureScrollController.GestureAction.SCROLL_DOWN -> {
                                    if (ScrollAccessibilityService.isEnabled() && !gestureScrollController.isPaused()) {
                                        android.util.Log.d("GestureService", "Sending SCROLL_DOWN to AccessibilityService")
                                        ScrollAccessibilityService.performScroll(result.scrollAmount)
                                        updateStatus("SCROLLING DOWN")
                                    } else {
                                        android.util.Log.d("GestureService", "SCROLL_DOWN skipped: enabled=${ScrollAccessibilityService.isEnabled()}, paused=${gestureScrollController.isPaused()}")
                                    }
                                }
                                GestureScrollController.GestureAction.SCROLL_UP -> {
                                    if (ScrollAccessibilityService.isEnabled() && !gestureScrollController.isPaused()) {
                                        android.util.Log.d("GestureService", "Sending SCROLL_UP to AccessibilityService")
                                        ScrollAccessibilityService.performScroll(-result.scrollAmount)
                                        updateStatus("SCROLLING UP")
                                    } else {
                                        android.util.Log.d("GestureService", "SCROLL_UP skipped: enabled=${ScrollAccessibilityService.isEnabled()}, paused=${gestureScrollController.isPaused()}")
                                    }
                                }
                                GestureScrollController.GestureAction.PAUSE -> {
                                    if (ScrollAccessibilityService.isEnabled()) {
                                        ScrollAccessibilityService.performTap()
                                    }
                                    updateStatus("PAUSED - Separate thumb and index to resume")
                                }
                                GestureScrollController.GestureAction.RESUME -> {
                                    if (ScrollAccessibilityService.isEnabled()) {
                                        ScrollAccessibilityService.performTap()
                                    }
                                    updateStatus("RESUMED - Angle 0..+90 up, 0..-90 down")
                                }
                                GestureScrollController.GestureAction.NONE -> {
                                    when {
                                        gesture.isPinching -> {
                                            updateStatus("PINCH - PAUSE")
                                        }
                                        gesture.indexFingerPosition != null -> {
                                            if (gestureScrollController.isPaused()) {
                                                updateStatus("PAUSED - Separate thumb and index to resume")
                                            } else {
                                                updateStatus("HAND DETECTED - Angle 0..+90 up, 0..-90 down")
                                            }
                                        }
                                        else -> {
                                            updateStatus("Angle 0..+90 up, 0..-90 down")
                                        }
                                    }
                                }
                            }
                        }

                        imageAnalysis.setAnalyzer(cameraExecutor, handDetectionAnalyzer!!)

                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        cameraProvider?.bindToLifecycle(this, cameraSelector, imageAnalysis)
                        
                        isCameraBound = true
                        android.util.Log.d("GestureService", "Camera bound successfully, gesture detection started")
                        updateStatus("Ready - Angle 0..+90 up, 0..-90 down. Pinch to pause.")
                    } catch (e: Exception) {
                        android.util.Log.e("GestureService", "Error binding camera", e)
                        updateStatus("Error: ${e.message}")
                        isCameraBound = false
                    }
                }, 1000) // Longer delay to ensure MainActivity releases camera
            } catch (e: Exception) {
                android.util.Log.e("GestureService", "Error getting camera provider", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Scrollable Background Active")
            .setContentText("Gesture detection is running in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Gesture Detection Service Channel",
                NotificationManager.IMPORTANCE_MIN
            )
            serviceChannel.setShowBadge(false)
            serviceChannel.enableVibration(false)
            serviceChannel.enableLights(false)
            serviceChannel.setSound(null, null)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        // Release camera resources
        try {
            if (isCameraBound && cameraProvider != null) {
                cameraProvider?.unbindAll()
                isCameraBound = false
            }
        } catch (e: Exception) {
            android.util.Log.e("GestureService", "Error unbinding camera on destroy", e)
        }
        
        cameraExecutor.shutdown()
        handDetectionAnalyzer?.release()
        instance = null
    }
}
