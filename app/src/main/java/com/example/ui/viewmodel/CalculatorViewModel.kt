package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BetMatchEntity
import com.example.data.repository.BettingRepository
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.CalculatorMode
import com.example.domain.model.PresetOddsRow
import com.example.domain.model.RoundStakeResult
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

    private val _baseStakeInput = MutableStateFlow("200")
    val baseStakeInput: StateFlow<String> = _baseStakeInput.asStateFlow()

    private val _targetProfitInput = MutableStateFlow("200")
    val targetProfitInput: StateFlow<String> = _targetProfitInput.asStateFlow()

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
        val base = baseStr.toDoubleOrNull() ?: 200.0
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
        val base = baseStr.toDoubleOrNull() ?: 200.0
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
