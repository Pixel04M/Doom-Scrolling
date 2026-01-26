package com.example.myapplication

import android.app.*
import android.content.Context
import android.content.Intent
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

    companion object {
        private const val CHANNEL_ID = "GestureDetectionChannel"
        private const val NOTIFICATION_ID = 1

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
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        cameraExecutor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        startBackgroundDetection()
        return START_STICKY
    }

    private fun startBackgroundDetection() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                handDetectionAnalyzer = HandDetectionAnalyzer(this) { fingers ->
                    if (fingers != null) {
                        if (fingers.isEmpty()) {
                            if (ScrollAccessibilityService.isEnabled()) {
                                ScrollAccessibilityService.setEnabled(false)
                            }
                            gestureScrollController.reset()
                            return@HandDetectionAnalyzer
                        }

                        if (!ScrollAccessibilityService.isEnabled()) {
                            ScrollAccessibilityService.setEnabled(true)
                        }

                        val result = gestureScrollController.processFingerMovement(fingers)
                        if (result != null) {
                            when (result.direction) {
                                GestureScrollController.ScrollDirection.VERTICAL -> {
                                    ScrollAccessibilityService.performScroll(result.amount)
                                }
                                GestureScrollController.ScrollDirection.LEFT -> {
                                    ScrollAccessibilityService.performScroll(-1000)
                                }
                                GestureScrollController.ScrollDirection.RIGHT -> {
                                    ScrollAccessibilityService.performScroll(1000)
                                }
                            }
                        }
                    } else {
                        gestureScrollController.reset()
                    }
                }

                imageAnalysis.setAnalyzer(cameraExecutor, handDetectionAnalyzer!!)

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)

            } catch (e: Exception) {
                e.printStackTrace()
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
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Gesture Detection Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        cameraExecutor.shutdown()
        handDetectionAnalyzer?.release()
    }
}
