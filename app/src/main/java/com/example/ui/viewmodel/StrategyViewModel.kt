package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.DayPlanEntity
import com.example.data.local.entity.StrategyConfigEntity
import com.example.data.repository.BettingRepository
import com.example.domain.model.DayStatus
import com.example.domain.model.StrategyStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StrategyViewModel(private val repository: BettingRepository) : ViewModel() {

    val allDays: StateFlow<List<DayPlanEntity>> = repository.allDayPlans
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<StrategyConfigEntity?> = repository.strategyConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats: StateFlow<StrategyStats> = repository.strategyStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StrategyStats())

    private val _filter = MutableStateFlow("ALL") // ALL, PENDING, COMPLETED, FAILED
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _selectedDay = MutableStateFlow<DayPlanEntity?>(null)
    val selectedDay: StateFlow<DayPlanEntity?> = _selectedDay.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
        }
    }

    fun setFilter(newFilter: String) {
        _filter.value = newFilter
    }

    fun selectDay(day: DayPlanEntity?) {
        _selectedDay.value = day
    }

    fun updateDayStatus(dayNumber: Int, status: DayStatus, actualBalance: Double? = null, actualProfit: Double? = null) {
        viewModelScope.launch {
            repository.setDayStatus(dayNumber, status, actualBalance, actualProfit)
            _selectedDay.value = null
        }
    }

    fun updateDayPlan(plan: DayPlanEntity) {
        viewModelScope.launch {
            repository.updateDayPlan(plan)
        }
    }

    fun updateStrategyConfig(initialBank: Double, dailyRate: Double, baseStake: Double, regenerate: Boolean) {
        viewModelScope.launch {
            val current = config.value ?: StrategyConfigEntity()
            val updated = current.copy(
                initialBank = initialBank,
                dailyProfitPercent = dailyRate,
                baseStake = baseStake,
                currentBank = if (regenerate) initialBank else current.currentBank
            )
            repository.updateConfig(updated, regenerateDays = regenerate)
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            repository.resetToDefault()
        }
    }

    fun exportCsv(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val csv = repository.exportDataAsCsv()
            onResult(csv)
        }
    }

    fun exportJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportDataAsJson()
            onResult(json)
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    class Factory(private val repository: BettingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StrategyViewModel(repository) as T
        }
    }
}
