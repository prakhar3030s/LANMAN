package com.example.lanman.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.ui.common.GlassButton
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.util.concurrent.Executors

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScanContactScreen(
    onNavigateBack: () -> Unit,
    viewModel: ScanContactViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hazeState = remember { HazeState() }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state) {
        if (state is ScanContactState.Success) {
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .haze(hazeState)
    ) {
        when (val currentState = state) {
            is ScanContactState.Scanning -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
                        Box(modifier = Modifier.weight(1f)) {
                            // Camera Preview
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()

                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val barcodeScanner = BarcodeScanning.getClient(
                                            BarcodeScannerOptions.Builder()
                                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                                .build()
                                        )

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setTargetResolution(Size(1280, 720))
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        val executor = Executors.newSingleThreadExecutor()

                                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                            if (viewModel.state.value !is ScanContactState.Scanning) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }

                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                                barcodeScanner.process(image)
                                                    .addOnSuccessListener { barcodes ->
                                                        for (barcode in barcodes) {
                                                            barcode.rawValue?.let { code ->
                                                                viewModel.onCodeScannedOrEntered(code)
                                                            }
                                                        }
                                                    }
                                                    .addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                imageAnalysis
                                            )
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Glass Viewfinder Overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(280.dp)
                                    .border(2.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(1.dp, ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Camera permission is required to scan QR codes.\nYou can still enter a code manually below.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    ManualEntrySection(

                        onAddClicked = { viewModel.onCodeScannedOrEntered(it) }
                    )
                }
            }
            
            is ScanContactState.Validating -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }
            
            is ScanContactState.ConfirmationRequired -> {
                Dialog(onDismissRequest = { viewModel.resetToScanning() }) {
                    GlassSurface(

                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (currentState.isUpdate) "Update Contact" else "Add Contact",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val action = if (currentState.isUpdate) "update" else "add"
                            Text(
                                text = "Do you want to $action ${currentState.payload.displayName}?",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                GlassButton(
                                    text = "Cancel",
                                    onClick = { viewModel.resetToScanning() },

                                    modifier = Modifier.weight(1f)
                                )
                                GlassButton(
                                    text = "Confirm",
                                    onClick = { viewModel.confirmAddContact(currentState.payload) },

                                    isPrimary = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
            
            is ScanContactState.Error -> {
                Dialog(onDismissRequest = { viewModel.resetToScanning() }) {
                    GlassSurface(

                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            GlassButton(
                                text = "Try Again",
                                onClick = { viewModel.resetToScanning() },

                                isPrimary = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            is ScanContactState.Success -> {
                // Handled by LaunchedEffect
            }
        }
    }
}

@Composable
fun ManualEntrySection(onAddClicked: (String) -> Unit) {
    var manualCode by remember { mutableStateOf("") }
    
    GlassSurface(

        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Manual Entry Fallback", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            GlassTextField(
                value = manualCode,
                onValueChange = { manualCode = it },
                placeholder = "Paste Code",

            )
            Spacer(modifier = Modifier.height(16.dp))
            GlassButton(
                text = "Add Contact",
                onClick = { onAddClicked(manualCode) },

                isPrimary = true,
                enabled = manualCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
