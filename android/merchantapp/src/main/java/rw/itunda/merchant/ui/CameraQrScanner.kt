package rw.itunda.merchant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Real camera-based QR scanning (2026-08-11) -- the FIRST real camera-scanning
 * capability anywhere in this monorepo, on any platform/app (confirmed via a
 * full-repo audit before building this: every prior "Pay" flow was manual code
 * entry, itunda's own doc comments across Android/iOS/web all admitted "this app
 * has no scanner"). CameraX for the real camera preview/frame pipeline, ML Kit
 * Barcode Scanning for on-device (no network round-trip, no per-scan cost) QR
 * decoding -- the standard modern Android combination, not a third-party SDK.
 *
 * `onScanned` fires at most once per composition of this scanner (guarded
 * internally) -- the caller owns what happens next (stop showing the camera,
 * navigate to a confirm step, etc.), this composable's only job is "find a QR,
 * report its raw text."
 *
 * Real Toss-sourced accessibility fix (2026-08-23, toss.tech/article/accessibility_face) --
 * see core:designsystem's own identical `CameraQrScanner.kt` doc comment for the full sourced
 * account (this is a deliberate duplicate, per this file's own header comment, so the fix is
 * applied here too rather than only in the shared copy). A distinct "scanning"/"found"
 * announcement via `announceForAccessibility` where none existed before, plus a 500ms delay
 * before `onScanned` fires so TalkBack has time to speak the "found" announcement before the
 * caller reacts (typically navigating away, tearing this composable down).
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
            Text("Camera permission is needed to scan a customer's payment code.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Guards onScanned firing more than once for the same scan session -- ML Kit's
    // analyzer keeps running on every camera frame until the caller tears this
    // composable down, so without this a single held-up code would fire dozens of
    // times before the caller reacts to the first one.
    var scanned by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(false) }
    val onScannedState = rememberUpdatedState(onScanned)
    val scanner = remember { BarcodeScanning.getClient() }

    LaunchedEffect(Unit) {
        view.announceForAccessibility("Camera ready. Point at a customer's payment code.")
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
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
                                        found = true
                                        view.announceForAccessibility("QR code found")
                                        scope.launch {
                                            delay(500)
                                            onScannedState.value(value)
                                        }
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
                            // rather than crashing the checkout flow.
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )
        if (found) {
            Text(
                "QR code found",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
        }
    }
}
