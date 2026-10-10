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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.BetMatchEntity
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.CalculatorMatchItem
import com.example.domain.model.CalculatorMode
import com.example.domain.util.MatchDisplayHelper
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

    val telegramInput by viewModel.telegramInput.collectAsStateWithLifecycle()
    val extractedMatches by viewModel.extractedMatches.collectAsStateWithLifecycle()
    val selectedMatch by viewModel.selectedMatch.collectAsStateWithLifecycle()

    val homeTeamInput by viewModel.homeTeamInput.collectAsStateWithLifecycle()
    val awayTeamInput by viewModel.awayTeamInput.collectAsStateWithLifecycle()
    val strategyNameInput by viewModel.strategyNameInput.collectAsStateWithLifecycle()

    // Active progression levels state
    val activeLevel by viewModel.activeLevel.collectAsStateWithLifecycle()
    val currentOdds by viewModel.currentOddsInput.collectAsStateWithLifecycle()
    val accumulatedLoss by viewModel.accumulatedLoss.collectAsStateWithLifecycle()
    val levelHistory by viewModel.levelHistory.collectAsStateWithLifecycle()
    val isSeriesCompleted by viewModel.isSeriesCompleted.collectAsStateWithLifecycle()
    val lastWonProfit by viewModel.lastWonProfit.collectAsStateWithLifecycle()

    // Real-time reactive calculated stake, return and profit!
    val calculatedStake by viewModel.currentCalculatedStake.collectAsStateWithLifecycle()
    val potentialReturn by viewModel.currentPotentialReturn.collectAsStateWithLifecycle()
    val netProfitIfWon by viewModel.currentNetProfit.collectAsStateWithLifecycle()

    // Saved pending matches (Persisted in Room Database!)
    val savedPendingMatches by viewModel.savedPendingMatches.collectAsStateWithLifecycle()

    val progressionResults by viewModel.progressionResults.collectAsStateWithLifecycle()
    val roundOdds by viewModel.roundOdds.collectAsStateWithLifecycle()

    var showLadderDetails by remember { mutableStateOf(false) }
    var matchToIdentify by remember { mutableStateOf<BetMatchEntity?>(null) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Tétkalkulátor",
                subtitle = "Telegram meccsek & kézi odds szintlépcső",
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
                                textAlign = TextAlign.Center,
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
                                text = "Kármentes (Nullázó)",
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center,
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
                            text = "✓ Tőkearányos tét növekedés: ha a tőke nő, az alaptét automatikusan emelkedik.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Telegram Alert Input Card (Extracts ONLY Strategy Name and Match / Teams)
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
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Telegram Értesítés Beillesztése",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (extractedMatches.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldPrimary
                                ) {
                                    Text(
                                        text = "${extractedMatches.size} meccs",
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
                                .heightIn(min = 70.dp, max = 130.dp),
                            placeholder = {
                                Text(
                                    text = "Illeszd be a Telegram üzenetet (csak a stratégia neve és a mérkőzés/csapatok kerülnek kinyerésre)...",
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

                        // Action Buttons: Paste, Sample, Clear
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
                                        Toast.makeText(context, "Beillesztve és kinyerve!", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "Minta betöltve!", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "Mérkőzések kinyerve!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                modifier = Modifier.weight(1.2f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kinyerés", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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

                        // Extracted Matches Selection Chips
                        if (extractedMatches.isNotEmpty()) {
                            Text(
                                text = "Kinyert mérkőzés kiválasztása a számításhoz:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(extractedMatches) { match ->
                                    val isSelected = match.id == selectedMatch?.id
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.selectMatch(match) },
                                        label = {
                                            Text(
                                                text = match.matchName,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldPrimary,
                                            selectedLabelColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MÉRKŐZÉS ÉS CSAPATOK AZONOSÍTÁSA (EXPLICIT IDENTIFICATION)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("match_teams_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.SportsSoccer, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "Mérkőzés és Csapatok Azonosítása",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "A mentett mérkőzésnél megjelenő csapatnevek és stratégia",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Home Team and Away Team Inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = homeTeamInput,
                                onValueChange = { viewModel.setHomeTeam(it) },
                                label = { Text("Hazai Csapat") },
                                placeholder = { Text("pl. Real Madrid") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("input_home_team")
                            )

                            OutlinedTextField(
                                value = awayTeamInput,
                                onValueChange = { viewModel.setAwayTeam(it) },
                                label = { Text("Vendég Csapat") },
                                placeholder = { Text("pl. Barcelona") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("input_away_team")
                            )
                        }

                        // Strategy Name Input
                        OutlinedTextField(
                            value = strategyNameInput,
                            onValueChange = { viewModel.setStrategyName(it) },
                            label = { Text("Stratégia Neve") },
                            placeholder = { Text("pl. Both Teams to Score / ⚡Second Half Action") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_strategy_name")
                        )

                        // Visual Identity Banner
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldOdds.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(imageVector = Icons.Default.SportsSoccer, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "${homeTeamInput.ifBlank { "Hazai csapat" }} vs ${awayTeamInput.ifBlank { "Vendég csapat" }}",
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoldOdds.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = strategyNameInput.ifBlank { "Stratégia" },
                                        color = GoldOdds,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CORE PROGRESSION CONTROLLER (SZINTEK ÉS KÖRÖK SZÁMÍTÓJA)
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (isSeriesCompleted) StatusWon else EmeraldPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Section Header: Active Series Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSeriesCompleted) StatusWon else EmeraldPrimary,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Text(
                                        text = if (isSeriesCompleted) "NYERT" else "$activeLevel. SZINT",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Text(
                                    text = if (isSeriesCompleted) "Fogadási Kör Befejezve!" else "Aktuális Fogadás Számítása",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (accumulatedLoss > 0 && !isSeriesCompleted) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = StatusLost.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Veszteség: -${BettingMathEngine.formatCurrency(accumulatedLoss)}",
                                        color = StatusLost,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Match Details Header (Strategy + Teams)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = GoldOdds.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = strategyNameInput.ifBlank { selectedMatch?.strategyName ?: "Stratégia" },
                                            color = GoldOdds,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    selectedMatch?.googleSearchUrl?.takeIf { it.isNotBlank() }?.let { url ->
                                        TextButton(
                                            onClick = {
                                                try {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    uriHandler.openUri(url)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Google Keresés", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Text(
                                    text = if (homeTeamInput.isNotBlank() && awayTeamInput.isNotBlank())
                                        "$homeTeamInput vs $awayTeamInput"
                                    else (selectedMatch?.matchName ?: "Kiválasztott Mérkőzés"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                selectedMatch?.league?.takeIf { it.isNotBlank() }?.let { league ->
                                    Text(
                                        text = league,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Completed Series Banner (WHEN WON)
                        if (isSeriesCompleted) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = StatusWon.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusWon.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusWon, modifier = Modifier.size(40.dp))
                                    Text(
                                        text = "A fogadás NYERT a(z) ${levelHistory.lastOrNull()?.levelNumber ?: 1}. szinten!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusWon,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "+${BettingMathEngine.formatCurrency(lastWonProfit)} tiszta nyereség jóváírva a tőkédben!\nNem kell tovább számolni, a kör sikeresen lezárult.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            viewModel.startNewSeries()
                                            Toast.makeText(context, "Új kör indítva az 1. szintről!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Új Kör Indítása (1. Szintről)", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // Active Level Calculator & Hand-entered Odds
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Previous levels history in this series (if any lost levels)
                                if (levelHistory.isNotEmpty()) {
                                    Text(
                                        text = "Széria előzménye ebben a körben:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    levelHistory.forEach { level ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = StatusLost.copy(alpha = 0.1f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${level.levelNumber}. Szint (Odds: ${level.odds})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "-${BettingMathEngine.formatCurrency(level.stake)} (Vesztett)",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StatusLost
                                                )
                                            }
                                        }
                                    }
                                }

                                // Odds Input Row (Hand-entered)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "$activeLevel. Szint Oddsa (kézzel megadott):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Írd be a szorzót (azonnal újraszámolja a tétet)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    OutlinedTextField(
                                        value = currentOdds,
                                        onValueChange = { viewModel.setOddsInput(it) },
                                        modifier = Modifier.width(115.dp),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = GoldOdds,
                                            textAlign = TextAlign.Center
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

                                // Quick Odds Buttons
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val quickOdds = listOf(1.33, 1.40, 1.50, 1.60, 1.72, 1.85, 2.00)
                                    items(quickOdds) { oddsVal ->
                                        val strVal = String.format(java.util.Locale.US, "%.2f", oddsVal)
                                        val isSelected = currentOdds == strVal
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) GoldOdds else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.clickable { viewModel.applyPresetOdd(oddsVal) }
                                        ) {
                                            Text(
                                                text = strVal,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // DYNAMIC CALCULATED STAKES DISPLAY BOX (VALÓS IDŐBEN FRISSÜL!)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldPrimary.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "SZÜKSÉGES TÉT ENNÉL AZ ODSS-NÁL:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = BettingMathEngine.formatCurrency(calculatedStake),
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = EmeraldPrimary
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Várható kifizetés:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = BettingMathEngine.formatCurrency(potentialReturn),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoldOdds
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = EmeraldPrimary.copy(alpha = 0.25f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (activeLevel == 1) "1. szint tiszta nyeresége:" else "Összes korábbi veszteség megtérülve + nyereség:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "+${BettingMathEngine.formatCurrency(netProfitIfWon)} tiszta profit",
                                                fontWeight = FontWeight.Bold,
                                                color = StatusWon,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                // MENTÉS KÉSŐBBRE GOMB (PERSISTENCE)
                                OutlinedButton(
                                    onClick = {
                                        viewModel.saveCurrentMatchForLater { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = EmeraldPrimary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.7f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("💾 Mérkőzés Mentése Későbbre (Odds: $currentOdds | Tét: ${calculatedStake.toInt()} Ft)", fontWeight = FontWeight.Bold)
                                }

                                // LEVEL OUTCOME ACTION BUTTONS (NYERT vs VESZTETT)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.recordLevelResult(won = true) { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusWon,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🟢 NYERT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.recordLevelResult(won = false) { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusLost,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(imageVector = Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🔴 VESZTETT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // KÉSŐBBRE ELMENTETT MECCSEK SZEKCIÓ (PERSISTED SAVED MATCHES)
            if (savedPendingMatches.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldOdds.copy(alpha = 0.4f)),
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
                                    Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = GoldOdds, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Későbbre Elmentett Meccsek (${savedPendingMatches.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            savedPendingMatches.forEach { savedMatch ->
                                val resolved = MatchDisplayHelper.resolve(savedMatch)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldOdds.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth().testTag("saved_match_${savedMatch.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 1. Teams Name Line - PROMINENT, BOLD & SOCCER ICON
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SportsSoccer,
                                                    contentDescription = null,
                                                    tint = EmeraldPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = resolved.fullMatchTitle,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = GoldOdds.copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldOdds.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "${savedMatch.roundNumber}. Szint",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = GoldOdds,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Warning / Quick Identify banner if team names are generic
                                        if (resolved.isGenericTeams) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = StatusLost.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusLost.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth().clickable { matchToIdentify = savedMatch }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = StatusLost, modifier = Modifier.size(14.dp))
                                                    Text(
                                                        text = "⚠️ Nincs azonosítva a két csapat! Kattints ide a nevek megadásához",
                                                        color = StatusLost,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // 2. Strategy Badge & League
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = EmeraldPrimary.copy(alpha = 0.15f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(12.dp))
                                                    Text(
                                                        text = "Stratégia: ${resolved.strategyName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = EmeraldPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            if (savedMatch.league.isNotBlank()) {
                                                Text(
                                                    text = "• ${savedMatch.league}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // 3. Odds & Stake Info
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Odds: @${String.format(java.util.Locale.US, "%.2f", savedMatch.odds)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GoldOdds,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Tét: ${BettingMathEngine.formatCurrency(savedMatch.stake)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = EmeraldPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Várható: ${BettingMathEngine.formatCurrency(savedMatch.stake * savedMatch.odds)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Action buttons on saved match: Load, Win, Lose, Edit, Delete
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.loadSavedMatchIntoCalculator(savedMatch)
                                                    Toast.makeText(context, "${resolved.fullMatchTitle} betöltve a kalkulátorba!", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.3f),
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Betöltés", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.settleSavedMatch(savedMatch, won = true) { msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusWon, contentColor = Color.Black),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                Text("Nyert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.settleSavedMatch(savedMatch, won = false) { msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = StatusLost, contentColor = Color.White),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                Text("Vesztett", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            IconButton(
                                                onClick = { matchToIdentify = savedMatch },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Azonosítás", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteSavedMatch(savedMatch.id) {
                                                        Toast.makeText(context, "Meccs törölve!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Törlés", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Standard 4-Round Ladder & Reference
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
                                    text = "Teljes 4-Körös Tétlépcső Referencia",
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

    if (matchToIdentify != null) {
        IdentifySavedMatchDialog(
            match = matchToIdentify!!,
            onDismiss = { matchToIdentify = null },
            onSave = { home, away, strategy ->
                viewModel.updateSavedMatchDetails(matchToIdentify!!.id, home, away, strategy) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                matchToIdentify = null
            }
        )
    }
}

@Composable
fun IdentifySavedMatchDialog(
    match: BetMatchEntity,
    onDismiss: () -> Unit,
    onSave: (home: String, away: String, strategy: String) -> Unit
) {
    val resolved = MatchDisplayHelper.resolve(match)
    var homeTeam by remember { mutableStateOf(if (match.homeTeam.isNotBlank() && !match.homeTeam.startsWith("Hazai")) match.homeTeam else resolved.homeTeam) }
    var awayTeam by remember { mutableStateOf(if (match.awayTeam.isNotBlank() && !match.awayTeam.startsWith("Vendég")) match.awayTeam else resolved.awayTeam) }
    var strategy by remember { mutableStateOf(if (match.tip.isNotBlank()) match.tip else resolved.strategyName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.SportsSoccer, contentDescription = null, tint = EmeraldPrimary)
                Text("Csapatok és Stratégia Azonosítása", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Add meg a mérkőzés pontos csapatneveit, hogy a mentett meccsek között egyértelműen beazonosítható legyen:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = homeTeam,
                    onValueChange = { homeTeam = it },
                    label = { Text("Hazai Csapat") },
                    placeholder = { Text("pl. Arsenal / Real Madrid") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("identify_home_input")
                )

                OutlinedTextField(
                    value = awayTeam,
                    onValueChange = { awayTeam = it },
                    label = { Text("Vendég Csapat") },
                    placeholder = { Text("pl. Chelsea / Barcelona") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("identify_away_input")
                )

                OutlinedTextField(
                    value = strategy,
                    onValueChange = { strategy = it },
                    label = { Text("Stratégia Neve") },
                    placeholder = { Text("pl. Both Teams to Score / ⚡Second Half Action") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("identify_strategy_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(homeTeam.trim(), awayTeam.trim(), strategy.trim())
                },
                enabled = homeTeam.isNotBlank() && awayTeam.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Mentés", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Mégse")
            }
        }
    )
}
