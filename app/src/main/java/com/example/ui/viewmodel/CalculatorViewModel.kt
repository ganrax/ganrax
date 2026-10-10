package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BetMatchEntity
import com.example.data.repository.BettingRepository
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.CalculatorMatchItem
import com.example.domain.model.CalculatorMode
import com.example.domain.model.MatchStatus
import com.example.domain.model.PresetOddsRow
import com.example.domain.model.ProgressionLevel
import com.example.domain.model.RoundStakeResult
import com.example.domain.util.MatchDisplayHelper
import com.example.domain.util.TelegramAlertParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    // Explicit Team and Strategy identification inputs (editable by user and prefilled by Telegram)
    private val _homeTeamInput = MutableStateFlow("Bnei Yehud")
    val homeTeamInput: StateFlow<String> = _homeTeamInput.asStateFlow()

    private val _awayTeamInput = MutableStateFlow("Maccabi Amishav Petah Tikva")
    val awayTeamInput: StateFlow<String> = _awayTeamInput.asStateFlow()

    private val _strategyNameInput = MutableStateFlow("⚡Second Half Action Ready")
    val strategyNameInput: StateFlow<String> = _strategyNameInput.asStateFlow()

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

    // REAL-TIME REACTIVE STAKE CALCULATION (MINDEN ODDS VÁLTOZÁS AZONNAL ÚJRASZÁMOLÓDIK!)
    val currentCalculatedStake: StateFlow<Double> = combine(
        combine(_activeLevel, _currentOddsInput, _accumulatedLoss) { level, odds, loss ->
            Triple(level, odds, loss)
        },
        combine(_baseStakeInput, _targetProfitInput, _mode) { base, target, mode ->
            Triple(base, target, mode)
        }
    ) { (level, oddsStr, loss), (baseStr, targetStr, mode) ->
        val cleanOdds = oddsStr.replace(",", ".").trim().toDoubleOrNull()?.coerceAtLeast(1.02) ?: 1.50
        val base = baseStr.toDoubleOrNull() ?: 203.0
        val target = targetStr.toDoubleOrNull() ?: base
        val divisor = (cleanOdds - 1.0).coerceAtLeast(0.01)

        val stake = if (mode == CalculatorMode.TARGET_PROFIT) {
            // Kitűzött profit elérése + összes korábbi veszteség megtérülése az egyedi odds-szal!
            (loss + target) / divisor
        } else {
            if (level == 1) base else loss / divisor
        }
        kotlin.math.ceil(stake).coerceAtLeast(1.0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 203.0)

    val currentPotentialReturn: StateFlow<Double> = combine(
        currentCalculatedStake,
        _currentOddsInput
    ) { stake, oddsStr ->
        val cleanOdds = oddsStr.replace(",", ".").trim().toDoubleOrNull()?.coerceAtLeast(1.02) ?: 1.50
        stake * cleanOdds
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 304.5)

    val currentNetProfit: StateFlow<Double> = combine(
        currentPotentialReturn,
        currentCalculatedStake,
        _accumulatedLoss
    ) { potentialReturn, stake, loss ->
        potentialReturn - (loss + stake)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 101.5)

    // Saved Pending Matches (Későbbre elmentett meccsek a helyi adatbázisból!)
    val savedPendingMatches: StateFlow<List<BetMatchEntity>> = repository.allMatches
        .map { list -> list.filter { it.status.equals("PENDING", ignoreCase = true) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        val odds = oddsStrings.map { it.replace(",", ".").toDoubleOrNull() ?: 1.50 }

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
        if (text.isNotBlank()) {
            parseTelegramText()
        }
    }

    fun setHomeTeam(name: String) {
        _homeTeamInput.value = name
        updateCurrentMatchItem()
    }

    fun setAwayTeam(name: String) {
        _awayTeamInput.value = name
        updateCurrentMatchItem()
    }

    fun setStrategyName(name: String) {
        _strategyNameInput.value = name
        updateCurrentMatchItem()
    }

    private fun updateCurrentMatchItem() {
        val home = _homeTeamInput.value.trim()
        val away = _awayTeamInput.value.trim()
        val strat = _strategyNameInput.value.trim()
        val current = _selectedMatch.value
        val name = if (home.isNotBlank() && away.isNotBlank()) "$home vs $away" else (current?.matchName ?: "$home vs $away")
        _selectedMatch.value = current?.copy(
            homeTeam = home,
            awayTeam = away,
            matchName = name,
            strategyName = strat
        ) ?: CalculatorMatchItem(
            homeTeam = home,
            awayTeam = away,
            matchName = name,
            strategyName = strat
        )
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
            val first = items.first()
            _selectedMatch.value = first
            _homeTeamInput.value = first.homeTeam
            _awayTeamInput.value = first.awayTeam
            _strategyNameInput.value = first.strategyName
        }
    }

    fun selectMatch(match: CalculatorMatchItem) {
        _selectedMatch.value = match
        _homeTeamInput.value = match.homeTeam
        _awayTeamInput.value = match.awayTeam
        _strategyNameInput.value = match.strategyName
    }

    fun clearTelegram() {
        _telegramInput.value = ""
        _extractedMatches.value = emptyList()
        _selectedMatch.value = null
        _homeTeamInput.value = ""
        _awayTeamInput.value = ""
        _strategyNameInput.value = ""
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

    fun applyPresetOdd(odds: Double) {
        val formatted = String.format(java.util.Locale.US, "%.2f", odds)
        _currentOddsInput.value = formatted
    }

    /**
     * MENTÉS KÉSŐBBRE: Elmenti az aktuális mérkőzést az adatbázisba PENDING státusszal,
     * a pontos csapatnevekkel (Hazai és Vendég csapat), stratégiával, a kiválasztott oddsszal
     * és kiszámított téttel, így később bármikor azonosítható és visszatölthető!
     */
    fun saveCurrentMatchForLater(onSavedToast: (String) -> Unit) {
        val home = _homeTeamInput.value.trim().ifBlank { _selectedMatch.value?.homeTeam?.trim() ?: "Hazai csapat" }
        val away = _awayTeamInput.value.trim().ifBlank { _selectedMatch.value?.awayTeam?.trim() ?: "Vendég csapat" }
        val strategy = _strategyNameInput.value.trim().ifBlank { _selectedMatch.value?.strategyName?.trim() ?: "Telegram Alert" }
        val matchName = if (home.isNotBlank() && away.isNotBlank()) "$home vs $away" else (_selectedMatch.value?.matchName ?: "$home vs $away")
        val odds = _currentOddsInput.value.replace(",", ".").trim().toDoubleOrNull() ?: 1.50
        val stake = currentCalculatedStake.value

        viewModelScope.launch {
            val entity = BetMatchEntity(
                dayNumber = currentBank.value?.activeDay ?: 1,
                roundNumber = _activeLevel.value,
                sport = "Labdarúgás",
                league = _selectedMatch.value?.league ?: "",
                homeTeam = home,
                awayTeam = away,
                market = "Szint ${_activeLevel.value}",
                tip = strategy,
                odds = odds,
                stake = stake,
                status = "PENDING",
                notes = "$strategy | $matchName | Mentve későbbre"
            )
            repository.insertMatch(entity)
            onSavedToast("💾 '$matchName' ($strategy) sikeresen elmentve!")
        }
    }

    /**
     * Elmentett meccs betöltése a kalkulátorba (pontos csapatnevekkel és stratégiával!)
     */
    fun loadSavedMatchIntoCalculator(match: BetMatchEntity) {
        val resolved = MatchDisplayHelper.resolve(match)
        _homeTeamInput.value = resolved.homeTeam
        _awayTeamInput.value = resolved.awayTeam
        _strategyNameInput.value = resolved.strategyName
        _selectedMatch.value = CalculatorMatchItem(
            homeTeam = resolved.homeTeam,
            awayTeam = resolved.awayTeam,
            matchName = resolved.fullMatchTitle,
            strategyName = resolved.strategyName,
            league = match.league,
            oddsInput = match.odds.toString()
        )
        _currentOddsInput.value = String.format(java.util.Locale.US, "%.2f", match.odds)
        _activeLevel.value = match.roundNumber
    }

    /**
     * Elmentett meccs csapatainak és stratégiájának közvetlen szerkesztése és azonosítása
     */
    fun updateSavedMatchDetails(matchId: Long, homeTeam: String, awayTeam: String, strategyName: String, onUpdated: (String) -> Unit) {
        viewModelScope.launch {
            val all = repository.allMatches.first()
            val existing = all.find { it.id == matchId }
            if (existing != null) {
                val cleanHome = homeTeam.trim().ifBlank { "Hazai csapat" }
                val cleanAway = awayTeam.trim().ifBlank { "Vendég csapat" }
                val cleanStrat = strategyName.trim().ifBlank { existing.tip }
                val updated = existing.copy(
                    homeTeam = cleanHome,
                    awayTeam = cleanAway,
                    tip = cleanStrat,
                    notes = "$cleanStrat | $cleanHome vs $cleanAway | Mentve későbbre"
                )
                repository.updateMatch(updated)
                onUpdated("✅ '$cleanHome vs $cleanAway' adatai sikeresen frissítve!")
            }
        }
    }

    /**
     * Elmentett meccs törlése
     */
    fun deleteSavedMatch(matchId: Long, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteMatch(matchId)
            onDeleted()
        }
    }

    /**
     * Elmentett meccs közvetlen elszámolása (NYERT / VESZTETT)
     */
    fun settleSavedMatch(match: BetMatchEntity, won: Boolean, onSettleToast: (String) -> Unit) {
        viewModelScope.launch {
            val status = if (won) MatchStatus.WON else MatchStatus.LOST
            repository.settleMatch(match.id, status)
            if (won) {
                val netProfit = match.stake * (match.odds - 1.0)
                onSettleToast("🎉 ${match.homeTeam} vs ${match.awayTeam} NYERT! +${netProfit.toInt()} Ft jóváírva a tőkében!")
            } else {
                _accumulatedLoss.value += match.stake
                _activeLevel.value += 1
                onSettleToast("🔴 ${match.homeTeam} vs ${match.awayTeam} VESZTETT (-${match.stake.toInt()} Ft). Szint növelve: ${_activeLevel.value}")
            }
        }
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
        val odds = _currentOddsInput.value.replace(",", ".").trim().toDoubleOrNull()?.coerceAtLeast(1.02) ?: 1.50
        val stake = currentCalculatedStake.value
        val ret = currentPotentialReturn.value
        val netProfit = currentNetProfit.value
        val currentMatch = _selectedMatch.value

        val home = _homeTeamInput.value.trim().ifBlank { _selectedMatch.value?.homeTeam?.trim() ?: "Hazai csapat" }
        val away = _awayTeamInput.value.trim().ifBlank { _selectedMatch.value?.awayTeam?.trim() ?: "Vendég csapat" }
        val strategyName = _strategyNameInput.value.trim().ifBlank { _selectedMatch.value?.strategyName?.trim() ?: "Stratégia" }
        val matchName = if (home.isNotBlank() && away.isNotBlank()) "$home vs $away" else "Kör $currentLevelNum Fogadás"

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
                    homeTeam = home,
                    awayTeam = away,
                    market = "Szint $currentLevelNum",
                    tip = strategyName,
                    odds = odds,
                    stake = stake,
                    status = "WON",
                    notes = "$strategyName | $matchName | Széria lezárva a(z) $currentLevelNum. szinten! (+${netProfit.toInt()} Ft)"
                )
                repository.insertMatch(entity)

                onCompletedToast("🎉 $matchName ($strategyName) NYERT! +${netProfit.toInt()} Ft tiszta profit jóváírva!")
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
                    homeTeam = home,
                    awayTeam = away,
                    market = "Szint $currentLevelNum",
                    tip = strategyName,
                    odds = odds,
                    stake = stake,
                    status = "LOST",
                    notes = "$strategyName | $matchName | $currentLevelNum. szint vesztett (-${stake.toInt()} Ft)"
                )
                repository.insertMatch(entity)

                // Advance to next match from Telegram if available
                val nextIndex = _extractedMatches.value.indexOfFirst { it.id == currentMatch?.id } + 1
                if (nextIndex in _extractedMatches.value.indices) {
                    selectMatch(_extractedMatches.value[nextIndex])
                }

                onCompletedToast("🔴 $matchName ($currentLevelNum. szint) veszített (-${stake.toInt()} Ft). Új tét számolva a(z) ${_activeLevel.value}. szintre!")
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

    class Factory(private val repository: BettingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalculatorViewModel(repository) as T
        }
    }
}
