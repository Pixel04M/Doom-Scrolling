package com.example.myapplication

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        
        lifecycle.addObserver(lifecycleObserver)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
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

    override fun onStop() {
        super.onStop()
        // If scrolling is enabled, we ideally want to keep the analysis running.
        // However, Android restricts camera access in the background for privacy.
    }

    override fun onResume() {
        super.onResume()
        // Re-bind if needed or refresh state
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
        val cameraProvider = cameraProvider ?: return
        val previewView = previewView ?: return

        // Set resolution to a standard 4:3 or 16:9 for stability
        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(1280, 720))
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(1280, 720))
            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        handDetectionAnalyzer = HandDetectionAnalyzer(this) { fingers ->
            if (fingers != null && fingers.size == 2) {
                val scrollAmount = gestureScrollController.processFingerMovement(
                    fingers[0],
                    fingers[1]
                )
                scrollAmount?.let {
                    ScrollAccessibilityService.performScroll(it)
                }
            } else {
                gestureScrollController.reset()
            }
        }

        imageAnalysis.setAnalyzer(cameraExecutor, handDetectionAnalyzer!!)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun enableScrolling() {
        ScrollAccessibilityService.setEnabled(true)
    }

    private fun disableScrolling() {
        ScrollAccessibilityService.setEnabled(false)
        gestureScrollController.reset()
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handDetectionAnalyzer?.release()
    }
}

data class PermissionState(
    val hasCamera: Boolean,
    val hasAccessibility: Boolean
)

@Composable
fun MainScreen(
    onEnableScrolling: () -> Unit,
    onDisableScrolling: () -> Unit,
    requestCameraPermission: () -> Unit,
    checkPermissions: () -> PermissionState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? MainActivity
    val permissions = remember { mutableStateOf(checkPermissions()) }
    val isScrollingEnabled = remember { mutableStateOf(false) }
    val scrollStatus = remember { mutableStateOf("Ready") }
    val previewView = remember { PreviewView(context) }

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
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isScrollingEnabled.value = !isScrollingEnabled.value
                        if (isScrollingEnabled.value) {
                            onEnableScrolling()
                            scrollStatus.value = "Scrolling Enabled"
                        } else {
                            onDisableScrolling()
                            scrollStatus.value = "Ready"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScrollingEnabled.value) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(
                        text = if (isScrollingEnabled.value) {
                            "Disable"
                        } else {
                            "Enable"
                        }
                    )
                }

                // Test Scrolling Buttons
                Button(
                    onClick = { ScrollAccessibilityService.performScroll(300) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Down")
                }

                Button(
                    onClick = { ScrollAccessibilityService.performScroll(-300) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Up")
                }
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
                    text = "• Hold up two fingers in front of the front camera",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Move fingers up to scroll down",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Move fingers down to scroll up",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
