package rw.itunda.app.ui

import rw.itunda.core.designsystem.components.formatMoney
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CooperativeMembershipDto
import rw.itunda.core.network.CooperativeOverviewResponse
import rw.itunda.core.network.HarvestAdvanceDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.RegisterCooperativeRequest
import rw.itunda.core.network.RepayAdvanceRequest
import rw.itunda.core.network.RequestAdvanceRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import rw.itunda.core.designsystem.components.EmptyState

/**
 * Real Rwanda coffee-cooperative harvest-advance / input financing -- sourced beyond
 * this session's usual Toss/Kakao/Naver/Coupang reference ecosystems, grounded in
 * Rwanda's own real coffee sector (Rwanda Coffee Cooperatives Federation: 13 member
 * cooperatives, ~19,000 producer members). A direct itunda-to-farmer lending
 * relationship (real loan_payable receivable), NOT a cooperative-pool redistribution
 * like Ikimina. The third feature in this codebase not sourced from the reference
 * ecosystems. Mirrors bank-mfe's HarvestAdvanceView exactly, same no-ViewModel,
 * direct-NetworkClient-call convention SaccoScreen.kt/IkiminaScreen.kt already
 * established -- including the post-fix repayment contract: repayment is a single
 * "Repay in full" action sending the advance's own principalAmount, never a
 * user-editable amount field (a real debt-forgiveness bug this session caught and
 * fixed before shipping: an arbitrary repay amount let a token payment silently
 * close out the whole real debt).
 */
@Composable
fun HarvestAdvanceScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var memberships by remember { mutableStateOf<List<CooperativeMembershipDto>?>(null) }
    var advances by remember { mutableStateOf<List<HarvestAdvanceDto>?>(null) }
    // Real member-facing cooperative detail (2026-08-04) -- see ApiService.kt's own
    // getCooperativeOverview doc comment.
    var overview by remember { mutableStateOf<CooperativeOverviewResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var coopId by remember { mutableStateOf("") }
    var coopName by remember { mutableStateOf("") }
    var coopCrop by remember { mutableStateOf("COFFEE") }
    var advanceAmount by remember { mutableStateOf("") }
    var advancePurpose by remember { mutableStateOf("INPUT_FINANCING") }
    var harvestMonthsAway by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                memberships = NetworkClient.apiService.getMyCooperativeMemberships().memberships
                advances = NetworkClient.apiService.getMyHarvestAdvances().advances
                overview = memberships?.firstOrNull()?.let {
                    try { NetworkClient.apiService.getCooperativeOverview(it.cooperativeId) } catch (_: Exception) { null }
                }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun registerCooperative() {
        if (coopName.isBlank()) return
        busy = true
        coroutineScope.launch {
            try {
                val coop = NetworkClient.apiService.registerCooperative(RegisterCooperativeRequest(coopName.trim(), coopCrop.trim().ifBlank { "COFFEE" }, null)).cooperative
                NetworkClient.apiService.joinCooperative(coop.id)
                coopName = ""
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun joinCooperative() {
        if (coopId.isBlank()) return
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.joinCooperative(coopId.trim())
                coopId = ""
                error = null
                load()
            } catch (e: HttpException) {
                if (rw.itunda.core.network.apiErrorCode(e) == "ALREADY_MEMBER") {
                    // Real Toss-style resolution, not a dead-end error: the account
                    // genuinely IS already a member of this cooperative -- load
                    // existing membership state and move forward instead of erroring.
                    coopId = ""
                    error = null
                    load()
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun requestAdvance(membershipId: String) {
        val amount = advanceAmount.toBigDecimalOrNull()
        val months = harvestMonthsAway.toLongOrNull()
        if (amount == null || amount.signum() <= 0 || months == null || months <= 0) return
        busy = true
        coroutineScope.launch {
            try {
                val harvestDate = Instant.now().plus(months * 30, ChronoUnit.DAYS)
                NetworkClient.apiService.requestHarvestAdvance(
                    RequestAdvanceRequest(membershipId, amount, advancePurpose, harvestDate.toString()),
                    UUID.randomUUID().toString(),
                )
                advanceAmount = ""
                harvestMonthsAway = ""
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun disburse(advanceId: String) {
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.disburseHarvestAdvance(advanceId, UUID.randomUUID().toString())
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun repayInFull(advance: HarvestAdvanceDto) {
        busy = true
        coroutineScope.launch {
            try {
                NetworkClient.apiService.repayHarvestAdvance(advance.id, RepayAdvanceRequest(advance.principalAmount), UUID.randomUUID().toString())
                error = null
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Harvest advance", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Ids.layout.screenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Real input-financing and post-harvest advances for coffee cooperative members, matching Rwanda's own real coffee-sector financing gap.",
                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                )
            }
            error?.let { msg -> item { Text(msg, color = Ids.colors.danger, fontSize = 13.sp) } }

            val hasMembership = !memberships.isNullOrEmpty()
            if (!hasMembership) {
                item {
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                    // a lone form section on this screen when shown (docs/UI_UX_GUIDELINES.md §10).
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Register a cooperative", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IdsTextField(value = coopName, onValueChange = { coopName = it }, label = "Cooperative name", modifier = Modifier.fillMaxWidth())
                        IdsTextField(value = coopCrop, onValueChange = { coopCrop = it }, label = "Crop (e.g. COFFEE)", modifier = Modifier.fillMaxWidth())
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !busy && coopName.isNotBlank()) { registerCooperative() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busy) "…" else "Register & join", color = Color.White, fontWeight = FontWeight.Bold) }
                        Text("Already have a cooperative ID?", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        IdsTextField(value = coopId, onValueChange = { coopId = it }, label = "Cooperative ID", modifier = Modifier.fillMaxWidth())
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                .pressScaleClickable(enabled = !busy && coopId.isNotBlank()) { joinCooperative() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busy) "…" else "Join", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                val membershipId = memberships!!.first().id
                // Real fix (2026-08-24, flat-design sweep): dropped both Card wrappers --
                // 2 distinct sections shown together, separated by a real Divider instead
                // of separate card boxes (docs/UI_UX_GUIDELINES.md §10).
                overview?.let { o ->
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(o.cooperative.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "${o.cooperative.cropType} · ${o.memberCount} member${if (o.memberCount == 1) "" else "s"} · member since ${o.myMembership.memberSince.take(10)}",
                                color = Ids.colors.textSecondary, fontSize = 12.sp,
                            )
                            Divider(color = Ids.colors.divider, thickness = 0.5.dp)
                        }
                    }
                }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Request an advance", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IdsTextField(value = advanceAmount, onValueChange = { advanceAmount = it }, label = "Amount (RWF, max 500,000)", isAmount = true, modifier = Modifier.fillMaxWidth())
                        IdsTextField(value = advancePurpose, onValueChange = { advancePurpose = it }, label = "Purpose (INPUT_FINANCING / POST_HARVEST)", modifier = Modifier.fillMaxWidth())
                        IdsTextField(value = harvestMonthsAway, onValueChange = { harvestMonthsAway = it }, label = "Expected harvest (months from now)", modifier = Modifier.fillMaxWidth())
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                .pressScaleClickable(enabled = !busy && advanceAmount.toBigDecimalOrNull()?.signum() == 1 && harvestMonthsAway.toLongOrNull() != null) { requestAdvance(membershipId) }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busy) "…" else "Request advance", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            item { Text("My advances", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            when {
                advances == null -> item {
                    SkeletonBlock(height = 48.dp)
                }
                advances!!.isEmpty() -> item {
                    EmptyState("No advances yet.")
                }
                // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card --
                // ledger-style history list, kept the per-row Divider convention
                // (docs/DESIGN_REFERENCES.md §274).
                else -> items(advances!!, key = { it.id }) { a ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${formatMoney(a.principalAmount)} RWF · ${a.purpose}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(a.status, color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        Text("Repay by ${a.repaymentDueDate.take(10)}", color = Ids.colors.textSecondary, fontSize = 11.sp)
                        if (a.status == "REQUESTED") {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand)
                                    .pressScaleClickable(enabled = !busy) { disburse(a.id) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busy) "…" else "Disburse", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                        if (a.status == "DISBURSED" || a.status == "OVERDUE") {
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft)
                                    .pressScaleClickable(enabled = !busy) { repayInFull(a) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (busy) "…" else "Repay in full (${formatMoney(a.principalAmount)} RWF)", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                    }
                    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

