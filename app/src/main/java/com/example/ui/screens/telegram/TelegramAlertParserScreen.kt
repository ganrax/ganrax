package com.example.ui.screens.telegram

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ParsedTelegramAlert
import com.example.ui.components.AppHeader
import com.example.ui.theme.*
import com.example.ui.viewmodel.TelegramParserViewModel

@Composable
fun TelegramAlertParserScreen(
    viewModel: TelegramParserViewModel,
    onNavigateToMatches: () -> Unit = {},
    onNavigateToCalculator: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    val inputText by viewModel.inputText.collectAsState()
    val parsedAlerts by viewModel.parsedAlerts.collectAsState()
    val isParsing by viewModel.isParsing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showHelpDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Telegram Értesítő & Linkelő",
                subtitle = "Mérkőzések, stratégiák és Google kereső linkek kinyerése"
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Input Box Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "Telegram Üzenet(ek) Beillesztése",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            IconButton(
                                onClick = { showHelpDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Útmutató",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Text Field
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { viewModel.setInputText(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 220.dp)
                                .testTag("telegram_input_field"),
                            placeholder = {
                                Text(
                                    text = "Illeszd be ide a Telegram értesítés(eke)t...\nPl.: [2026. 10. 04. 20:52] ⚽️ ganrax Alerts: 🔔 ⚡Second Half Action Ready\n🇮🇱 Israel Liga Bet South\nBnei Yehud vs Maccabi Amishav Petah Tikva...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Paste from Clipboard Button
                            OutlinedButton(
                                onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        viewModel.setInputText(clipText)
                                        viewModel.parse()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Beillesztés", fontSize = 12.sp)
                            }

                            // Load Sample Button
                            OutlinedButton(
                                onClick = { viewModel.loadSampleData(autoParse = true) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Minta", fontSize = 12.sp)
                            }

                            // Clear Button
                            if (inputText.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.clearAll() },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Törlés",
                                        tint = StatusLost
                                    )
                                }
                            }
                        }

                        // Parse Main Button
                        Button(
                            onClick = { viewModel.parse() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("parse_telegram_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isParsing) "Feldolgozás..." else "Csapatok & Google Linkek Kinyerése",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        // Status message if any
                        statusMessage?.let { msg ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldPrimary.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    color = EmeraldPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Results Section Header & Quick Copy
            if (parsedAlerts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Kinyert Mérkőzések (${parsedAlerts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldPrimary
                            ) {
                                Text(
                                    text = "${parsedAlerts.size} db",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.copyFormattedListToClipboard(context) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("copy_all_matches_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Lista Másolása",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Match List Cards
                itemsIndexed(parsedAlerts) { index, alert ->
                    MatchAlertCard(
                        index = index + 1,
                        alert = alert,
                        onOpenGoogle = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.googleSearchUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                uriHandler.openUri(alert.googleSearchUrl)
                            }
                        },
                        onOpenFlashscore = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.flashscoreSearchUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                uriHandler.openUri(alert.flashscoreSearchUrl)
                            }
                        },
                        onCopyLink = {
                            viewModel.copySingleUrl(context, alert.googleSearchUrl, "Google Keresés Link")
                        },
                        onAddToTracker = {
                            viewModel.addMatchToTracker(alert, context)
                        }
                    )
                }
            } else {
                // Empty state card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsSoccer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Nincs még beillesztett értesítés",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Másolj ki egy vagy több Telegram üzenetet a ganrax Alerts csatornádból, és illeszd be a fenti mezőbe.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.loadSampleData(autoParse = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Teszt Értesítések Betöltése",
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "Hogyan működik a Telegram Linkelő?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. **Telegram Üzenetek Másolása:** Másold ki a ganrax Alerts csatornából érkező bármilyen üzenetet (akár egyszerre több mérkőzést is!).")
                    Text("2. **Beillesztés & Kinyerés:** Kattints a 'Beillesztés' vagy a 'Kinyerés' gombra.")
                    Text("3. **Automatikus Adatkinyerés:** A rendszer automatikusan azonosítja:")
                    Text("   • A Stratégia nevét (pl. ⚡ Second Half Action Ready)")
                    Text("   • A Bajnokságot és Országot (pl. 🇮🇱 Israel Liga Bet South)")
                    Text("   • A Résztvevő Csapatokat (Hazai vs Vendég)")
                    Text("   • Közvetlen Google és FlashScore Kereső Linket generál hozzájuk!")
                    Text("4. **Közvetlen Használat:** A kártyákon lévő gombokkal azonnal megnyithatod a Google keresést a meccsre, vagy 1-kattintással hozzáadhatod a Meccskövetőhöz!")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Értem", fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                }
            }
        )
    }
}

@Composable
fun MatchAlertCard(
    index: Int,
    alert: ParsedTelegramAlert,
    onOpenGoogle: () -> Unit,
    onOpenFlashscore: () -> Unit,
    onCopyLink: () -> Unit,
    onAddToTracker: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_alert_card_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Number + Strategy + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldPrimary.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "#$index",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (alert.strategyName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldOdds.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GoldOdds.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = alert.strategyName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldOdds,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (alert.timestamp.isNotBlank()) {
                    Text(
                        text = alert.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // League if present
            if (alert.league.isNotBlank()) {
                Text(
                    text = alert.league,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Teams Header (Big prominent text)
            Text(
                text = alert.matchName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            // Live Stats Bar (Timer, Score, Corners, etc.)
            if (alert.score.isNotBlank() || alert.timer.isNotBlank() || alert.corners.isNotBlank() || alert.momentum.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (alert.score.isNotBlank()) {
                            Text(
                                text = "Állás: ${alert.score}",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                fontSize = 12.sp
                            )
                        }
                        if (alert.timer.isNotBlank()) {
                            Text(
                                text = "Idő: ${alert.timer}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                        if (alert.corners.isNotBlank()) {
                            Text(
                                text = "Szöglet: ${alert.corners}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        if (alert.momentum.isNotBlank()) {
                            Text(
                                text = "Momentum: ${alert.momentum}",
                                color = GoldOdds,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Live Odds if available
            if (alert.liveOdds1X2.isNotBlank() || alert.bttsOdds.isNotBlank() || alert.overUnderOdds.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (alert.liveOdds1X2.isNotBlank()) {
                        Text(
                            text = "1X2 Élő: ${alert.liveOdds1X2}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (alert.bttsOdds.isNotBlank()) {
                        Text(
                            text = "BTTS: ${alert.bttsOdds}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldOdds
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

            // Google Search URL Preview & Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Clickable Google Search Main Button
                Button(
                    onClick = onOpenGoogle,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Keresés Megnyitása",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Secondary Action Row (Flashscore, Copy Link, Add to Tracker)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flashscore button
                    OutlinedButton(
                        onClick = onOpenFlashscore,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Text("Flashscore", fontSize = 11.sp, maxLines = 1)
                    }

                    // Copy Link button
                    OutlinedButton(
                        onClick = onCopyLink,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Link Másolása", fontSize = 11.sp, maxLines = 1)
                    }

                    // Add to Tracker button
                    Button(
                        onClick = onAddToTracker,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Meccsekhez", fontSize = 11.sp, color = EmeraldPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
