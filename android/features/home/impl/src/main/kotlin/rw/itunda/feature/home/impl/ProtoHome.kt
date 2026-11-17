package rw.itunda.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import rw.itunda.core.designsystem.components.IdsAppBarTitle
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * Consumer Home composed to follow the supplied v1038 prototype at its 390dp viewport:
 * brand/profile header, provider-status line, Bank and Pay balances, primary actions,
 * Quick actions, Rwanda services, Spending, and For you. Keep this screen flat and
 * content-led: no dashboard cards or extra promotional modules absent from the source.
 *
 * Demo-only bill data is supplied by ProtoHomeTab; account and spending figures remain
 * sourced from the existing app state. Keep interactions routed through app callbacks.
 */
data class ProtoBill(val title: String, val due: String, val amount: Double)

data class ProtoHomeState(
    val bankBalance: Double,
    val payBalance: Double,
    val interestDays: Int?,
    val spentThisMonth: Double,
    val monthName: String,
    val week: List<DaySpend>,
    val bill: ProtoBill?,
    val earned: Double,
    val today: java.time.LocalDate = java.time.LocalDate.now(),
)

internal fun homeRwf(v: Double) = String.format(Locale.US, "%,.0f", v)

@Composable
fun ProtoHomeContent(
state:ProtoHomeState,hasUnread:Boolean,onOpenBank:()->Unit,onOpenPay:()->Unit,onOpenNotifications:()->Unit,
onSend:()->Unit,onAdd:()->Unit,onBills:()->Unit,onCard:()->Unit,onSavings:()->Unit,onSeeAll:()->Unit,
onInvite:()->Unit,onOpenSpending:()->Unit,onPayBill:()->Unit,onOpenRewards:()->Unit,
onOpenProfile:()->Unit,onOpenCreditScore:()->Unit,onOpenShop:()->Unit,onOpenTransit:()->Unit,
){
val p=homePalette()
val bellLabel=when(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]?.language){"rw"->"Amamenyesha";"fr"->"Notifications";else->"Notifications"}
// Every vertical gap below is the prototype's measured card spacing: cards sit 12dp apart.
androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontFamily=rw.itunda.core.designsystem.theme.PretendardFontFamily,lineHeightStyle=androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,androidx.compose.ui.text.style.LineHeightStyle.Trim.None),platformStyle=androidx.compose.ui.text.PlatformTextStyle(includeFontPadding=false))){
LazyColumn(Modifier.fillMaxSize().background(Ids.colors.background),contentPadding=androidx.compose.foundation.layout.PaddingValues(bottom=96.dp)){
item{HomeProtoHeader(hasUnread,onOpenBank,onOpenPay,onOpenNotifications,bellLabel)}
item{Spacer(Modifier.height(12.dp));HomeAccountsCard(state.bankBalance,state.payBalance,state.interestDays,onOpenBank,onOpenPay,onSend,onAdd,onBills,onCard,onSavings,onSeeAll)}
item{Spacer(Modifier.height(12.dp));HomeInviteBanner(onInvite)}
item{Spacer(Modifier.height(12.dp+6.dp));HomeSpendingRow(state.spentThisMonth,state.monthName,onOpenSpending);ProtoWeekChart(state.week,state.today)}
state.bill?.let{b->item{Spacer(Modifier.height(6.dp));HomeRow({ItundaArt("bill",ItundaArtSet.Proto,20.7.dp)},b.title,"Due ${b.due} · ${homeRwf(b.amount)} RWF","Pay",onPayBill);Spacer(Modifier.height(8.dp))}}
item{Spacer(Modifier.height(12.dp+6.dp));HomeRow({ItundaArt("star-solid",ItundaArtSet.Solid,18.dp,tint=Color(0xFFFFC83D))},"${homeRwf(state.earned)} RWF earned","Points and coupons","View",onOpenRewards);Spacer(Modifier.height(8.dp))}
item{Spacer(Modifier.height(18.dp));HomeSectionHeader("Pockets","See all",onSavings)}
item{HomePocketRow({ItundaArt("school",ItundaArtSet.Line,22.dp,tint=p.tintFg)},0.4f,"School fees","120,000 RWF",onSavings)}
item{HomePocketRow({ItundaArt("lock",ItundaArtSet.Proto,17.5.dp)},null,"Emergency fund","50,000 RWF",onSavings);Spacer(Modifier.height(8.dp))}
item{Spacer(Modifier.height(18.dp));HomeSectionHeader("Around you",null){}}
item{HomeNavRow("market","Kigali Roast","Cafe · open till 20:00 · 3 min walk",onOpenShop)}
item{HomeNavRow("restaurant","Eats","Free delivery on your first order",onOpenShop)}
item{HomeNavRow("moto","Home","About 24 min by bus · leave now",onOpenTransit);Spacer(Modifier.height(8.dp))}
item{Spacer(Modifier.height(18.dp));HomeSectionHeader("Recent activity","See all",onSeeAll)}
item{HomeActivityRow("receive","Amina K.","Received · Yesterday","+25,000 RWF",null)}
item{HomeActivityRow("transit","Kigali Bus Services","Paid · Tuesday","\u2212300 RWF","+9 earned");Spacer(Modifier.height(8.dp))}
item{HomeSettingsButton(onOpenProfile)}
}
}
}
