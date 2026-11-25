package com.prakhar.lanman.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prakhar.lanman.ui.common.GlassButton
import com.prakhar.lanman.ui.common.GlassSurface
import com.prakhar.lanman.ui.common.GlassTextField
import com.prakhar.lanman.ui.common.QrCodeIcon
import com.prakhar.lanman.ui.theme.Black
import com.prakhar.lanman.ui.theme.LanmanTheme
import com.prakhar.lanman.ui.theme.White
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScanContactScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMyQr: () -> Unit = {},
    viewModel: ScanContactViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LanmanTheme.colors

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
            .background(colors.background)
            .imePadding()
    ) {
        when (val currentState = state) {
            is ScanContactState.Scanning -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (hasCameraPermission) {
                        // Own CameraX executor and ML Kit scanner lifecycle only while camera permission is granted.
                        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                        val barcodeScanner = remember {
                            runCatching {
                                BarcodeScanning.getClient(
                                    BarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                        .build()
                                )
                            }.getOrNull()
                        }
                        var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

                        DisposableEffect(Unit) {
                            onDispose {
                                runCatching { cameraProviderRef?.unbindAll() }
                                runCatching { barcodeScanner?.close() }
                                analysisExecutor.shutdown()
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        cameraProviderRef = cameraProvider

                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val resolutionSelector = ResolutionSelector.Builder()
                                            .setResolutionStrategy(
                                                ResolutionStrategy(
                                                    Size(1280, 720),
                                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                                )
                                            )
                                            .build()

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setResolutionSelector(resolutionSelector)
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                            if (viewModel.state.value !is ScanContactState.Scanning || barcodeScanner == null) {
                                                imageProxy.close()
                                                return@setAnalyzer
                                            }

                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val image = InputImage.fromMediaImage(
                                                    mediaImage,
                                                    imageProxy.imageInfo.rotationDegrees
                                                )
                                                barcodeScanner.process(image)
                                                    .addOnSuccessListener { barcodes ->
                                                        barcodes.firstOrNull()?.rawValue?.let { code ->
                                                            viewModel.onCodeScannedOrEntered(code)
                                                        }
                                                    }
                                                    .addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }

                                        runCatching {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                imageAnalysis
                                            )
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(260.dp)
                                    .border(2.dp, colors.accent.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(1.dp, colors.accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                )
                            }

                            Text(
                                text = "Point at a LANMAN QR code",
                                style = MaterialTheme.typography.labelLarge,
                                color = White,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Camera access is needed to scan QR codes.\nYou can still paste a pairing code below.",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                GlassButton(
                                    text = "Grant Camera Permission",
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    isPrimary = true
                                )
                            }
                        }
                    }

                    ManualEntrySection(
                        onAddClicked = { viewModel.onCodeScannedOrEntered(it) }
                    )
                }
            }

            is ScanContactState.Validating -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            }

            is ScanContactState.ConfirmationRequired -> {
                Dialog(onDismissRequest = { viewModel.resetToScanning() }) {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        tint = colors.dialogSurface,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (currentState.isUpdate) "Update Contact" else "Verify & Pair",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (currentState.isUpdate) {
                                    "Update encryption keys for ${currentState.payload.displayName}?"
                                } else {
                                    "Pair with ${currentState.payload.displayName}?"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )

                            if (currentState.identityKeyChanged) {
                                Spacer(modifier = Modifier.height(12.dp))
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    tint = colors.warning.copy(alpha = 0.14f),
                                    borderColor = colors.warning.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = colors.warning,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Encryption key changed since last pairing. Confirm only if they reset their identity.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }

                            if (!currentState.fingerprint.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "VERIFICATION CODE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.accent
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentState.fingerprint,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = colors.textPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Verify this matches the code shown on ${currentState.payload.displayName}'s screen",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        tint = colors.dialogSurface,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Could not pair",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
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

            is ScanContactState.Success -> {}
        }

        // Top overlay controls respecting status bar insets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.background(Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = White
                )
            }

            Surface(
                onClick = onNavigateToMyQr,
                shape = RoundedCornerShape(20.dp),
                color = Black.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QrCodeIcon(
                        contentDescription = "Show my QR",
                        tint = White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "My QR",
                        style = MaterialTheme.typography.labelLarge,
                        color = White
                    )
                }
            }
        }
    }
}

@Composable
fun ManualEntrySection(onAddClicked: (String) -> Unit) {
    var manualCode by remember { mutableStateOf("") }
    val colors = LanmanTheme.colors

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        tint = colors.surfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Manual pairing code",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            GlassTextField(
                value = manualCode,
                onValueChange = { manualCode = it },
                placeholder = "Paste pairing code",
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            GlassButton(
                text = "Pair Contact",
                onClick = { onAddClicked(manualCode) },
                isPrimary = true,
                enabled = manualCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
