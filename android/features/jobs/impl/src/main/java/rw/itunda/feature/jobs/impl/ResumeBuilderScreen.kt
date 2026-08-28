package rw.itunda.feature.jobs.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddResumeCertificationRequest
import rw.itunda.core.network.AddResumeEducationRequest
import rw.itunda.core.network.AddResumeExperienceRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ResumeCertificationDto
import rw.itunda.core.network.ResumeDetailResponse
import rw.itunda.core.network.ResumeEducationDto
import rw.itunda.core.network.ResumeExperienceDto
import rw.itunda.core.network.ResumeStrengthDto
import rw.itunda.core.network.UpdateResumeProfileRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real 이력서 (Karrot 당근알바-style résumé) builder (itunda Hood redesign, 2026-08-28)
// -- see backend Resume.kt's own doc comment. A real completion-percent nudge, same
// reference-sourced "이력서를 완성해보세요 X%" the backend's own completionPercent
// already computes -- this screen just renders it, never re-derives it client-side.
@Composable
internal fun ResumeBuilderView() {
    var detail by remember { mutableStateOf<ResumeDetailResponse?>(null) }
    var strengthsCatalog by remember { mutableStateOf<List<ResumeStrengthDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun reload() {
        coroutineScope.launch {
            try { detail = NetworkClient.hoodApi.getMyResume(); error = null }
            catch (e: HttpException) { error = superAppErrorMessage(e) }
            catch (e: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
        }
    }
    LaunchedEffect(Unit) {
        try { strengthsCatalog = NetworkClient.hoodApi.getResumeStrengths().strengths } catch (e: Exception) { /* chips just won't render */ }
        reload()
    }

    val current = detail
    if (error != null && current == null) { ErrorCard(error!!, onRetry = ::reload); return }
    if (current == null) { SkeletonBlock(); return }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Complete your résumé", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${current.completionPercent}%", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(Ids.colors.surfaceSoft, RoundedCornerShape(999.dp))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (current.completionPercent / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Ids.colors.brand, RoundedCornerShape(999.dp)),
                )
            }
        }
        ResumeProfileSection(current, onSaved = ::reload)
        ResumeExperienceSection(current.experiences, onChanged = ::reload)
        ResumeEducationSection(current.educations, onChanged = ::reload)
        ResumeCertificationSection(current.certifications, onChanged = ::reload)
    }
}

@Composable
private fun ResumeProfileSection(detail: ResumeDetailResponse, onSaved: () -> Unit) {
    var selfIntro by remember(detail.resume?.id) { mutableStateOf(detail.resume?.selfIntro.orEmpty()) }
    var additionalInfo by remember(detail.resume?.id) { mutableStateOf(detail.resume?.additionalInfo.orEmpty()) }
    var selectedStrengths by remember(detail.resume?.id) {
        mutableStateOf((detail.resume?.strengths ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
    }
    var strengthsCatalog by remember { mutableStateOf<List<ResumeStrengthDto>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { strengthsCatalog = NetworkClient.hoodApi.getResumeStrengths().strengths } catch (e: Exception) { /* chips just won't render */ } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("About you", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        IdsTextField(value = selfIntro, onValueChange = { selfIntro = it }, label = "Self-introduction", singleLine = false, modifier = Modifier.fillMaxWidth())
        if (strengthsCatalog.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                strengthsCatalog.forEach { s ->
                    val active = s.id in selectedStrengths
                    Text(
                        s.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else Ids.colors.textPrimary,
                        modifier = Modifier
                            .background(if (active) Ids.colors.brand else Ids.colors.surfaceSoft, RoundedCornerShape(999.dp))
                            .pressScaleClickable { selectedStrengths = if (active) selectedStrengths - s.id else selectedStrengths + s.id }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        IdsTextField(value = additionalInfo, onValueChange = { additionalInfo = it }, label = "Additional info (optional)", singleLine = false, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        TextButton(
            enabled = !saving,
            onClick = {
                saving = true
                coroutineScope.launch {
                    try {
                        NetworkClient.hoodApi.updateResumeProfile(UpdateResumeProfileRequest(selfIntro.trim().ifEmpty { null }, selectedStrengths.toList(), additionalInfo.trim().ifEmpty { null }))
                        onSaved()
                    } catch (e: HttpException) {
                        error = superAppErrorMessage(e)
                    } catch (e: IOException) {
                        error = "Couldn't reach itunda. Check your connection and try again."
                    } finally {
                        saving = false
                    }
                }
            },
        ) { Text(if (saving) "Saving…" else "Save") }
    }
}

@Composable
private fun ResumeExperienceSection(experiences: List<ResumeExperienceDto>, onChanged: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var company by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Experience", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "+ Add") }
        }
        experiences.forEach { exp ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${exp.role} · ${exp.company}", color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(exp.period, color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { coroutineScope.launch { try { NetworkClient.hoodApi.removeResumeExperience(exp.id); onChanged() } catch (e: Exception) { /* best-effort */ } } }) { Text("Remove") }
            }
        }
        if (adding) {
            IdsTextField(value = company, onValueChange = { company = it }, label = "Company", singleLine = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = role, onValueChange = { role = it }, label = "Role", singleLine = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = period, onValueChange = { period = it }, label = "Period (e.g. 2023 – present)", singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            TextButton(onClick = {
                coroutineScope.launch {
                    try {
                        NetworkClient.hoodApi.addResumeExperience(AddResumeExperienceRequest(company.trim(), role.trim(), period.trim()))
                        company = ""; role = ""; period = ""; adding = false; error = null
                        onChanged()
                    } catch (e: HttpException) { error = superAppErrorMessage(e) } catch (e: IOException) { error = "Couldn't reach itunda." }
                }
            }) { Text("Save") }
        }
    }
}

@Composable
private fun ResumeEducationSection(educations: List<ResumeEducationDto>, onChanged: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var school by remember { mutableStateOf("") }
    var degree by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Education", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "+ Add") }
        }
        educations.forEach { edu ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(listOfNotNull(edu.school, edu.degree).joinToString(" · "), color = Ids.colors.textPrimary, fontSize = 13.sp)
                TextButton(onClick = { coroutineScope.launch { try { NetworkClient.hoodApi.removeResumeEducation(edu.id); onChanged() } catch (e: Exception) { /* best-effort */ } } }) { Text("Remove") }
            }
        }
        if (adding) {
            IdsTextField(value = school, onValueChange = { school = it }, label = "School", singleLine = true, modifier = Modifier.fillMaxWidth())
            IdsTextField(value = degree, onValueChange = { degree = it }, label = "Degree (optional)", singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            TextButton(onClick = {
                coroutineScope.launch {
                    try {
                        NetworkClient.hoodApi.addResumeEducation(AddResumeEducationRequest(school.trim(), degree.trim().ifEmpty { null }))
                        school = ""; degree = ""; adding = false; error = null
                        onChanged()
                    } catch (e: HttpException) { error = superAppErrorMessage(e) } catch (e: IOException) { error = "Couldn't reach itunda." }
                }
            }) { Text("Save") }
        }
    }
}

@Composable
private fun ResumeCertificationSection(certifications: List<ResumeCertificationDto>, onChanged: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Certifications", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "+ Add") }
        }
        certifications.forEach { cert ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(cert.name, color = Ids.colors.textPrimary, fontSize = 13.sp)
                TextButton(onClick = { coroutineScope.launch { try { NetworkClient.hoodApi.removeResumeCertification(cert.id); onChanged() } catch (e: Exception) { /* best-effort */ } } }) { Text("Remove") }
            }
        }
        if (adding) {
            IdsTextField(value = name, onValueChange = { name = it }, label = "Certification name", singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            TextButton(onClick = {
                coroutineScope.launch {
                    try {
                        NetworkClient.hoodApi.addResumeCertification(AddResumeCertificationRequest(name.trim()))
                        name = ""; adding = false; error = null
                        onChanged()
                    } catch (e: HttpException) { error = superAppErrorMessage(e) } catch (e: IOException) { error = "Couldn't reach itunda." }
                }
            }) { Text("Save") }
        }
    }
}
