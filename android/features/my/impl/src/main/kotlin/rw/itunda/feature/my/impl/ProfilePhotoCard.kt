package rw.itunda.feature.my.impl

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.IdsAvatar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.superAppErrorMessage
import retrofit2.HttpException
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

// Profile photo (prototype v929: tap the photo, pick one). The old card asked people to
// paste a link because itunda "doesn't host photo uploads yet"; it does now
// (`uploadPhoto` returns a hosted URL, same pipeline Property's ownership documents
// use), so this picks from the gallery, uploads, and saves the returned URL with the
// existing `PUT /api/v1/auth/profile/photo`. Camera and the prototype's 24 characters
// are not built: camera needs a FileProvider + permission flow, characters need assets.
//
// Flat, no card (UI_UX_GUIDELINES rule 10).
@Composable
internal fun ProfilePhotoCard() {
    var profilePhotoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val user = rw.itunda.core.network.NetworkClient.authApi.getProfile().user
            profilePhotoUrl = user.profilePhotoUrl
            displayName = "${user.firstName} ${user.lastName}".trim()
        } catch (_: Exception) {
            // Real, non-critical -- the rest of "My" still works without this.
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        saving = true
        error = null
        coroutineScope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    error = "Couldn't read that photo."
                    return@launch
                }
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val part = MultipartBody.Part.createFormData("file", "profile.jpg", bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                val hostedUrl = rw.itunda.core.network.NetworkClient.apiService.uploadPhoto(part).url
                profilePhotoUrl = rw.itunda.core.network.NetworkClient.authApi.updateProfilePhoto(
                    rw.itunda.core.network.UpdateProfilePhotoRequest(hostedUrl),
                ).user.profilePhotoUrl
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                saving = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IdsAvatar(name = displayName.ifBlank { "?" }, photoUrl = profilePhotoUrl, size = 80.dp)
        if (displayName.isNotBlank()) {
            Text(displayName, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp) }
        IdsButton(
            text = if (saving) "Saving…" else if (profilePhotoUrl.isNullOrBlank()) "Add a photo" else "Change photo",
            enabled = !saving,
            onClick = { pickPhoto.launch("image/*") },
            variant = IdsButtonVariant.Tinted,
        )
    }
}
