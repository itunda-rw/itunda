package rw.itunda.feature.maps.impl

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

// Extracted from PlaceDetailAndRouteView (Maps product-completeness pass, 2026-09-07),
// same real "extract before growing further" precedent this file-size-lint guideline
// already established elsewhere in this module (SharedFolderSection/ItineraryBuilderCard/
// AroundYouSection/MapTopChrome) -- PlaceDetailAndRouteView crossed 500 lines the moment
// this dialog's 3 raw string literals were localized. Pure "values in, callbacks out"
// rendering, identical params as the block it was lifted from.
@Composable
internal fun BookmarkFolderDialog(
    folderNameInput: String,
    folderColorInput: String,
    bookmarking: Boolean,
    onFolderNameChange: (String) -> Unit,
    onFolderColorChange: (String) -> Unit,
    onConfirmSaveToFolder: () -> Unit,
    onCancelSaveToFolder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IdsTextField(
            value = folderNameInput,
            onValueChange = onFolderNameChange,
            label = stringResource(R.string.maps_folder_name_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Home", "Work").forEach { preset ->
                val active = folderNameInput.equals(preset, ignoreCase = true)
                Text(preset, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft).pressScaleClickable { onFolderNameChange(preset) }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BOOKMARK_COLOR_PALETTE.forEach { c ->
                val color = try { androidx.compose.ui.graphics.Color(AndroidColor.parseColor(c)) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFF5A623) }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(color, CircleShape)
                        .then(
                            if (folderColorInput == c) Modifier.border(2.dp, Ids.colors.textPrimary, CircleShape) else Modifier,
                        )
                        .pressScaleClickable { onFolderColorChange(c) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Ids.colors.brand, RoundedCornerShape(8.dp))
                    .pressScaleClickable(enabled = !bookmarking, onClick = onConfirmSaveToFolder)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (bookmarking) stringResource(R.string.maps_saving) else stringResource(R.string.maps_save), color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScaleClickable(enabled = !bookmarking, onClick = onCancelSaveToFolder)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.maps_cancel), color = Ids.colors.textSecondary, fontSize = 13.sp) }
        }
    }
}
