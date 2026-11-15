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

private fun rwf(v: Double) = String.format(Locale.US, "%,.0f", v)

private data class HomeCopy(
    val bank:String,val pay:String,val send:String,val add:String,val quick:String,val scan:String,val bills:String,val airtime:String,
    val services:String,val irembo:String,val taxes:String,val electricity:String,val water:String,val school:String,val spending:String,
    val spent:String,val due:String,val forYou:String,val goal:String,val goalHint:String,val credit:String,val creditHint:String,
    val view:String,val status:String,val profile:String,val notifications:String,
)
private fun homeCopy(language:String)=when(language){
"rw"->HomeCopy("itunda Bank","itunda Pay","Ohereza","Ongeraho","Ibikorwa byihuse","Sikana wishyure","Ishyura fagitire","Igihe cyo guhamagara","Serivisi zo mu Rwanda","Irembo","Imisoro ya RRA","Amashanyarazi","Amazi","Amafaranga y'ishuri","Imikoreshereze","Yakoreshejwe muri iki gihe","Birishyurwa vuba","Byagenewe wowe","Tangira intego yawe yo kuzigama","Hitamo amafaranga n'igihe uzayageraho","Amanota y'inguzanyo","Reba amanota yawe ku buntu","Reba","MTN MoMo na Airtel Money birakora neza","Umwirondoro","Amamenyesha")
"fr"->HomeCopy("itunda Bank","itunda Pay","Envoyer","Ajouter","Actions rapides","Scanner pour payer","Payer les factures","Crédit téléphonique","Services au Rwanda","Irembo","Impôts RRA","Électricité","Eau","Frais scolaires","Dépenses","Dépensé pendant cette période","À payer prochainement","Pour vous","Créez votre premier objectif d’épargne","Choisissez un montant et une date cible","Votre score de crédit","Vérification gratuite avec vos données alternatives","Voir","MTN MoMo et Airtel Money fonctionnent normalement","Profil","Notifications")
else->HomeCopy("itunda Bank","itunda Pay","Send","Add","Quick actions","Scan to pay","Pay bills","Airtime","Rwanda services","Irembo","RRA taxes","REG electricity","WASAC water","School fees","Spending","Spent this period, mostly on food","Upcoming, due Friday","For you","Start your first savings goal","A target and a date make saving easier","My credit score","Free check, alternative data","View","MTN MoMo and Airtel Money are working normally","Profile","Notifications")
}

@Composable
fun ProtoHomeContent(
state:ProtoHomeState,hasUnread:Boolean,onOpenBank:()->Unit,onOpenPay:()->Unit,onOpenNotifications:()->Unit,
onSend:()->Unit,onAdd:()->Unit,onBills:()->Unit,onCard:()->Unit,onSavings:()->Unit,onSeeAll:()->Unit,
onInvite:()->Unit,onOpenSpending:()->Unit,onPayBill:()->Unit,onOpenRewards:()->Unit,
onOpenProfile:()->Unit,onOpenCreditScore:()->Unit,
){
val language=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]?.language?:"en"
val t=homeCopy(language)
var inviteShown by remember{mutableStateOf(true)}
LazyColumn(Modifier.fillMaxSize().background(Ids.colors.background),contentPadding=androidx.compose.foundation.layout.PaddingValues(bottom=96.dp)){
item{HomeProtoHeader(hasUnread,onOpenBank,onOpenPay,onOpenNotifications,t.notifications)}
item{HomeAccountsCard(state.bankBalance,state.payBalance,state.interestDays,onOpenBank,onOpenPay,onSend,onAdd,onBills,onCard,onSavings,onSeeAll)}
if(inviteShown) item{HomeInviteBanner(onInvite){inviteShown=false}}
item{Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(top=8.dp)){
Row(Modifier.fillMaxWidth().clickable(onClick=onOpenSpending),verticalAlignment=Alignment.CenterVertically){
Column(Modifier.weight(1f)){
Row(verticalAlignment=Alignment.Bottom){Text(rwf(state.spentThisMonth),color=Ids.colors.textPrimary,fontSize=24.sp,fontWeight=FontWeight.ExtraBold);Text(" RWF",color=Ids.colors.textSecondary,fontSize=14.sp,modifier=Modifier.padding(bottom=3.dp))}
Text("Spent in ${state.monthName}",color=Ids.colors.textSecondary,fontSize=15.sp)}
ItundaArt("chev",ItundaArtSet.Line,18.dp,tint=Ids.colors.textTertiary)}
Spacer(Modifier.height(16.dp));ProtoWeekChart(state.week,state.today)
}}
state.bill?.let{b->item{HomeRow("bill",b.title,"Due ${b.due} · ${rwf(b.amount)} RWF","Pay",onPayBill)}}
item{HomeRow("gift","${rwf(state.earned)} RWF earned","Points and coupons","View",onOpenRewards)}
item{HomeSectionHeader("Pockets","See all",onSavings)}
item{HomeRow("school-fees","School fees","120,000 RWF","Top up",onSavings)}
item{HomeRow("lock","Emergency fund","50,000 RWF","Top up",onSavings)}
item{HomeSectionHeader("Around you",null){}}
item{HomeNavRow("market","Kigali Roast","Cafe · open till 20:00 · 3 min walk"){}}
item{HomeNavRow("restaurant","Eats","Free delivery on your first order"){}}
item{HomeNavRow("moto","Home","About 24 min by bus · leave now"){}}
item{HomeSectionHeader("Recent activity","See all",onSeeAll)}
item{HomeActivityRow("receive","Amina K.","Received · Yesterday","+25,000 RWF",null)}
item{HomeActivityRow("transit","Kigali Bus Services","Paid · Tuesday","−300 RWF","+9 earned")}
item{HomeSettingsButton{}}
}
}
@Composable private fun HomeHeader(hasUnread:Boolean,onOpenNotifications:()->Unit,onOpenProfile:()->Unit,notificationsLabel:String,profileLabel:String){
Row(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,top=20.dp),verticalAlignment=Alignment.CenterVertically){
Icon(painterResource(rw.itunda.core.designsystem.R.drawable.itunda_mark),null,Modifier.size(40.dp),tint=Ids.colors.brand)
Spacer(Modifier.width(8.dp));Text("itunda",color=Ids.colors.textPrimary,fontSize=20.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-0.5).sp)
Spacer(Modifier.weight(1f))
Box(Modifier.size(44.dp).clickable(onClick=onOpenNotifications).semantics{contentDescription=notificationsLabel},contentAlignment=Alignment.Center){
ItundaArt("bell",ItundaArtSet.Line,24.dp,tint=Ids.colors.textPrimary)
if(hasUnread) Box(Modifier.align(Alignment.TopEnd).padding(top=8.dp,end=8.dp).size(8.dp).clip(CircleShape).background(Ids.colors.danger))
}
Box(Modifier.size(44.dp).clickable(onClick=onOpenProfile).semantics{contentDescription=profileLabel},contentAlignment=Alignment.Center){ItundaArt("user",ItundaArtSet.Line,24.dp,tint=Ids.colors.textPrimary)}
}
}
@Composable private fun HomeSection(title:String,content: @Composable () -> Unit){
Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(top=32.dp)){Text(title,color=Ids.colors.textPrimary,fontSize=18.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(bottom=8.dp));content()}
}
@Composable private fun HomeGridAction(name:String,label:String,onClick:()->Unit,modifier:Modifier=Modifier){
Column(modifier.clickable(onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
Box(Modifier.size(60.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.surfaceSoft),contentAlignment=Alignment.Center){ItundaArt(name,if(name in setOf("qr","phone","bolt","drop")) ItundaArtSet.Line else ItundaArtSet.Proto,38.dp,tint=if(name in setOf("qr","phone","bolt","drop")) Ids.colors.textPrimary else null)}
Text(label,color=Ids.colors.textPrimary,fontSize=12.sp,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center,lineHeight=15.sp,maxLines=2)
}
}
@Composable private fun HomeListRow(icon:String,title:String,subtitle:String,trailing:String?,onClick:()->Unit){
Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=11.dp),verticalAlignment=Alignment.CenterVertically){
Box(Modifier.size(44.dp).clip(CircleShape).background(Ids.colors.surfaceSoft),contentAlignment=Alignment.Center){ItundaArt(icon,if(icon in setOf("coin","trend","chev")) ItundaArtSet.Line else ItundaArtSet.Proto,30.dp)}
Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=Ids.colors.textPrimary,fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=1);Text(subtitle,color=Ids.colors.textSecondary,fontSize=13.sp,lineHeight=18.sp,maxLines=2)}
if(trailing!=null){Text(trailing,color=Ids.colors.textSecondary,fontSize=14.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.width(4.dp))}
ItundaArt("chev",ItundaArtSet.Line,16.dp,tint=Ids.colors.textTertiary)
}
}
@Composable private fun HomeActionButton(label:String,primary:Boolean,modifier:Modifier,onClick:()->Unit){
Box(modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(if(primary)Ids.colors.brand else Ids.colors.surfaceSoft).clickable(onClick=onClick),contentAlignment=Alignment.Center){
Text(label,color=if(primary)Color(0xFF17171C)else Ids.colors.textPrimary,fontSize=16.sp,fontWeight=FontWeight.ExtraBold)
}
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ids.colors.surface).then(modifier)) { content() }
}

@Composable
private fun WalletGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawRoundRect(Color(0xFFFFC83D), Offset(4 * u, 3 * u), androidx.compose.ui.geometry.Size(13 * u, 8 * u), androidx.compose.ui.geometry.CornerRadius(3 * u))
        drawRoundRect(Color(0xFF7B74F5), Offset(2 * u, 7 * u), androidx.compose.ui.geometry.Size(20 * u, 14 * u), androidx.compose.ui.geometry.CornerRadius(4 * u))
        drawCircle(Color(0xFFFFC83D), 2 * u, Offset(17 * u, 14 * u))
    }
}

@Composable
private fun IconTile(content: @Composable () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun AmountLine(amount: Double) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(rwf(amount), color = Ids.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(" RWF", color = Ids.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun AccountRow(tile: @Composable () -> Unit, amount: Double, subtitle: androidx.compose.ui.text.AnnotatedString, action: String, onAction: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        IconTile(tile)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            AmountLine(amount)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 14.sp)
        }
        Pill(action, onAction)
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).widthIn(min = 64.dp).clip(RoundedCornerShape(50)).background(Ids.colors.brand.copy(alpha = 0.14f)).pressScaleClickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Ids.colors.textBrand, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ShortcutText(label: String, onClick: () -> Unit) {
    Text(label, color = Ids.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.pressScaleClickable(onClick = onClick).padding(vertical = 14.dp))
}
