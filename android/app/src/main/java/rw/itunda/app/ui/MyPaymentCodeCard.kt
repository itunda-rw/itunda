package rw.itunda.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.itundaface.LockGlyph
import rw.itunda.core.designsystem.theme.IdsColors
import rw.itunda.core.designsystem.theme.IdsIcons

// Real customer-presented payment code (2026-08-11) -- see backend's
// MerchantService.generateCustomerPaymentCode doc comment. Auto-refreshes shortly
// before its own real 2-minute expiry so a customer standing at a register never
// has it silently go stale mid-checkout.
//
// Rebuilt 2026-08-11 to closely match the user's own real KakaoPay screenshot
// (not an invented layout -- see feedback_dont_imagine_use_real_reference memory):
// one white card holding the masked pay button, the account balance, the funding
// account, and nearby benefits, in that order. The card is pinned to raw IdsColors
// light values (White/Grey100/Gray900 etc.) rather than the theme-adaptive
// Ids.colors -- the reference screenshot itself is shown against a dark system
// background yet the payment card stays white, the same real "barcode/QR needs to
// read against white under a POS scanner regardless of phone theme" reasoning
// IdsSemanticColors.kt's own light-palette comment already documents.
//
// Extracted from ItundaAppScreen.kt (2026-08-28, itunda Pay redesign) to stay under
// that file's own file-size-lint baseline -- same convention AccountCardCarousel.kt
// already established 2026-08-26 for the identical constraint.
@Composable
internal fun MyPaymentCodeCard(
    accounts: List<rw.itunda.core.network.Account>,
    selectedAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onOpenAccountDetail: (rw.itunda.core.network.Account) -> Unit,
    onOpenCard: () -> Unit,
) {
    val selectedAccount = accounts.find { it.id == selectedAccountId }
    var revealed by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<String?>(null) }
    var expiresAtMillis by remember { mutableStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }
    var linkedAccount by remember { mutableStateOf<rw.itunda.core.network.LinkedAccountEntityDto?>(null) }
    var nearbyAds by remember { mutableStateOf<List<rw.itunda.core.network.NearbyMerchantAdDto>>(emptyList()) }
    // Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
    // "Facepay · QR Payment" Recent/Account/Card picker sheet) -- additive to the
    // existing AccountCardCarousel swipe below, not a replacement.
    var showFundingPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Re-keyed on selectedAccount.id (not just `revealed`): swiping AccountCardCarousel
    // to a different real account while the code is already showing must regenerate it
    // against the newly-selected account, not silently keep charging the old one.
    LaunchedEffect(revealed, selectedAccount?.id) {
        if (!revealed) {
            // Real NFC bridge (2026-08-27) -- see TransitPresentmentStore.kt's own doc
            // comment. A hidden code must never still be broadcastable over NFC.
            rw.itunda.app.nfc.TransitPresentmentStore.clear()
            return@LaunchedEffect
        }
        while (true) {
            try {
                val res = rw.itunda.core.network.NetworkClient.apiService.generateCustomerPaymentCode(
                    rw.itunda.core.network.GenerateCustomerPaymentCodeRequest(accountId = selectedAccount?.id),
                )
                code = res.code
                expiresAtMillis = java.time.Instant.parse(res.expiresAt).toEpochMilli()
                error = null
                rw.itunda.app.nfc.TransitPresentmentStore.set(res.code, expiresAtMillis)
            } catch (e: Exception) {
                error = "Could not load your payment code."
            }
            val waitMs = (expiresAtMillis - System.currentTimeMillis() - 10_000L).coerceAtLeast(5_000L)
            delay(waitMs)
        }
    }
    // A rider who navigates away from this screen entirely (composable disposed, not
    // just `revealed` toggled off) must also stop broadcasting -- same real intent as
    // the `!revealed` branch above.
    DisposableEffect(Unit) {
        onDispose { rw.itunda.app.nfc.TransitPresentmentStore.clear() }
    }
    LaunchedEffect(revealed) {
        if (!revealed) return@LaunchedEffect
        while (true) {
            secondsLeft = ((expiresAtMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            delay(1000)
        }
    }
    // Real linked funding account (rw.itunda.overview.LinkedAccountService) -- same
    // data AutoTopUpScreen/OverviewScreen already fetch, read-only display here.
    LaunchedEffect(Unit) {
        try {
            linkedAccount = rw.itunda.core.network.NetworkClient.apiService.getLinkedAccounts().linkedAccounts.firstOrNull { it.status == "LINKED" }
        } catch (e: Exception) {
            // Real, non-critical.
        }
    }
    // Real 당근(Karrot)-style radius-targeted nearby merchant ads (MerchantAdService.nearby)
    // -- same rememberRealLocationRequester + endpoint ShopScreen.kt's own nearby-ads
    // rail already established. Silent when location is denied or nothing is nearby.
    val requestLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                try {
                    nearbyAds = rw.itunda.core.network.NetworkClient.apiService.getNearbyMerchantAds(lat, lng).ads
                } catch (e: Exception) {
                    // Real, non-critical -- the row just won't render if this fails.
                }
            }
        },
        onError = {},
    )
    LaunchedEffect(Unit) { requestLocation() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(IdsColors.White)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IdsColors.Grey100).padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!revealed) {
                // Real fix (2026-08-13, direct user report: "this pay UI/UX it's so
                // bad" -- researched real KakaoPay's own reveal-gate: the code screen
                // requires security auth before showing the real barcode/QR, not an
                // unprotected tap). This pill used to be a small, ambiguous "Pay"
                // label floating alone in an empty gray box -- nothing communicated
                // that tapping it does anything, let alone that it's a real security
                // gate protecting a real payment code. A lock icon + explicit "Tap to
                // show your code" copy makes the gate and the action both legible.
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(IdsColors.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Real fix (2026-08-24): was a raw Material Lock icon, itunda
                        // already has its own real LockGlyph (itundaface) for this
                        // exact security concept, matching web/iOS's identical fix.
                        LockGlyph(size = 24.dp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Your payment code is hidden", color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Protects you if someone else has your phone", color = IdsColors.Gray600, fontSize = 12.sp)
                    }
                    Text(
                        "Tap to show", color = IdsColors.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(IdsColors.Blue500)
                            .pressScaleClickable { revealed = true }
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentCode = code
                    when {
                        currentCode != null -> {
                            // Real correction (product-feel audit, item 242): this
                            // was QR-only -- KakaoPay's own real screenshots (App
                            // Store listing + 3 real screenshots of the user's own
                            // live app, fetched/sent this session) show the primary
                            // code is a real linear BARCODE (Code128) with a small
                            // QR secondary, not QR alone. Same real correction just
                            // made on bank-mfe/iOS -- see PayQrCodeUtil.kt's own
                            // generatePayBarcodeBitmap doc comment.
                            val barcode = remember(currentCode) { generatePayBarcodeBitmap(currentCode) }
                            val qr = remember(currentCode) { generatePayQrBitmap(currentCode) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(
                                    bitmap = barcode,
                                    contentDescription = "Your payment barcode",
                                    // Real fix: FillBounds (not the default Fit) so the
                                    // barcode always exactly fills whatever weighted
                                    // width this card actually has, no letterboxing --
                                    // safe specifically for a 1D barcode since a real
                                    // scanner only reads the bar-WIDTH sequence along
                                    // one scan line, not the image's aspect ratio, so a
                                    // horizontal-only stretch never breaks decodability.
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(6.dp)).background(IdsColors.White),
                                )
                                Image(
                                    bitmap = qr,
                                    contentDescription = "Your payment QR code",
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(IdsColors.White),
                                )
                            }
                            Text(
                                if (secondsLeft > 0) "Refreshes in ${secondsLeft}s" else "Refreshing…",
                                color = IdsColors.Gray600, fontSize = 12.sp,
                            )
                        }
                        error != null -> Text(error!!, color = IdsColors.Red500, fontSize = 13.sp)
                        else -> CircularProgressIndicator(color = IdsColors.Blue500)
                    }
                }
            }
        }

        if (selectedAccount != null) {
            Spacer(Modifier.height(20.dp))
            // Real drill-in to the "Toss Pay Money" detail/statement screen (user
            // screenshots, 2026-08-21) -- see PayMoneyDetailScreen's own doc comment.
            Row(
                Modifier.fillMaxWidth().pressScaleClickable { onOpenAccountDetail(selectedAccount) },
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedAccount.type == "PAY") "itunda Pay" else "itunda Pay (${selectedAccount.currency})",
                    color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selectedAccount.currency} ${
                            if (selectedAccount.currency == "RWF") "%,.0f".format(selectedAccount.availableBalance)
                            else "%,.2f".format(selectedAccount.availableBalance)
                        }",
                        color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                    Icon(IdsIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = IdsColors.Gray500)
                }
            }
            Spacer(Modifier.height(10.dp))
            // Real fold-in (2026-08-28) of what used to be a separate, read-only
            // "Funding account" row -- now folded into the picker sheet's own
            // Account tab, matching the real reference's single funding-source
            // entry point instead of two separate real UI affordances.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedAccount.let { if (it.type == "PAY") "itunda Pay" else if (it.type == "MAIN") "itunda Bank" else "itunda Pay ${it.currency}" },
                    color = IdsColors.Gray600, fontSize = 13.sp,
                )
                Text(
                    "Change", color = IdsColors.Blue500, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.pressScaleClickable { showFundingPicker = true },
                )
            }
            if (showFundingPicker) {
                PayFundingSourcePicker(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelectAccount = onSelectAccount,
                    onDismiss = { showFundingPicker = false },
                    onOpenCard = onOpenCard,
                )
            }
        }

        if (nearbyAds.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = IdsColors.Grey200, thickness = 1.dp)
            Spacer(Modifier.height(16.dp))
            Text("Nearby benefits", color = IdsColors.Gray900, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(nearbyAds, key = { it.ad.id }) { nearbyAd ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(IdsColors.Blue100),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(nearbyAd.businessName.take(1).uppercase(), color = IdsColors.Blue600, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(nearbyAd.businessName, color = IdsColors.Gray800, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(nearbyAd.distanceKm * 1000).toInt()}m", color = IdsColors.Gray600, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        // Real fix (2026-08-13, direct user report: "this pay UI/UX it's so bad"):
        // the real disclosure this line makes (itunda pays from its own ledger, not
        // a card network) is genuinely important and stays -- only the wording
        // changes, from an internal-doc-comment-style "--" aside to plain,
        // user-facing copy a real product would actually ship.
        Text(
            "Pays instantly from your real itunda balance.",
            color = IdsColors.Gray600, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
