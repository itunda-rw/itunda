package rw.itunda.rider.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real device-location fetch for the rider app -- its own minimal copy, mirroring
 * the shape of the consumer app's rememberRealLocationRequester, but a suspend
 * one-shot function instead of a callback: this app needs to await a fresh fix
 * inside a periodic coroutine loop (see RiderLocationPusher), not just a single
 * user-triggered tap.
 */
fun hasFineLocationPermission(context: Context): Boolean =
    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

suspend fun fetchLocationOnce(context: Context): Location? {
    if (!hasFineLocationPermission(context)) return null
    return suspendCancellableCoroutine { cont ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        cont.invokeOnCancellation { cts.cancel() }
    }
}

/** Real runtime permission prompt, requested once when a screen that needs it first appears. */
@Composable
fun rememberFineLocationPermissionGranted(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasFineLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    return granted
}
