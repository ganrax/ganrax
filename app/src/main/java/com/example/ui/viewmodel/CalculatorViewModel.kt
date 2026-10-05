package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BetMatchEntity
import com.example.data.repository.BettingRepository
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.CalculatorMatchItem
import com.example.domain.model.CalculatorMode
import com.example.domain.model.PresetOddsRow
import com.example.domain.model.RoundStakeResult
import com.example.domain.util.TelegramAlertParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalculatorViewModel(private val repository: BettingRepository) : ViewModel() {

    private val _mode = MutableStateFlow(CalculatorMode.TARGET_PROFIT)
    val mode: StateFlow<CalculatorMode> = _mode.asStateFlow()

    private val _bankrollInput = MutableStateFlow("10000")
    val bankrollInput: StateFlow<String> = _bankrollInput.asStateFlow()

    private val _baseStakeInput = MutableStateFlow("203")
    val baseStakeInput: StateFlow<String> = _baseStakeInput.asStateFlow()

    private val _targetProfitInput = MutableStateFlow("203")
    val targetProfitInput: StateFlow<String> = _targetProfitInput.asStateFlow()

    private val _roundOdds = MutableStateFlow(listOf("1.50", "1.50", "1.50", "1.50"))
    val roundOdds: StateFlow<List<String>> = _roundOdds.asStateFlow()

    // Telegram input and parsed matches for unified calculation
    private val _telegramInput = MutableStateFlow("")
    val telegramInput: StateFlow<String> = _telegramInput.asStateFlow()

    private val _parsedMatches = MutableStateFlow<List<CalculatorMatchItem>>(emptyList())
    val parsedMatches: StateFlow<List<CalculatorMatchItem>> = _parsedMatches.asStateFlow()

    val currentBank = repository.strategyConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val progressionResults: StateFlow<List<RoundStakeResult>> = combine(
        _mode,
        _bankrollInput,
        _baseStakeInput,
        _targetProfitInput,
        _roundOdds
    ) { mode, bankStr, baseStr, targetStr, oddsStrings ->
        val bank = bankStr.toDoubleOrNull() ?: 10000.0
        val base = baseStr.toDoubleOrNull() ?: 203.0
        val target = targetStr.toDoubleOrNull() ?: base
        val odds = oddsStrings.map { it.toDoubleOrNull() ?: 1.50 }

        BettingMathEngine.calculateProgression(
            currentBank = bank,
            baseStake = base,
            targetProfit = target,
            oddsList = odds,
            mode = mode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presetOddsTable: StateFlow<List<PresetOddsRow>> = combine(
        _baseStakeInput,
        _mode
    ) { baseStr, mode ->
        val base = baseStr.toDoubleOrNull() ?: 203.0
        BettingMathEngine.getPresetTable(base, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.strategyConfig.collect { config ->
                if (config != null) {
                    val bank = config.currentBank
                    val autoBase = (bank / 49.25).toInt().coerceAtLeast(1)
                    _bankrollInput.value = bank.toInt().toString()
                    _baseStakeInput.value = autoBase.toString()
                    _targetProfitInput.value = autoBase.toString()
                    recalculateParsedMatches()
                }
            }
        }

        // Preload sample matches so the user sees immediate value on first opening
        loadSampleTelegram(autoParse = true)
    }

    fun setMode(newMode: CalculatorMode) {
        _mode.value = newMode
        recalculateParsedMatches()
    }

    fun setBankroll(value: String) {
        _bankrollInput.value = value
        val bank = value.toDoubleOrNull() ?: 10000.0
        val autoBase = (bank / 49.25).toInt().coerceAtLeast(1)
        _baseStakeInput.value = autoBase.toString()
        _targetProfitInput.value = autoBase.toString()
        recalculateParsedMatches()
    }

    fun setBaseStake(value: String) {
        _baseStakeInput.value = value
        if (_mode.value == CalculatorMode.TARGET_PROFIT) {
            _targetProfitInput.value = value
        }
        recalculateParsedMatches()
    }

    fun setTargetProfit(value: String) {
        _targetProfitInput.value = value
        recalculateParsedMatches()
    }

    fun updateOdd(roundIndex: Int, oddStr: String) {
        val current = _roundOdds.value.toMutableList()
        if (roundIndex in current.indices) {
            current[roundIndex] = oddStr
            _roundOdds.value = current
        }
    }

    fun applyPresetOdd(odds: Double) {
        val formatted = String.format(java.util.Locale.US, "%.2f", odds)
        _roundOdds.value = List(4) { formatted }
    }

    fun addRound() {
        if (_roundOdds.value.size < 6) {
            _roundOdds.value = _roundOdds.value + "1.50"
        }
    }

    fun removeRound() {
        if (_roundOdds.value.size > 2) {
            _roundOdds.value = _roundOdds.value.dropLast(1)
        }
    }

    // Telegram Unified Handling
    fun setTelegramInput(text: String) {
        _telegramInput.value = text
    }

    fun parseTelegramText() {
        val text = _telegramInput.value
        if (text.isBlank()) return

        val parsedAlerts = TelegramAlertParser.parseMessages(text)
        val base = _baseStakeInput.value.toDoubleOrNull() ?: 203.0
        val bank = _bankrollInput.value.toDoubleOrNull() ?: 10000.0

        val items = parsedAlerts.map { alert ->
            val initialOdds = alert.liveOdds1X2.split(" ").firstOrNull()?.toDoubleOrNull()
                ?: 1.50

            val item = CalculatorMatchItem(
                homeTeam = alert.homeTeam,
                awayTeam = alert.awayTeam,
                matchName = alert.matchName,
                league = alert.league,
                strategyName = alert.strategyName,
                timer = alert.timer,
                score = alert.score,
                googleSearchUrl = alert.googleSearchUrl,
                flashscoreSearchUrl = alert.flashscoreSearchUrl,
                oddsInput = String.format(java.util.Locale.US, "%.2f", initialOdds),
                selectedRound = 1
            )
            calculateMatchStake(item, base, bank, _mode.value)
        }
        _parsedMatches.value = items
    }

    fun updateMatchOdds(matchId: String, newOddsStr: String) {
        val base = _baseStakeInput.value.toDoubleOrNull() ?: 203.0
        val bank = _bankrollInput.value.toDoubleOrNull() ?: 10000.0

        _parsedMatches.value = _parsedMatches.value.map { item ->
            if (item.id == matchId) {
                val updated = item.copy(oddsInput = newOddsStr)
                calculateMatchStake(updated, base, bank, _mode.value)
            } else {
                item
            }
        }
    }

    fun updateMatchRound(matchId: String, newRound: Int) {
        val base = _baseStakeInput.value.toDoubleOrNull() ?: 203.0
        val bank = _bankrollInput.value.toDoubleOrNull() ?: 10000.0

        _parsedMatches.value = _parsedMatches.value.map { item ->
            if (item.id == matchId) {
                val updated = item.copy(selectedRound = newRound.coerceIn(1, 4))
                calculateMatchStake(updated, base, bank, _mode.value)
            } else {
                item
            }
        }
    }

    private fun calculateMatchStake(
        item: CalculatorMatchItem,
        base: Double,
        bank: Double,
        mode: CalculatorMode
    ): CalculatorMatchItem {
        val odds = item.oddsInput.toDoubleOrNull()?.coerceAtLeast(1.05) ?: 1.50
        val round = item.selectedRound

        // Calculate progression stakes up to this round for exact hand-entered odds
        val oddsList = List(round) { odds }
        val progression = BettingMathEngine.calculateProgression(
            currentBank = bank,
            baseStake = base,
            targetProfit = base,
            oddsList = oddsList,
            mode = mode
        )

        val targetResult = progression.getOrNull(round - 1)
        val calculatedStake = targetResult?.stake ?: base
        val potentialReturn = calculatedStake * odds
        val netProfit = targetResult?.netProfit ?: (potentialReturn - calculatedStake)

        return item.copy(
            calculatedStake = calculatedStake,
            potentialReturn = potentialReturn,
            netProfit = netProfit
        )
    }

    private fun recalculateParsedMatches() {
        val base = _baseStakeInput.value.toDoubleOrNull() ?: 203.0
        val bank = _bankrollInput.value.toDoubleOrNull() ?: 10000.0
        val mode = _mode.value

        _parsedMatches.value = _parsedMatches.value.map { item ->
            calculateMatchStake(item, base, bank, mode)
        }
    }

    fun loadSampleTelegram(autoParse: Boolean = true) {
        val sample = """
[2026. 10. 04. 20:52] ⚽️ ganrax Alerts: 🔔 ⚡Second Half Action Ready

🇮🇱 Israel Liga Bet South 
Bnei Yehud vs Maccabi Amishav Petah Tikva
🟥🟩🟥🟩🟥 - 🟩🟩🟨🟩🟥

Timer: 50'
Last Goal: Away at 20' (30 minutes ago)

Goals: 0 - 1
Corners: 3 - 1
Momentum: 105 - 53
Shots On Target: 5 - 5
Attacks: 69 - 56
Dangerous Attacks: 39 - 23
Possession %: 66 - 34

1X2 Live Odds:
3.75 3.40 1.91
Both Teams To Score:
1.36 3.00


[2026. 10. 04. 21:11] ⚽️ ganrax Alerts: 🔔 Both Teams to Score (Favorite conceded first)

🇪🇸 Spain Primera Division RFEF Group 2 (4th vs 19th)
Real Zaragoza vs Teruel
🟥🟩🟩🟩🟩 - 🟨🟥🟥🟥🟨

Timer: 11'
Goals: 0 - 1
Corners: 0 - 0
Momentum: 20 - 30
1X2 Live Odds:
2.40 3.40 2.75
Both Teams To Score:
1.17 4.50
        """.trimIndent()

        _telegramInput.value = sample
        if (autoParse) {
            parseTelegramText()
        }
    }

    fun clearTelegram() {
        _telegramInput.value = ""
        _parsedMatches.value = emptyList()
    }

    fun saveParsedMatchToTracker(matchId: String, onSaved: () -> Unit) {
        val item = _parsedMatches.value.find { it.id == matchId } ?: return
        val round = item.selectedRound
        val odds = item.oddsInput.toDoubleOrNull() ?: 1.50
        val stake = item.calculatedStake

        viewModelScope.launch {
            val match = BetMatchEntity(
                dayNumber = currentBank.value?.activeDay ?: 1,
                roundNumber = round,
                sport = "Labdarúgás",
                league = item.league.ifBlank { "Ismeretlen liga" },
                homeTeam = item.homeTeam.ifBlank { "Hazai csapat" },
                awayTeam = item.awayTeam.ifBlank { "Vendég csapat" },
                market = if (item.strategyName.contains("Both Teams", ignoreCase = true)) "BTTS" else "1X2",
                tip = item.strategyName.ifBlank { "Telegram Alert" },
                odds = odds,
                stake = stake,
                notes = "${item.strategyName} | Google: ${item.googleSearchUrl}",
                status = "LIVE"
            )
            repository.insertMatch(match)

            // Mark as saved
            _parsedMatches.value = _parsedMatches.value.map {
                if (it.id == matchId) it.copy(isSavedToTracker = true) else it
            }
            onSaved()
        }
    }

    fun saveStakeAsMatch(
        round: Int,
        odds: Double,
        stake: Double,
        homeTeam: String,
        awayTeam: String,
        sport: String,
        league: String,
        tip: String,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            val match = BetMatchEntity(
                dayNumber = currentBank.value?.activeDay ?: 1,
                roundNumber = round,
                sport = sport,
                league = league,
                homeTeam = homeTeam,
                awayTeam = awayTeam,
                market = "Tétkezelő kör $round",
                tip = tip,
                odds = odds,
                stake = stake,
                status = "PENDING"
            )
            repository.insertMatch(match)
            onSaved()
        }
    }

    class Factory(private val repository: BettingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalculatorViewModel(repository) as T
        }
    }
}
