package com.itunda.app.ui.screens.qr

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itunda.app.ui.theme.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScreen() {
    var tab by remember { mutableStateOf(0) } // 0 = My QR, 1 = Scan

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        TopAppBar(
            title = { Text("QR", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.White,
            contentColor = Primary
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("My QR", fontWeight = FontWeight.SemiBold) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Scan", fontWeight = FontWeight.SemiBold) })
        }

        if (tab == 0) MyQrTab() else ScanTab()
    }
}

@Composable
private fun MyQrTab() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Uwase Diane", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
        Text("+250 788 123 456", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(20.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.size(260.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                MockQrPattern(seed = "itunda-uwase-diane")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Show this code to receive a payment", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Icon(Icons.Outlined.Share, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {},
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Request amount", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ScanTab() {
    val transition = rememberInfiniteTransition(label = "scan")
    val lineY by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "line"
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF191F28)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            )
            Canvas(modifier = Modifier.size(200.dp)) {
                val y = size.height * lineY
                drawLine(
                    color = Primary,
                    start = Offset(8f, y),
                    end = Offset(size.width - 8f, y),
                    strokeWidth = 4f
                )
            }
            Icon(
                Icons.Outlined.CameraAlt, null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Point your camera at a merchant or personal QR code", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {}.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.QrCode2, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Enter code manually", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun MockQrPattern(seed: String) {
    val rng = remember(seed) { Random(seed.hashCode()) }
    val grid = remember(seed) { List(11) { row -> List(11) { col ->
        // finder patterns in three corners, pseudo-random elsewhere
        val inFinder = (row < 3 && col < 3) || (row < 3 && col > 7) || (row > 7 && col < 3)
        if (inFinder) (row % 2 == 0 || col % 2 == 0) else rng.nextBoolean()
    } } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cell = size.width / 11
        grid.forEachIndexed { r, rowList ->
            rowList.forEachIndexed { c, filled ->
                if (filled) {
                    drawRect(
                        color = Color(0xFF191F28),
                        topLeft = Offset(c * cell, r * cell),
                        size = androidx.compose.ui.geometry.Size(cell * 0.92f, cell * 0.92f)
                    )
                }
            }
        }
    }
}
