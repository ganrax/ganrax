package com.example.ui.screens.calculator

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.CalculatorMode
import com.example.domain.model.PresetOddsRow
import com.example.domain.model.RoundStakeResult
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.CalculatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StakeCalculatorScreen(
    viewModel: CalculatorViewModel,
    onMatchCreated: () -> Unit
) {
    val context = LocalContext.current
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val bankrollStr by viewModel.bankrollInput.collectAsStateWithLifecycle()
    val baseStakeStr by viewModel.baseStakeInput.collectAsStateWithLifecycle()
    val targetProfitStr by viewModel.targetProfitInput.collectAsStateWithLifecycle()
    val roundOdds by viewModel.roundOdds.collectAsStateWithLifecycle()
    val progressionResults by viewModel.progressionResults.collectAsStateWithLifecycle()
    val presetTable by viewModel.presetOddsTable.collectAsStateWithLifecycle()

    var showPresetDialog by remember { mutableStateOf(false) }
    var saveMatchRound by remember { mutableStateOf<RoundStakeResult?>(null) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Tétkalkulátor",
                subtitle = mode.subtitle,
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
                            color = if (mode == CalculatorMode.BREAK_EVEN) GoldOdds else Color.Transparent,
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

            // Inputs Row (Bankroll & Auto-Calculated Base Stake)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Aktuális Tőke (Bankroll):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val bankVal = bankrollStr.toDoubleOrNull() ?: 10000.0
                                        Text(
                                            text = BettingMathEngine.formatCurrency(bankVal),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Alaptét (Tőke / 49.25):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val baseVal = baseStakeStr.toDoubleOrNull() ?: 200.0
                                        Text(
                                            text = BettingMathEngine.formatCurrency(baseVal),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = CyanAccent
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "✓ A tétösszegek a tőke növekedésével automatikusan növekednek az Excel képletek szerint.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Quick Preset Odds Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Gyors Odds Sablonok:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            TextButton(
                                onClick = { showPresetDialog = true },
                                modifier = Modifier.testTag("open_preset_table_button")
                            ) {
                                Icon(imageVector = Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Teljes Excel Tábla", fontSize = 12.sp)
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val quickPresets = listOf(1.33, 1.40, 1.50, 1.60, 1.72, 1.80, 1.90, 2.00)
                            items(quickPresets) { odd ->
                                SuggestionChip(
                                    onClick = { viewModel.applyPresetOdd(odd) },
                                    label = { Text("$odd", fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Round Controls Header (+ / - Rounds)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Többkörös Tétlépcső (${progressionResults.size} Kör)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { viewModel.removeRound() },
                            enabled = roundOdds.size > 2,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.RemoveCircleOutline, contentDescription = "Kör törlése")
                        }
                        IconButton(
                            onClick = { viewModel.addRound() },
                            enabled = roundOdds.size < 6,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AddCircleOutline, contentDescription = "Kör hozzáadása")
                        }
                    }
                }
            }

            // Progression Cards for Each Round
            itemsIndexed(progressionResults) { index, result ->
                RoundCard(
                    result = result,
                    currentOddStr = roundOdds.getOrElse(index) { "1.50" },
                    onOddChange = { viewModel.updateOdd(index, it) },
                    onSaveMatch = { saveMatchRound = result }
                )
            }
        }
    }

    // Save To Match Tracker Dialog
    saveMatchRound?.let { round ->
        SaveRoundToMatchDialog(
            round = round,
            onDismiss = { saveMatchRound = null },
            onSave = { home, away, sport, league, tip ->
                viewModel.saveStakeAsMatch(
                    round = round.round,
                    odds = round.odds,
                    stake = round.stake,
                    homeTeam = home,
                    awayTeam = away,
                    sport = sport,
                    league = league,
                    tip = tip,
                    onSaved = {
                        saveMatchRound = null
                        Toast.makeText(context, "Mérkőzés elmentve a követőbe!", Toast.LENGTH_SHORT).show()
                        onMatchCreated()
                    }
                )
            }
        )
    }

    // Preset Excel Reference Dialog
    if (showPresetDialog) {
        PresetOddsTableDialog(
            presetTable = presetTable,
            baseStake = baseStakeStr.toDoubleOrNull() ?: 200.0,
            mode = mode,
            onSelectOdd = { odd ->
                viewModel.applyPresetOdd(odd)
                showPresetDialog = false
            },
            onDismiss = { showPresetDialog = false }
        )
    }
}

@Composable
fun RoundCard(
    result: RoundStakeResult,
    currentOddStr: String,
    onOddChange: (String) -> Unit,
    onSaveMatch: () -> Unit
) {
    val roundColor = when (result.round) {
        1 -> EmeraldPrimary
        2 -> CyanAccent
        3 -> GoldOdds
        else -> StatusLost
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, roundColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().testTag("round_card_${result.round}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(roundColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${result.round}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = roundColor
                        )
                    }

                    Column {
                        Text(
                            text = "${result.round}. Kör Tétje",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Szükséges tét a célhoz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Odds Input
                OutlinedTextField(
                    value = currentOddStr,
                    onValueChange = onOddChange,
                    label = { Text("Odds") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(90.dp).testTag("odds_input_round_${result.round}"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = GoldOdds)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Stake Display
            Surface(
                shape = RoundedCornerShape(12.dp),
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
                    Column {
                        Text(
                            text = "Kiszámított Tét:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BettingMathEngine.formatCurrency(result.stake),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = roundColor
                        )
                    }

                    RiskGaugeBadge(percentageOfBank = result.bankPercentage)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Breakdown Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Összes Kockázat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = BettingMathEngine.formatCurrency(result.totalInvested),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Várható Kifizetés",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = BettingMathEngine.formatCurrency(result.potentialReturn),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Tiszta Nyereség",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+${BettingMathEngine.formatCurrency(result.netProfit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save to match button
            Button(
                onClick = onSaveMatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("save_round_${result.round}_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Fogadás Rögzítése Ezzel a Téttel (${BettingMathEngine.formatCurrency(result.stake)})", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SaveRoundToMatchDialog(
    round: RoundStakeResult,
    onDismiss: () -> Unit,
    onSave: (home: String, away: String, sport: String, league: String, tip: String) -> Unit
) {
    var homeTeam by remember { mutableStateOf("") }
    var awayTeam by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("Labdarúgás") }
    var league by remember { mutableStateOf("Bajnokság") }
    var tip by remember { mutableStateOf("Hazai győzelem (1)") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${round.round}. Körös Fogadás Mentése") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tét: ${BettingMathEngine.formatCurrency(round.stake)}", fontWeight = FontWeight.Bold)
                        Text("Odds: ${round.odds}", color = GoldOdds, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = homeTeam,
                    onValueChange = { homeTeam = it },
                    label = { Text("Hazai Csapat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("match_home_input")
                )

                OutlinedTextField(
                    value = awayTeam,
                    onValueChange = { awayTeam = it },
                    label = { Text("Vendég Csapat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("match_away_input")
                )

                OutlinedTextField(
                    value = tip,
                    onValueChange = { tip = it },
                    label = { Text("Tipp / Fogadási Piac") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = league,
                    onValueChange = { league = it },
                    label = { Text("Bajnokság / Liga") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (homeTeam.isNotBlank() && awayTeam.isNotBlank()) {
                        onSave(homeTeam, awayTeam, sport, league, tip)
                    }
                },
                enabled = homeTeam.isNotBlank() && awayTeam.isNotBlank()
            ) {
                Text("Mentés a Követőbe")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Mégse")
            }
        }
    )
}

@Composable
fun PresetOddsTableDialog(
    presetTable: List<PresetOddsRow>,
    baseStake: Double,
    mode: CalculatorMode,
    onSelectOdd: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Excel Tétkezelő Referenciatábla", fontWeight = FontWeight.Bold)
                Text(
                    text = "Mód: ${mode.title} (Alaptét: ${baseStake.toInt()} Ft)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Odds", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("2. Kör", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("3. Kör", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("4. Kör", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                }

                items(presetTable) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectOdd(row.odds) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${row.odds}",
                            color = GoldOdds,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${row.round2Stake.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${row.round3Stake.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${row.round4Stake.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Bezárás")
            }
        }
    )
}
