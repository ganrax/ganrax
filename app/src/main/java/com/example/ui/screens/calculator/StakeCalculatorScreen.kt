package com.example.ui.screens.calculator

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.CalculatorMatchItem
import com.example.domain.model.CalculatorMode
import com.example.domain.model.PresetOddsRow
import com.example.domain.model.RoundStakeResult
import com.example.ui.components.AppHeader
import com.example.ui.theme.*
import com.example.ui.viewmodel.CalculatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakeCalculatorScreen(
    viewModel: CalculatorViewModel,
    onMatchCreated: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val bankrollStr by viewModel.bankrollInput.collectAsStateWithLifecycle()
    val baseStakeStr by viewModel.baseStakeInput.collectAsStateWithLifecycle()
    val targetProfitStr by viewModel.targetProfitInput.collectAsStateWithLifecycle()
    val roundOdds by viewModel.roundOdds.collectAsStateWithLifecycle()
    val progressionResults by viewModel.progressionResults.collectAsStateWithLifecycle()
    val presetTable by viewModel.presetOddsTable.collectAsStateWithLifecycle()

    val telegramInput by viewModel.telegramInput.collectAsStateWithLifecycle()
    val parsedMatches by viewModel.parsedMatches.collectAsStateWithLifecycle()

    var showPresetDialog by remember { mutableStateOf(false) }
    var showLadderDetails by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Tétkalkulátor",
                subtitle = "Telegram meccsek & kézi odds alapú tétkezelés",
                currentBank = bankrollStr.toDoubleOrNull()
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
            // Mode Selector Segmented Tabs
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (mode == CalculatorMode.TARGET_PROFIT) EmeraldPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setMode(CalculatorMode.TARGET_PROFIT) }
                                .testTag("tab_target_profit")
                        ) {
                            Text(
                                text = "1 Alaptétnyi Profit",
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (mode == CalculatorMode.TARGET_PROFIT) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (mode == CalculatorMode.BREAK_EVEN) EmeraldPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setMode(CalculatorMode.BREAK_EVEN) }
                                .testTag("tab_break_even")
                        ) {
                            Text(
                                text = "Kármentés (Nullázó)",
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (mode == CalculatorMode.BREAK_EVEN) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bankroll & Base Stake Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Aktuális Tőke (Bankroll):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = BettingMathEngine.formatCurrency(bankrollStr.toDoubleOrNull() ?: 10000.0),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldPrimary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Alaptét (Tőke / 49.25):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = BettingMathEngine.formatCurrency(baseStakeStr.toDoubleOrNull() ?: 203.0),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldOdds
                                )
                            }
                        }

                        Text(
                            text = "✓ A tétösszegek a tőke növekedésével automatikusan nőnek az Excel tábla szerint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Telegram Alert Input Card (Unified on the same page!)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "Telegram Értesítés Beillesztése",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (parsedMatches.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldPrimary
                                ) {
                                    Text(
                                        text = "${parsedMatches.size} meccs",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = telegramInput,
                            onValueChange = { viewModel.setTelegramInput(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp, max = 180.dp),
                            placeholder = {
                                Text(
                                    text = "Ide illeszd be a ganrax Alerts vagy más Telegram értesítést...\nPl. ⚡Second Half Action Ready\nBnei Yehud vs Maccabi Amishav Petah Tikva...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        )

                        // Action Buttons: Paste, Sample, Calculate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        viewModel.setTelegramInput(clip)
                                        viewModel.parseTelegramText()
                                        Toast.makeText(context, "Beillesztve és feldolgozva!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "A vágólap üres!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Beillesztés", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.loadSampleTelegram(autoParse = true)
                                    Toast.makeText(context, "Minta értesítések betöltve!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Minta", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    viewModel.parseTelegramText()
                                    Toast.makeText(context, "Mérkőzések frissítve!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                modifier = Modifier.weight(1.3f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Calculate, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kinyerés & Tét", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }

                            if (telegramInput.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.clearTelegram() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Törlés", tint = StatusLost)
                                }
                            }
                        }
                    }
                }
            }

            // Section Header: Match-Based Calculations
            if (parsedMatches.isNotEmpty()) {
                item {
                    Text(
                        text = "Telegram Meccsek Tétkalkulációja (${parsedMatches.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                itemsIndexed(parsedMatches, key = { _, match -> match.id }) { index, match ->
                    MatchCalculatorCard(
                        index = index + 1,
                        match = match,
                        onOddsChange = { newOdds -> viewModel.updateMatchOdds(match.id, newOdds) },
                        onRoundChange = { newRound -> viewModel.updateMatchRound(match.id, newRound) },
                        onOpenGoogle = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(match.googleSearchUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                uriHandler.openUri(match.googleSearchUrl)
                            }
                        },
                        onSaveToTracker = {
                            viewModel.saveParsedMatchToTracker(match.id) {
                                Toast.makeText(context, "${match.matchName} rögzítve a Meccsekhez!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            // Collapsible Standard 4-Round Ladder & Excel Table
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLadderDetails = !showLadderDetails },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Általános 4-Körös Tétlépcső & Sablonok",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Icon(
                                imageVector = if (showLadderDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showLadderDetails) {
                            Text(
                                text = "Gyors Odds Sablonok:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val quickOdds = listOf(1.33, 1.40, 1.50, 1.60, 1.72, 1.85, 2.00)
                                items(quickOdds) { odds ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.applyPresetOdd(odds) },
                                        label = { Text(text = String.format(java.util.Locale.US, "%.2f", odds), fontWeight = FontWeight.Bold) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // 4 Rounds Ladder display
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                progressionResults.forEachIndexed { i, result ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${result.round}. Kör (Odds: ${roundOdds.getOrNull(i) ?: "1.50"}):",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = BettingMathEngine.formatCurrency(result.stake),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = EmeraldPrimary,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCalculatorCard(
    index: Int,
    match: CalculatorMatchItem,
    onOddsChange: (String) -> Unit,
    onRoundChange: (Int) -> Unit,
    onOpenGoogle: () -> Unit,
    onSaveToTracker: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: #Index + Strategy badge + Timer/Score
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "#$index",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (match.strategyName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldOdds.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldOdds.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = match.strategyName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldOdds,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (match.score.isNotBlank() || match.timer.isNotBlank()) {
                    Text(
                        text = "${match.score} (${match.timer})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
            }

            // League
            if (match.league.isNotBlank()) {
                Text(
                    text = match.league,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Teams (Big prominent title)
            Text(
                text = match.matchName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Round Selector Row (1. Kör, 2. Kör, 3. Kör, 4. Kör)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Válassz Kört:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (r in 1..4) {
                        val isSelected = match.selectedRound == r
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) EmeraldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onRoundChange(r) }
                        ) {
                            Text(
                                text = "$r. Kör",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Odds Input & Quick Buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kézzel megadott Odds:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = match.oddsInput,
                        onValueChange = onOddsChange,
                        modifier = Modifier.width(110.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = GoldOdds,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldOdds,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                }

                // Quick Odds Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quick = listOf("1.33", "1.40", "1.50", "1.60", "1.72", "1.85", "2.00")
                    items(quick) { qOdds ->
                        val isSelected = match.oddsInput == qOdds
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) GoldOdds else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { onOddsChange(qOdds) }
                        ) {
                            Text(
                                text = qOdds,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Calculation Results Highlight Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldPrimary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${match.selectedRound}. Kör Kiszámított Tétje:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BettingMathEngine.formatCurrency(match.calculatedStake),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Várható Kifizetés:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BettingMathEngine.formatCurrency(match.potentialReturn),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldOdds
                        )
                        Text(
                            text = "Tiszta Profit: +${BettingMathEngine.formatCurrency(match.netProfit)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusWon
                        )
                    }
                }
            }

            // Action Buttons: Google Search & Save to Matches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenGoogle,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Google Keresés", fontSize = 12.sp)
                }

                Button(
                    onClick = onSaveToTracker,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (match.isSavedToTracker) StatusWon else EmeraldPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (match.isSavedToTracker) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (match.isSavedToTracker) "Rögzítve ✓" else "Rögzítés a Meccsekhez",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
