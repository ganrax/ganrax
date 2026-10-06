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
import com.example.domain.model.ProgressionLevel
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

    // Telegram input and extracted matches
    private val _telegramInput = MutableStateFlow("")
    val telegramInput: StateFlow<String> = _telegramInput.asStateFlow()

    private val _extractedMatches = MutableStateFlow<List<CalculatorMatchItem>>(emptyList())
    val extractedMatches: StateFlow<List<CalculatorMatchItem>> = _extractedMatches.asStateFlow()

    private val _selectedMatch = MutableStateFlow<CalculatorMatchItem?>(null)
    val selectedMatch: StateFlow<CalculatorMatchItem?> = _selectedMatch.asStateFlow()

    // Dynamic Progression Series (Körök és Szintek Vezérlője)
    private val _activeLevel = MutableStateFlow(1)
    val activeLevel: StateFlow<Int> = _activeLevel.asStateFlow()

    private val _currentOddsInput = MutableStateFlow("1.50")
    val currentOddsInput: StateFlow<String> = _currentOddsInput.asStateFlow()

    private val _accumulatedLoss = MutableStateFlow(0.0)
    val accumulatedLoss: StateFlow<Double> = _accumulatedLoss.asStateFlow()

    private val _levelHistory = MutableStateFlow<List<ProgressionLevel>>(emptyList())
    val levelHistory: StateFlow<List<ProgressionLevel>> = _levelHistory.asStateFlow()

    private val _isSeriesCompleted = MutableStateFlow(false)
    val isSeriesCompleted: StateFlow<Boolean> = _isSeriesCompleted.asStateFlow()

    private val _lastWonProfit = MutableStateFlow(0.0)
    val lastWonProfit: StateFlow<Double> = _lastWonProfit.asStateFlow()

    // Standard progression ladder for overview
    private val _roundOdds = MutableStateFlow(listOf("1.50", "1.50", "1.50", "1.50"))
    val roundOdds: StateFlow<List<String>> = _roundOdds.asStateFlow()

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
                }
            }
        }

        // Load sample Telegram matches on start
        loadSampleTelegram(autoParse = true)
    }

    fun setMode(newMode: CalculatorMode) {
        _mode.value = newMode
    }

    fun setBankroll(value: String) {
        _bankrollInput.value = value
        val bank = value.toDoubleOrNull() ?: 10000.0
        val autoBase = (bank / 49.25).toInt().coerceAtLeast(1)
        _baseStakeInput.value = autoBase.toString()
        _targetProfitInput.value = autoBase.toString()
    }

    fun setBaseStake(value: String) {
        _baseStakeInput.value = value
        if (_mode.value == CalculatorMode.TARGET_PROFIT) {
            _targetProfitInput.value = value
        }
    }

    fun setTargetProfit(value: String) {
        _targetProfitInput.value = value
    }

    // Telegram Alert Parsing: Extracts ONLY Strategy Name and Match / Teams
    fun setTelegramInput(text: String) {
        _telegramInput.value = text
    }

    fun parseTelegramText() {
        val text = _telegramInput.value
        if (text.isBlank()) return

        val parsedAlerts = TelegramAlertParser.parseMessages(text)
        val items = parsedAlerts.map { alert ->
            CalculatorMatchItem(
                homeTeam = alert.homeTeam.ifBlank { "Hazai csapat" },
                awayTeam = alert.awayTeam.ifBlank { "Vendég csapat" },
                matchName = alert.matchName.ifBlank { "${alert.homeTeam} vs ${alert.awayTeam}" },
                league = alert.league,
                strategyName = alert.strategyName.ifBlank { "Telegram Alert" },
                timer = alert.timer,
                score = alert.score,
                googleSearchUrl = alert.googleSearchUrl,
                flashscoreSearchUrl = alert.flashscoreSearchUrl,
                oddsInput = "1.50",
                selectedRound = 1
            )
        }
        _extractedMatches.value = items
        if (items.isNotEmpty()) {
            _selectedMatch.value = items.first()
        }
    }

    fun selectMatch(match: CalculatorMatchItem) {
        _selectedMatch.value = match
    }

    fun clearTelegram() {
        _telegramInput.value = ""
        _extractedMatches.value = emptyList()
        _selectedMatch.value = null
    }

    fun loadSampleTelegram(autoParse: Boolean = true) {
        val sample = """
[2026. 10. 04. 20:52] ⚽️ ganrax Alerts: 🔔 ⚡Second Half Action Ready

🇮🇱 Israel Liga Bet South 
Bnei Yehud vs Maccabi Amishav Petah Tikva
🟥🟩🟥🟩🟥 - 🟩🟩🟨🟩🟥

Timer: 50'
Goals: 0 - 1

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

    // Dynamic Level Progression Calculation
    fun setOddsInput(newOdds: String) {
        _currentOddsInput.value = newOdds
    }

    fun calculateStakeForCurrentLevel(): Double {
        val odds = _currentOddsInput.value.toDoubleOrNull()?.coerceAtLeast(1.05) ?: 1.50
        val base = _baseStakeInput.value.toDoubleOrNull() ?: 203.0
        val target = _targetProfitInput.value.toDoubleOrNull() ?: base
        val loss = _accumulatedLoss.value
        val level = _activeLevel.value

        return if (level == 1) {
            base
        } else {
            val divisor = (odds - 1.0).coerceAtLeast(0.05)
            if (_mode.value == CalculatorMode.TARGET_PROFIT) {
                (target + loss) / divisor
            } else {
                loss / divisor
            }
        }
    }

    fun calculatePotentialReturn(): Double {
        val stake = calculateStakeForCurrentLevel()
        val odds = _currentOddsInput.value.toDoubleOrNull()?.coerceAtLeast(1.05) ?: 1.50
        return stake * odds
    }

    fun calculateNetProfitIfWon(): Double {
        val ret = calculatePotentialReturn()
        val stake = calculateStakeForCurrentLevel()
        val totalInvested = _accumulatedLoss.value + stake
        return ret - totalInvested
    }

    /**
     * User reports the result of the current level:
     * - If WON:
     *   "ha nyert a fogadás 1. Szintnél nem kell tovább számolni"
     *   The series is successfully finished! Net profit is added to bankroll.
     * - If LOST:
     *   "ha veszít akkor addig számolsz amíg nem nyer, minden egyes szintnél megadom az odds értéket Amihez számolnod kell a logika szerint a szükséges tét összegét..."
     *   Accumulate loss, move to next level (Level 2, 3, etc.), ready for user to enter new odds!
     */
    fun recordLevelResult(won: Boolean, onCompletedToast: (String) -> Unit) {
        val currentLevelNum = _activeLevel.value
        val odds = _currentOddsInput.value.toDoubleOrNull()?.coerceAtLeast(1.05) ?: 1.50
        val stake = calculateStakeForCurrentLevel()
        val ret = calculatePotentialReturn()
        val netProfit = calculateNetProfitIfWon()
        val currentMatch = _selectedMatch.value

        val matchName = currentMatch?.matchName ?: "Kör $currentLevelNum Fogadás"
        val strategyName = currentMatch?.strategyName ?: "Stratégia"

        viewModelScope.launch {
            if (won) {
                // Record level as WON
                val levelRecord = ProgressionLevel(
                    levelNumber = currentLevelNum,
                    matchName = matchName,
                    strategyName = strategyName,
                    odds = odds,
                    stake = stake,
                    potentialReturn = ret,
                    netProfit = netProfit,
                    status = "WON"
                )
                _levelHistory.value = _levelHistory.value + levelRecord

                // Mark series finished
                _isSeriesCompleted.value = true
                _lastWonProfit.value = netProfit

                // Credit net profit to database bankroll
                repository.addProfitToBank(netProfit)

                // Log into matches repository
                val entity = BetMatchEntity(
                    dayNumber = currentBank.value?.activeDay ?: 1,
                    roundNumber = currentLevelNum,
                    sport = "Labdarúgás",
                    league = currentMatch?.league ?: "",
                    homeTeam = currentMatch?.homeTeam ?: "Hazai",
                    awayTeam = currentMatch?.awayTeam ?: "Vendég",
                    market = "Szint $currentLevelNum",
                    tip = strategyName,
                    odds = odds,
                    stake = stake,
                    status = "WON",
                    notes = "Széria lezárva a(z) $currentLevelNum. szinten! Net profit: +${netProfit.toInt()} Ft"
                )
                repository.insertMatch(entity)

                onCompletedToast("🎉 $currentLevelNum. Szint NYERT! +${netProfit.toInt()} Ft tiszta profit hozzáadva a tőkéhez!")
            } else {
                // Record level as LOST
                val levelRecord = ProgressionLevel(
                    levelNumber = currentLevelNum,
                    matchName = matchName,
                    strategyName = strategyName,
                    odds = odds,
                    stake = stake,
                    potentialReturn = 0.0,
                    netProfit = -stake,
                    status = "LOST"
                )
                _levelHistory.value = _levelHistory.value + levelRecord

                // Accumulate loss and step to next level
                _accumulatedLoss.value = _accumulatedLoss.value + stake
                _activeLevel.value = currentLevelNum + 1

                // Log loss into database matches
                val entity = BetMatchEntity(
                    dayNumber = currentBank.value?.activeDay ?: 1,
                    roundNumber = currentLevelNum,
                    sport = "Labdarúgás",
                    league = currentMatch?.league ?: "",
                    homeTeam = currentMatch?.homeTeam ?: "Hazai",
                    awayTeam = currentMatch?.awayTeam ?: "Vendég",
                    market = "Szint $currentLevelNum",
                    tip = strategyName,
                    odds = odds,
                    stake = stake,
                    status = "LOST",
                    notes = "$currentLevelNum. szint veszített (-${stake.toInt()} Ft). Következő szint: ${_activeLevel.value}."
                )
                repository.insertMatch(entity)

                // Advance to next match from Telegram if available
                val nextIndex = _extractedMatches.value.indexOfFirst { it.id == currentMatch?.id } + 1
                if (nextIndex in _extractedMatches.value.indices) {
                    _selectedMatch.value = _extractedMatches.value[nextIndex]
                }

                onCompletedToast("🔴 $currentLevelNum. Szint veszített (-${stake.toInt()} Ft). Szükséges tét kiszámítva a(z) ${_activeLevel.value}. szintre!")
            }
        }
    }

    fun startNewSeries() {
        _activeLevel.value = 1
        _accumulatedLoss.value = 0.0
        _levelHistory.value = emptyList()
        _isSeriesCompleted.value = false
        _lastWonProfit.value = 0.0
        _currentOddsInput.value = "1.50"
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
        _currentOddsInput.value = formatted
    }

    class Factory(private val repository: BettingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalculatorViewModel(repository) as T
        }
    }
}
