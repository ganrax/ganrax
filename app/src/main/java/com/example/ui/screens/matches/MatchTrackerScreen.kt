package com.example.ui.screens.matches

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.example.data.local.entity.BetMatchEntity
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.MatchStatus
import com.example.domain.util.MatchDisplayHelper
import com.example.network.gemini.MatchAiAnalysis
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MatchTrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchTrackerScreen(
    viewModel: MatchTrackerViewModel,
    onNavigateToCalculator: () -> Unit
) {
    val context = LocalContext.current
    val matches by viewModel.allMatches.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val analyzingMatch by viewModel.analyzingMatch.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiAnalysisResult.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()

    var showAddMatchDialog by remember { mutableStateOf(false) }
    var matchToEditScore by remember { mutableStateOf<BetMatchEntity?>(null) }
    var matchToEditDetails by remember { mutableStateOf<BetMatchEntity?>(null) }

    Scaffold(
        topBar = {
            AppHeader(
                title = "Mérkőzéskövető",
                subtitle = "Aktuális és lezárt fogadások",
                currentBank = stats?.currentBank
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddMatchDialog = true },
                containerColor = EmeraldPrimary,
                contentColor = Color.Black,
                modifier = Modifier.testTag("add_match_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Új mérkőzés hozzáadása")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats Summary Bar
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Fogadási Statisztikák",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                title = "Nyerési Arány",
                                value = String.format(java.util.Locale.US, "%.1f%%", stats?.winRate ?: 0.0),
                                subtitle = "${stats?.wonMatches ?: 0} Nyert / ${stats?.lostMatches ?: 0} Vesztes",
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                iconColor = EmeraldPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                title = "Realizált Profit",
                                value = BettingMathEngine.formatCurrency(stats?.totalProfitRealized ?: 0.0),
                                subtitle = "ROI: ${String.format(java.util.Locale.US, "%.1f%%", stats?.roi ?: 0.0)}",
                                icon = Icons.Default.MonetizationOn,
                                iconColor = if ((stats?.totalProfitRealized ?: 0.0) >= 0) EmeraldPrimary else StatusLost,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Search Bar & Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Keresés csapat, bajnokság vagy sport szerint...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Törlés")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("match_search_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = statusFilter == "ALL",
                            onClick = { viewModel.setFilter("ALL") },
                            label = { Text("Összes") }
                        )
                        FilterChip(
                            selected = statusFilter == "PENDING",
                            onClick = { viewModel.setFilter("PENDING") },
                            label = { Text("Függőben") }
                        )
                        FilterChip(
                            selected = statusFilter == "LIVE",
                            onClick = { viewModel.setFilter("LIVE") },
                            label = { Text("Élő") }
                        )
                        FilterChip(
                            selected = statusFilter == "WON",
                            onClick = { viewModel.setFilter("WON") },
                            label = { Text("Nyertes") }
                        )
                        FilterChip(
                            selected = statusFilter == "LOST",
                            onClick = { viewModel.setFilter("LOST") },
                            label = { Text("Vesztes") }
                        )
                    }
                }
            }

            // Empty State
            if (matches.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsSoccer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nincs még rögzített mérkőzés",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nyomj a jobb alsó + gombra új mérkőzés hozzáadásához, vagy ments át tétet a kalkulátorból!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Matches List
            items(matches, key = { it.id }) { match ->
                MatchItemCard(
                    match = match,
                    onSettle = { status -> viewModel.settleMatch(match.id, status) },
                    onEditScore = { matchToEditScore = match },
                    onEditDetails = { matchToEditDetails = match },
                    onDelete = { viewModel.deleteMatch(match.id) },
                    onAnalyzeAi = {
                        val bank = stats?.currentBank ?: 10000.0
                        viewModel.analyzeWithAi(match, bank)
                    }
                )
            }
        }
    }

    // Add Match Dialog
    if (showAddMatchDialog) {
        AddMatchDialog(
            currentBank = stats?.currentBank ?: 10000.0,
            onDismiss = { showAddMatchDialog = false },
            onAdd = { match ->
                viewModel.addMatch(match)
                showAddMatchDialog = false
                Toast.makeText(context, "Mérkőzés sikeresen hozzáadva!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Match Details Dialog (Csapatok és Stratégia szerkesztése)
    if (matchToEditDetails != null) {
        EditMatchDetailsDialog(
            match = matchToEditDetails!!,
            onDismiss = { matchToEditDetails = null },
            onSave = { updatedMatch ->
                viewModel.updateMatch(updatedMatch)
                matchToEditDetails = null
                Toast.makeText(context, "${updatedMatch.homeTeam} vs ${updatedMatch.awayTeam} adatai mentve!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Score Dialog
    if (matchToEditScore != null) {
        EditScoreDialog(
            match = matchToEditScore!!,
            onDismiss = { matchToEditScore = null },
            onSave = { homeScore, awayScore, minute, status ->
                viewModel.settleMatch(matchToEditScore!!.id, status, homeScore, awayScore)
                matchToEditScore = null
            }
        )
    }

    // AI Analysis Dialog
    if (analyzingMatch != null) {
        AiAnalysisDialog(
            match = analyzingMatch!!,
            analysis = aiResult,
            isLoading = isAnalyzing,
            onDismiss = { viewModel.closeAnalysisDialog() }
        )
    }
}

@Composable
fun MatchItemCard(
    match: BetMatchEntity,
    onSettle: (MatchStatus) -> Unit,
    onEditScore: () -> Unit,
    onEditDetails: () -> Unit,
    onDelete: () -> Unit,
    onAnalyzeAi: () -> Unit
) {
    val statusEnum = MatchStatus.fromString(match.status)
    val resolved = MatchDisplayHelper.resolve(match)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().testTag("match_card_${match.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: League, Sport & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = match.sport,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "• ${match.league}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MatchStatusBadge(status = statusEnum)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Teams & Score - PROMINENT DISPLAY WITH TEAM NAMES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = resolved.fullMatchTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (resolved.isGenericTeams) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = StatusLost.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusLost.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable(onClick = onEditDetails)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = StatusLost, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "⚠️ Csapatok megadása / Azonosítás",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusLost
                                )
                            }
                        }
                    }

                    if (resolved.strategyName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldOdds.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldOdds.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "Stratégia: ${resolved.strategyName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = GoldOdds,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (match.homeScore != null && match.awayScore != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable(onClick = onEditScore)
                    ) {
                        Text(
                            text = "${match.homeScore} - ${match.awayScore}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (statusEnum == MatchStatus.LIVE) StatusLive else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Market, Tip, Odds, Stake & Round
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Tipp: ${match.tip}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${match.roundNumber}. kör • ${match.market}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldOdds.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldOdds.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "@${match.odds}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = GoldOdds
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Tét:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = BettingMathEngine.formatCurrency(match.stake),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Settle Won, Settle Lost, Score, AI Analysis, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onAnalyzeAi,
                        modifier = Modifier.size(36.dp).testTag("ai_analyze_button_${match.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Elemzés",
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onEditDetails,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Meccs és csapatok szerkesztése",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onEditScore,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsScore,
                            contentDescription = "Eredmény beállítása",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Törlés",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { onSettle(MatchStatus.WON) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (statusEnum == MatchStatus.WON) EmeraldPrimary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (statusEnum == MatchStatus.WON) Color.Black else StatusWon
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("settle_won_${match.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nyert", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = { onSettle(MatchStatus.LOST) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (statusEnum == MatchStatus.LOST) StatusLost else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (statusEnum == MatchStatus.LOST) Color.White else StatusLost
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("settle_lost_${match.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vesztes", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AddMatchDialog(
    currentBank: Double,
    onDismiss: () -> Unit,
    onAdd: (BetMatchEntity) -> Unit
) {
    var homeTeam by remember { mutableStateOf("") }
    var awayTeam by remember { mutableStateOf("") }
    var sport by remember { mutableStateOf("Labdarúgás") }
    var league by remember { mutableStateOf("Bajnokok Ligája") }
    var market by remember { mutableStateOf("1X2") }
    var tip by remember { mutableStateOf("Hazai (1)") }
    var oddsStr by remember { mutableStateOf("1.50") }
    var roundNum by remember { mutableStateOf(1) }
    var isBreakEvenMode by remember { mutableStateOf(false) }

    // Automatic base stake based directly on current capital (Bank / 49.25)
    val baseStake = remember(currentBank) {
        (currentBank / 49.25).toInt().toDouble().coerceAtLeast(1.0)
    }

    // Automatic stake calculation based on current capital, round, and odds
    val computedStake = remember(currentBank, oddsStr, roundNum, isBreakEvenMode) {
        val odds = (oddsStr.toDoubleOrNull() ?: 1.50).coerceAtLeast(1.02)
        val divisor = odds - 1.0
        val targetProfit = if (isBreakEvenMode) 0.0 else baseStake

        when (roundNum) {
            1 -> baseStake
            2 -> {
                val s1 = baseStake
                ((targetProfit + s1) / divisor).toInt().toDouble()
            }
            3 -> {
                val s1 = baseStake
                val s2 = ((targetProfit + s1) / divisor).toInt().toDouble()
                ((targetProfit + s1 + s2) / divisor).toInt().toDouble()
            }
            4 -> {
                val s1 = baseStake
                val s2 = ((targetProfit + s1) / divisor).toInt().toDouble()
                val s3 = ((targetProfit + s1 + s2) / divisor).toInt().toDouble()
                ((targetProfit + s1 + s2 + s3) / divisor).toInt().toDouble()
            }
            else -> baseStake
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Új Fogadás Hozzáadása", fontWeight = FontWeight.Bold)
                Text(
                    text = "A tétet a tőke (${BettingMathEngine.formatCurrency(currentBank)}) és az odds alapján automatikusan számoljuk",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Capital & Auto-Stake Summary Card
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
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
                                        text = "Kiszámított Tét ($roundNum. kör):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = BettingMathEngine.formatCurrency(computedStake),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmeraldPrimary
                                    )
                                }

                                RiskGaugeBadge(percentageOfBank = (computedStake / currentBank) * 100.0)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tőke: ${BettingMathEngine.formatCurrency(currentBank)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Alaptét (Tőke/49.25): ${BettingMathEngine.formatCurrency(baseStake)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyanAccent
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = homeTeam,
                        onValueChange = { homeTeam = it },
                        label = { Text("Hazai Csapat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_dialog_home")
                    )
                }

                item {
                    OutlinedTextField(
                        value = awayTeam,
                        onValueChange = { awayTeam = it },
                        label = { Text("Vendég Csapat") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_dialog_away")
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sport,
                            onValueChange = { sport = it },
                            label = { Text("Sportág") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = league,
                            onValueChange = { league = it },
                            label = { Text("Bajnokság") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = market,
                            onValueChange = { market = it },
                            label = { Text("Piac") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tip,
                            onValueChange = { tip = it },
                            label = { Text("Tipp") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Text(text = "Stratégiai Kör Száma:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..4).forEach { r ->
                            FilterChip(
                                selected = roundNum == r,
                                onClick = { roundNum = r },
                                label = { Text("$r. Kör") }
                            )
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = oddsStr,
                            onValueChange = { oddsStr = it },
                            label = { Text("Odds (Szorzó) - Írd be vagy válassz:") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("add_dialog_odds"),
                            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = GoldOdds)
                        )

                        // Quick Odds Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1.33, 1.40, 1.50, 1.65, 1.80).forEach { quickOdd ->
                                SuggestionChip(
                                    onClick = { oddsStr = quickOdd.toString() },
                                    label = { Text("$quickOdd", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBreakEvenMode) "Mód: Kármentés (Nullázó)" else "Mód: 1 Alaptétnyi profit",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBreakEvenMode) GoldOdds else EmeraldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { isBreakEvenMode = !isBreakEvenMode }) {
                            Text(if (isBreakEvenMode) "Váltás: Profittermelés" else "Váltás: Kármentés", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val odds = oddsStr.toDoubleOrNull() ?: 1.50
                    if (homeTeam.isNotBlank() && awayTeam.isNotBlank()) {
                        val match = BetMatchEntity(
                            roundNumber = roundNum,
                            sport = sport,
                            league = league,
                            homeTeam = homeTeam,
                            awayTeam = awayTeam,
                            market = market,
                            tip = tip,
                            odds = odds,
                            stake = computedStake,
                            status = "PENDING"
                        )
                        onAdd(match)
                    }
                },
                enabled = homeTeam.isNotBlank() && awayTeam.isNotBlank()
            ) {
                Text("Fogadás Mentése (${BettingMathEngine.formatCurrency(computedStake)})")
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
fun EditMatchDetailsDialog(
    match: BetMatchEntity,
    onDismiss: () -> Unit,
    onSave: (BetMatchEntity) -> Unit
) {
    val resolved = MatchDisplayHelper.resolve(match)
    var homeTeam by remember { mutableStateOf(if (match.homeTeam.isNotBlank() && !match.homeTeam.startsWith("Hazai")) match.homeTeam else resolved.homeTeam) }
    var awayTeam by remember { mutableStateOf(if (match.awayTeam.isNotBlank() && !match.awayTeam.startsWith("Vendég")) match.awayTeam else resolved.awayTeam) }
    var tip by remember { mutableStateOf(if (match.tip.isNotBlank()) match.tip else resolved.strategyName) }
    var league by remember { mutableStateOf(match.league) }
    var oddsStr by remember { mutableStateOf(match.odds.toString()) }
    var stakeStr by remember { mutableStateOf(match.stake.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = EmeraldPrimary)
                Text("Mérkőzés Adatainak Módosítása", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = homeTeam,
                    onValueChange = { homeTeam = it },
                    label = { Text("Hazai Csapat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = awayTeam,
                    onValueChange = { awayTeam = it },
                    label = { Text("Vendég Csapat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tip,
                    onValueChange = { tip = it },
                    label = { Text("Stratégia / Tipp") },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = oddsStr,
                        onValueChange = { oddsStr = it },
                        label = { Text("Odds") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = stakeStr,
                        onValueChange = { stakeStr = it },
                        label = { Text("Tét (Ft)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val odds = oddsStr.replace(",", ".").toDoubleOrNull() ?: match.odds
                    val stake = stakeStr.toDoubleOrNull() ?: match.stake
                    onSave(
                        match.copy(
                            homeTeam = homeTeam.trim(),
                            awayTeam = awayTeam.trim(),
                            tip = tip.trim(),
                            league = league.trim(),
                            odds = odds,
                            stake = stake
                        )
                    )
                },
                enabled = homeTeam.isNotBlank() && awayTeam.isNotBlank()
            ) {
                Text("Mentés")
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
fun EditScoreDialog(
    match: BetMatchEntity,
    onDismiss: () -> Unit,
    onSave: (homeScore: Int?, awayScore: Int?, minute: String?, status: MatchStatus) -> Unit
) {
    var homeScoreStr by remember { mutableStateOf(match.homeScore?.toString() ?: "0") }
    var awayScoreStr by remember { mutableStateOf(match.awayScore?.toString() ?: "0") }
    var isLive by remember { mutableStateOf(match.status == "LIVE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eredmény Módosítása") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${match.homeTeam} vs ${match.awayTeam}", fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = homeScoreStr,
                        onValueChange = { homeScoreStr = it },
                        label = { Text("Hazai gól") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = awayScoreStr,
                        onValueChange = { awayScoreStr = it },
                        label = { Text("Vendég gól") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = isLive, onCheckedChange = { isLive = it })
                    Text("Mérkőzés jelenleg élőben tart (Live)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hs = homeScoreStr.toIntOrNull()
                    val ascore = awayScoreStr.toIntOrNull()
                    val newStatus = if (isLive) MatchStatus.LIVE else MatchStatus.fromString(match.status)
                    onSave(hs, ascore, null, newStatus)
                }
            ) {
                Text("Mentés")
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
fun AiAnalysisDialog(
    match: BetMatchEntity,
    analysis: MatchAiAnalysis?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = CyanAccent)
                Text("Gemini AI Meccselemzés", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = CyanAccent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Mérkőzés és tétstratégia elemzése folyamatban...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (analysis != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${match.homeTeam} vs ${match.awayTeam}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tipp: ${match.tip} @${match.odds} (${match.roundNumber}. körös tét)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GoldOdds
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = analysis.summary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Stratégiai Javaslat:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = analysis.recommendation,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Becsült esély: ${analysis.winProbabilityEstimate.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldPrimary
                            )
                            Text(
                                text = "Kockázati szint: ${analysis.riskScore}/10",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (analysis.riskScore > 5) StatusLost else StatusWon
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Kulcstényezők:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        analysis.keyFactors.forEach { factor ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("•", color = CyanAccent)
                                Text(factor, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                Text("Nem sikerült lekérni az elemzést.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Rendben")
            }
        }
    )
}
