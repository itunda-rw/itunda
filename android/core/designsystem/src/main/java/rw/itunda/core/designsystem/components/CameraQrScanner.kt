package rw.itunda.core.designsystem.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Real camera-based QR scanning -- relocated here (item 244) from
 * :features:shop:impl (where it was built 2026-08-11 for Pay's scan-a-merchant's-
 * QR flow) once a real second Feature module (:features:talk:impl, Open Chat's
 * join-by-scan) needed the identical capability. Feature-module isolation
 * (Konsist-enforced, see root CLAUDE.md) forbids :features:talk:impl importing
 * :features:shop:impl's impl directly, so a shared home was the only compliant
 * option once this second real need existed -- same "promote once 2+ real needs
 * exist" precedent RouteMiniMap.kt/HoodShared.kt/IdsAvatar.kt in this same
 * package already establish. `merchantapp`'s own separate copy (a different
 * Gradle application entirely) stays a deliberate duplicate, out of scope for
 * this promotion.
 *
 * CameraX for the real camera preview/frame pipeline, ML Kit Barcode Scanning
 * for on-device (no network round-trip, no per-scan cost) QR decoding.
 *
 * `onScanned` fires at most once per composition of this scanner (guarded
 * internally) -- the caller owns what happens next, this composable's only job
 * is "find a QR, report its raw text."
 */
@Composable
fun CameraQrScanner(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission is needed to scan a QR code.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    // Guards onScanned firing more than once for the same scan session -- ML Kit's
    // analyzer keeps running on every camera frame until the caller tears this
    // composable down, so without this a single held-up code would fire dozens of
    // times before the caller reacts to the first one.
    var scanned by remember { mutableStateOf(false) }
    val onScannedState = rememberUpdatedState(onScanned)
    val scanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || scanned) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                                if (value != null && !scanned) {
                                    scanned = true
                                    onScannedState.value(value)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    } catch (_: Exception) {
                        // Real, non-critical -- e.g. no back camera on this device. The
                        // permission-denied empty state above already covers the other
                        // real "can't scan" case; this one just leaves a blank preview
                        // rather than crashing the caller's flow.
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
        }
    }
}
