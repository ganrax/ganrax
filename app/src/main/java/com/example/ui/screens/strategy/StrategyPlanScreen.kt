package com.example.ui.screens.strategy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.example.data.local.entity.DayPlanEntity
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.DayStatus
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.StrategyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyPlanScreen(
    viewModel: StrategyViewModel,
    onNavigateToCalculator: (bankroll: Double, baseStake: Double) -> Unit
) {
    val context = LocalContext.current
    val days by viewModel.allDays.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedText by remember { mutableStateOf("") }
    var exportFormat by remember { mutableStateOf("CSV") }

    val filteredDays = remember(days, filter) {
        when (filter) {
            "COMPLETED" -> days.filter { it.status == "COMPLETED" }
            "PENDING" -> days.filter { it.status == "PENDING" || it.status == "IN_PROGRESS" }
            "FAILED" -> days.filter { it.status == "FAILED" }
            else -> days
        }
    }

    Scaffold(
        topBar = {
            AppHeader(
                title = "100 Napos Tétkezelő Terv",
                subtitle = "10% napi kamatos kamat stratégia",
                currentBank = stats.currentBank,
                onSettingsClick = { showConfigDialog = true },
                onExportClick = {
                    viewModel.exportCsv { csv ->
                        exportedText = csv
                        exportFormat = "CSV"
                        showExportDialog = true
                    }
                }
            )
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
            // Strategy Overview Header Card
            item {
                StrategyHeaderCard(
                    stats = stats,
                    config = config,
                    onOpenCalculator = {
                        val activeBank = stats.currentBank
                        val base = config?.baseStake ?: 200.0
                        onNavigateToCalculator(activeBank, base)
                    }
                )
            }

            // Strategy Formula Info Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Képletek: Min Roll = Bank / 49.25 (4 körre) • Max Roll = Bank / 20 • Terv = +10% napi profit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter == "ALL",
                        onClick = { viewModel.setFilter("ALL") },
                        label = { Text("Összes (${days.size})") },
                        modifier = Modifier.testTag("filter_all")
                    )
                    FilterChip(
                        selected = filter == "PENDING",
                        onClick = { viewModel.setFilter("PENDING") },
                        label = { Text("Függőben (${days.count { it.status == "PENDING" || it.status == "IN_PROGRESS" }})") },
                        modifier = Modifier.testTag("filter_pending")
                    )
                    FilterChip(
                        selected = filter == "COMPLETED",
                        onClick = { viewModel.setFilter("COMPLETED") },
                        label = { Text("Nyertes (${days.count { it.status == "COMPLETED" }})") },
                        modifier = Modifier.testTag("filter_completed")
                    )
                }
            }

            // Days List
            items(filteredDays, key = { it.dayNumber }) { day ->
                DayPlanRow(
                    day = day,
                    isActive = config?.activeDay == day.dayNumber,
                    currency = config?.currency ?: "Ft",
                    onClick = { viewModel.selectDay(day) }
                )
            }
        }
    }

    // Selected Day Action BottomSheet / Dialog
    if (selectedDay != null) {
        DayDetailDialog(
            day = selectedDay!!,
            currency = config?.currency ?: "Ft",
            onDismiss = { viewModel.selectDay(null) },
            onStatusSelected = { status, balance, profit ->
                viewModel.updateDayStatus(selectedDay!!.dayNumber, status, balance, profit)
            },
            onCalculate = {
                val bank = selectedDay!!.targetBank
                val base = selectedDay!!.minRoll
                viewModel.selectDay(null)
                onNavigateToCalculator(bank, base)
            }
        )
    }

    // Config Settings Dialog
    if (showConfigDialog) {
        StrategyConfigDialog(
            currentConfig = config,
            onDismiss = { showConfigDialog = false },
            onSave = { initialBank, dailyRate, baseStake, regenerate ->
                viewModel.updateStrategyConfig(initialBank, dailyRate, baseStake, regenerate)
                showConfigDialog = false
                Toast.makeText(context, "Beállítások frissítve!", Toast.LENGTH_SHORT).show()
            },
            onReset = {
                viewModel.resetToDefault()
                showConfigDialog = false
                Toast.makeText(context, "Alapértelmezett stratégia visszaállítva!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Terv Exportálása ($exportFormat)") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Az alábbi adatok megfelelnek az eredeti Excel táblázat formátumának:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("BettingStrategy_$exportFormat", exportedText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Vágólapra másolva!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    modifier = Modifier.testTag("copy_export_button")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Másolás vágólapra")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Bezárás")
                }
            }
        )
    }
}

@Composable
fun StrategyHeaderCard(
    stats: com.example.domain.model.StrategyStats,
    config: com.example.data.local.entity.StrategyConfigEntity?,
    onOpenCalculator: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Aktuális Bankroll",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = BettingMathEngine.formatCurrency(stats.currentBank, config?.currency ?: "Ft"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = onOpenCalculator,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("open_calculator_quick_button")
                ) {
                    Icon(imageVector = Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tétkalkulátor", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar (Completed Days / 100)
            val progress = (stats.completedDays.toFloat() / 100f).coerceIn(0f, 1f)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Teljesített napok: ${stats.completedDays} / 100 nap",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EmeraldPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3 Small Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Kezdő Bank",
                    value = BettingMathEngine.formatCurrency(stats.startingBank, config?.currency ?: "Ft"),
                    icon = Icons.Default.Flag,
                    iconColor = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "100. Nap Cél",
                    value = BettingMathEngine.formatCurrency(stats.totalTargetDay100, config?.currency ?: "Ft"),
                    icon = Icons.Default.EmojiEvents,
                    iconColor = GoldOdds,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Nyerő Sorozat",
                    value = "${stats.currentStreak} nap",
                    icon = Icons.Default.LocalFireDepartment,
                    iconColor = StatusLive,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DayPlanRow(
    day: DayPlanEntity,
    isActive: Boolean,
    currency: String,
    onClick: () -> Unit
) {
    val statusEnum = DayStatus.fromString(day.status)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("day_row_${day.dayNumber}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
                            .background(if (isActive) EmeraldPrimary else MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${day.dayNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = "${day.dayNumber}. Nap Cél",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Terv Bank: ${BettingMathEngine.formatCurrency(day.targetBank, currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DayStatusBadge(status = statusEnum)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            // 3 Values: Min Roll, Max Roll, Target Profit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Min Roll (4 körre)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = BettingMathEngine.formatCurrency(day.minRoll, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Max Roll (Bank/20)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = BettingMathEngine.formatCurrency(day.maxRoll, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldOdds
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Napi Profit Cél (+10%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "+${BettingMathEngine.formatCurrency(day.targetProfit, currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary
                    )
                }
            }

            if (day.actualBalance != null || day.actualProfit != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Teljesített Egyenleg:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BettingMathEngine.formatCurrency(day.actualBalance ?: 0.0, currency),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if ((day.actualProfit ?: 0.0) >= 0) EmeraldPrimary else StatusLost
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayDetailDialog(
    day: DayPlanEntity,
    currency: String,
    onDismiss: () -> Unit,
    onStatusSelected: (DayStatus, Double?, Double?) -> Unit,
    onCalculate: () -> Unit
) {
    var customBalanceStr by remember { mutableStateOf((day.actualBalance?.toInt() ?: (day.targetBank + day.targetProfit).toInt()).toString()) }
    var showCustomInput by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${day.dayNumber}. Nap Részletei",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tervezett Bank: ${BettingMathEngine.formatCurrency(day.targetBank, currency)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Napi 10% Profit Cél: +${BettingMathEngine.formatCurrency(day.targetProfit, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Minimális alaptét (4 körre): ${BettingMathEngine.formatCurrency(day.minRoll, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Maximális tét (Bank/20): ${BettingMathEngine.formatCurrency(day.maxRoll, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onCalculate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tétkalkuláció ehhez a naphoz")
                }

                if (showCustomInput) {
                    OutlinedTextField(
                        value = customBalanceStr,
                        onValueChange = { customBalanceStr = it },
                        label = { Text("Tényleges záró egyenleg ($currency)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val bal = customBalanceStr.toDoubleOrNull() ?: (day.targetBank + day.targetProfit)
                            val profit = bal - day.targetBank
                            onStatusSelected(DayStatus.COMPLETED, bal, profit)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Nyertes (+10%)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val bal = day.targetBank * 0.8
                            val profit = -day.targetBank * 0.2
                            onStatusSelected(DayStatus.FAILED, bal, profit)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusLost, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Vesztes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { showCustomInput = !showCustomInput }) {
                        Text(if (showCustomInput) "Elrejtés" else "Egyedi egyenleg beírás")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Bezárás")
                    }
                }
            }
        }
    )
}

@Composable
fun StrategyConfigDialog(
    currentConfig: com.example.data.local.entity.StrategyConfigEntity?,
    onDismiss: () -> Unit,
    onSave: (initialBank: Double, dailyRate: Double, baseStake: Double, regenerate: Boolean) -> Unit,
    onReset: () -> Unit
) {
    var initialBankStr by remember { mutableStateOf((currentConfig?.initialBank?.toInt() ?: 10000).toString()) }
    var dailyRateStr by remember { mutableStateOf((currentConfig?.dailyProfitPercent ?: 10.0).toString()) }
    var baseStakeStr by remember { mutableStateOf((currentConfig?.baseStake?.toInt() ?: 200).toString()) }
    var regeneratePlans by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stratégiai Paraméterek") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = initialBankStr,
                    onValueChange = { initialBankStr = it },
                    label = { Text("Kezdő Bank (Ft)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dailyRateStr,
                    onValueChange = { dailyRateStr = it },
                    label = { Text("Napi Profit Cél (%)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baseStakeStr,
                    onValueChange = { baseStakeStr = it },
                    label = { Text("Alaptét (Ft)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = regeneratePlans,
                        onCheckedChange = { regeneratePlans = it }
                    )
                    Text(
                        text = "100 napos terv újragenerálása az új kezdő bankkal",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TextButton(
                    onClick = onReset,
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusLost)
                ) {
                    Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Alaphelyzetbe állítás (10.000 Ft)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bank = initialBankStr.toDoubleOrNull() ?: 10000.0
                    val rate = dailyRateStr.toDoubleOrNull() ?: 10.0
                    val base = baseStakeStr.toDoubleOrNull() ?: 200.0
                    onSave(bank, rate, base, regeneratePlans)
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
