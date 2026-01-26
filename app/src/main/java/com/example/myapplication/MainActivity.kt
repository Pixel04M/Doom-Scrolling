package com.example.myapplication

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var handDetectionAnalyzer: HandDetectionAnalyzer? = null
    private val gestureScrollController = GestureScrollController()
    
    private val PREFS_NAME = "ScrollablePrefs"
    private val KEY_ENABLED = "gesture_scrolling_enabled"
    
    // Create a mutable state that can be accessed by both Compose and the Analyzer
    private val _scrollStatus = mutableStateOf("Ready")
    val scrollStatusState: State<String> = _scrollStatus
    
    private val _isScrollingEnabledPersisted = mutableStateOf(false)
    val isScrollingEnabledState: State<Boolean> = _isScrollingEnabledPersisted

    private var isInForeground: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            // Permission denied
        }
    }

    private val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
            val newState = checkAllPermissions()
            // We'll use a local state in the UI to react to this
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Load persisted state
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isScrollingEnabledPersisted.value = prefs.getBoolean(KEY_ENABLED, false)
        
        // Register status callback for service updates (will be used when service starts)
        GestureDetectionService.setStatusCallback(object : GestureDetectionService.StatusCallback {
            override fun onStatusChanged(status: String) {
                runOnUiThread {
                    android.util.Log.d("MainActivity", "Status updated from service: $status")
                    _scrollStatus.value = status
                }
            }
        })

        // Auto-start service if it was previously enabled
        if (_isScrollingEnabledPersisted.value) {
            ScrollAccessibilityService.setEnabled(true)
            _scrollStatus.value = if (ScrollAccessibilityService.getInstance() == null) {
                "Accessibility Service NOT connected. Please enable it in Settings."
            } else {
                "Ready - Move hand right (scroll up) or left (scroll down). Pinch to pause."
            }
        }

        lifecycle.addObserver(lifecycleObserver)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        scrollStatus = scrollStatusState,
                        isScrollingEnabledState = isScrollingEnabledState,
                        onEnableScrolling = { enableScrolling() },
                        onDisableScrolling = { disableScrolling() },
                        requestCameraPermission = { requestCameraPermission() },
                        checkPermissions = { checkAllPermissions() }
                    )
                }
            }
        }
    }

    private fun checkAllPermissions(): PermissionState {
        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasAccessibility = isAccessibilityServiceEnabled()

        return PermissionState(hasCamera, hasAccessibility)
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                // Ensure we unbind before binding to avoid "already bound" blinking issues
                cameraProvider?.unbindAll()
                bindCameraUseCases()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onPause() {
        super.onPause()
        // Keep camera running for background gesture detection if scrolling is enabled
        // On modern Android, we might need a Foreground Service to keep camera access.
        // For now, we ensure the camera provider isn't unbinded.
    }

    override fun onStart() {
        super.onStart()
        isInForeground = true

        // If gesture scrolling is enabled, stop background service to avoid camera contention
        if (_isScrollingEnabledPersisted.value) {
            GestureDetectionService.stop(this)
        }
    }

    override fun onStop() {
        super.onStop()
        isInForeground = false

        // Start service only when app goes to background and gesture scrolling is enabled
        if (_isScrollingEnabledPersisted.value) {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error unbinding camera before background service", e)
            }
            GestureDetectionService.start(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister status callback
        GestureDetectionService.setStatusCallback(null)
        cameraExecutor.shutdown()
        handDetectionAnalyzer?.release()
    }

    override fun onResume() {
        super.onResume()

        // If gesture scrolling is enabled, we run in-foreground detection via Activity.
        // Service will be used only when app goes to background.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            bindCameraUseCases()
        }
    }

    private var previewView: PreviewView? = null

    fun setupPreview(view: PreviewView) {
        previewView = view
        bindCameraUseCases()
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
        if (cameraProvider == null) {
            startCamera()
            return
        }
        val previewView = previewView ?: return

        // Set resolution to a standard 4:3 or 16:9 for stability
        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(1280, 720))
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        // Activity always owns preview + analysis while app is open.
        // Background service starts only when app goes to background.
        val isServiceRunning = false

        try {
            // Only unbind if service is not running, otherwise let service handle it
            if (!isServiceRunning) {
                cameraProvider.unbindAll()
            }
            
            // Add delay to ensure camera is fully released if we just unbound
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (isServiceRunning) {
                        // If service is running, Activity ONLY shows the preview
                        // Don't unbind here - let service keep the camera
                        try {
                            cameraProvider.bindToLifecycle(
                                this,
                                cameraSelector,
                                preview
                            )
                            android.util.Log.d("MainActivity", "Preview bound (service has analysis)")
                        } catch (e: Exception) {
                            // Camera might be bound by service, try again later
                            android.util.Log.w("MainActivity", "Could not bind preview, service may have camera: ${e.message}")
                        }
                    } else {
                        // If service is NOT running, Activity handles both preview and analysis
                        val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(1280, 720))
                            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        setupAnalyzer(imageAnalysis)
                        
                        cameraProvider.bindToLifecycle(
                            this,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        android.util.Log.d("MainActivity", "Preview and analysis bound")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error binding camera", e)
                }
            }, if (isServiceRunning) 1200 else 100) // Longer delay if service is running
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error unbinding camera", e)
        }
    }

    private fun setupAnalyzer(imageAnalysis: androidx.camera.core.ImageAnalysis) {
                handDetectionAnalyzer = HandDetectionAnalyzer(this) { gesture ->
            if (gesture == null) {
                // No hand detected
                if (ScrollAccessibilityService.isEnabled()) {
                    runOnUiThread {
                        _scrollStatus.value = "WAITING FOR GESTURE"
                    }
                }
                gestureScrollController.reset()
                return@HandDetectionAnalyzer
            }
            
            // Process gesture
            val result = gestureScrollController.processGesture(gesture)
            
            when (result.action) {
                GestureScrollController.GestureAction.SCROLL_DOWN -> {
                    if (ScrollAccessibilityService.isEnabled() && !gestureScrollController.isPaused()) {
                        ScrollAccessibilityService.performScroll(result.scrollAmount)
                        runOnUiThread {
                            _scrollStatus.value = "SCROLLING DOWN"
                        }
                    }
                }
                GestureScrollController.GestureAction.SCROLL_UP -> {
                    if (ScrollAccessibilityService.isEnabled() && !gestureScrollController.isPaused()) {
                        ScrollAccessibilityService.performScroll(-result.scrollAmount)
                        runOnUiThread {
                            _scrollStatus.value = "SCROLLING UP"
                        }
                    }
                }
                GestureScrollController.GestureAction.PAUSE -> {
                    if (ScrollAccessibilityService.isEnabled()) {
                        ScrollAccessibilityService.performTap()
                    }
                    runOnUiThread {
                        _scrollStatus.value = "PAUSED - Separate thumb and index to resume"
                    }
                }
                GestureScrollController.GestureAction.RESUME -> {
                    if (ScrollAccessibilityService.isEnabled()) {
                        ScrollAccessibilityService.performTap()
                    }
                    runOnUiThread {
                        _scrollStatus.value = "RESUMED - Rotate/move finger angle + (up) / - (down). Pinch pauses."
                    }
                }
                GestureScrollController.GestureAction.NONE -> {
                    runOnUiThread {
                        when {
                            gesture.isPinching -> {
                                _scrollStatus.value = "PINCH - PAUSE"
                            }
                            else -> {
                                _scrollStatus.value = "Angle control: 0..+90 = scroll up, 0..-90 = scroll down"
                            }
                        }
                    }
                }
            }
        }
        imageAnalysis.setAnalyzer(cameraExecutor, handDetectionAnalyzer!!)
    }

    private fun enableScrolling() {
        _isScrollingEnabledPersisted.value = true
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, true).apply()
        
        // Ensure accessibility service is enabled
        ScrollAccessibilityService.setEnabled(true)
        
        // Foreground mode: keep camera in Activity so UI keeps working.
        // Background mode: service will start in onStop().
        GestureDetectionService.stop(this)
        _scrollStatus.value = if (ScrollAccessibilityService.getInstance() == null) {
            "Accessibility Service NOT connected. Please enable it in Settings."
        } else {
            "Ready - Angle 0..+90 up, 0..-90 down. Pinch to pause."
        }

        bindCameraUseCases()
    }

    private fun disableScrolling() {
        _isScrollingEnabledPersisted.value = false
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
        
        // Stop background service
        GestureDetectionService.stop(this)
        // Rebind camera for local preview/analyzer
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            bindCameraUseCases()
        }, 300)
        
        gestureScrollController.reset()
        _scrollStatus.value = "Ready"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

}

data class PermissionState(
    val hasCamera: Boolean,
    val hasAccessibility: Boolean
)

@Composable
fun MainScreen(
    scrollStatus: State<String>,
    isScrollingEnabledState: State<Boolean>,
    onEnableScrolling: () -> Unit,
    onDisableScrolling: () -> Unit,
    requestCameraPermission: () -> Unit,
    checkPermissions: () -> PermissionState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? MainActivity
    val permissions = remember { mutableStateOf(checkPermissions()) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    // Listen for lifecycle changes (like returning from settings) to refresh permissions
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissions.value = checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        permissions.value = checkPermissions()
    }

    LaunchedEffect(permissions.value.hasCamera) {
        if (permissions.value.hasCamera && activity != null) {
            activity.setupPreview(previewView)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Scrollable",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Camera Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Status
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Status: ${scrollStatus.value}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Permissions
        if (!permissions.value.hasCamera) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Camera permission required",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { requestCameraPermission() }) {
                        Text("Grant Camera Permission")
                    }
                }
            }
        }

        if (!permissions.value.hasAccessibility) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Accessibility permission required for system-wide scrolling",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Open Accessibility Settings")
                    }
                }
            }
        }

        // Control Button
        if (permissions.value.hasCamera && permissions.value.hasAccessibility) {
            Button(
                onClick = {
                    if (isScrollingEnabledState.value) {
                        onDisableScrolling()
                    } else {
                        // Request notification permission for Foreground Service on Android 13+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                (context as? androidx.activity.ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                            }
                        }
                        onEnableScrolling()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScrollingEnabledState.value) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    text = if (isScrollingEnabledState.value) {
                        "Disable Gesture Scrolling"
                    } else {
                        "Enable Gesture Scrolling"
                    }
                )
            }
        }

        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Instructions:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Hold up your index finger in front of the front camera",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Move finger LEFT to scroll down",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Move finger RIGHT to scroll up",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Open 5 fingers to pause, close 5 fingers to resume",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
