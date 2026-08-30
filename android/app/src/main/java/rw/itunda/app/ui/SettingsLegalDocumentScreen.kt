package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.LegalDocument
import rw.itunda.core.network.SessionManager

/**
 * Real itunda-branded Terms of Service/Privacy Policy/Credit Data Policy full-text
 * viewer -- closes SettingsScreen.kt's own long-documented "Legal Documents" gap
 * (three plain, deliberately non-clickable labels since 2026-08-12, because itunda
 * had no document screen to link to yet). See LegalDocument.kt's own doc comment on
 * the backend for the full sourced account of these documents' content and why they
 * exist. Extracted into its own file rather than added inline to SettingsScreen.kt,
 * which was already sitting near its own file-size-lint baseline.
 */
@Composable
fun LegalDocumentScreen(documentId: String, fallbackTitle: String, onBack: () -> Unit) {
    var document by remember { mutableStateOf<LegalDocument?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        val documents = SessionManager.getLegalDocuments()
        document = documents.find { it.id == documentId }
        loadFailed = document == null
    }

    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        SettingsSubScreenHeader(document?.title ?: fallbackTitle, onBack)
        when {
            document != null -> Text(
                text = document!!.bodyMarkdown,
                color = Ids.colors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
            loadFailed -> Text(
                text = "Couldn't load this document. Check your connection and try again.",
                color = Ids.colors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp),
            )
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ids.colors.brand)
            }
        }
    }
}
