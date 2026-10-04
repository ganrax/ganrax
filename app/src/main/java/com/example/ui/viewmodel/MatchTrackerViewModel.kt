package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BetMatchEntity
import com.example.data.repository.BettingRepository
import com.example.domain.model.MatchStatus
import com.example.network.gemini.GeminiSportsService
import com.example.network.gemini.MatchAiAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MatchTrackerViewModel(
    private val repository: BettingRepository,
    private val geminiService: GeminiSportsService = GeminiSportsService()
) : ViewModel() {

    private val _statusFilter = MutableStateFlow("ALL") // ALL, PENDING, LIVE, WON, LOST
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allMatches: StateFlow<List<BetMatchEntity>> = combine(
        repository.allMatches,
        _statusFilter,
        _searchQuery
    ) { matches, filter, query ->
        matches.filter { match ->
            val matchesFilter = when (filter) {
                "ALL" -> true
                else -> match.status.equals(filter, ignoreCase = true)
            }
            val matchesQuery = query.isBlank() ||
                    match.homeTeam.contains(query, ignoreCase = true) ||
                    match.awayTeam.contains(query, ignoreCase = true) ||
                    match.league.contains(query, ignoreCase = true) ||
                    match.sport.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats = repository.strategyStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _analyzingMatch = MutableStateFlow<BetMatchEntity?>(null)
    val analyzingMatch: StateFlow<BetMatchEntity?> = _analyzingMatch.asStateFlow()

    private val _aiAnalysisResult = MutableStateFlow<MatchAiAnalysis?>(null)
    val aiAnalysisResult: StateFlow<MatchAiAnalysis?> = _aiAnalysisResult.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    fun setFilter(filter: String) {
        _statusFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addMatch(match: BetMatchEntity) {
        viewModelScope.launch {
            repository.insertMatch(match)
        }
    }

    fun updateMatch(match: BetMatchEntity) {
        viewModelScope.launch {
            repository.updateMatch(match)
        }
    }

    fun deleteMatch(id: Long) {
        viewModelScope.launch {
            repository.deleteMatch(id)
        }
    }

    fun settleMatch(id: Long, status: MatchStatus, homeScore: Int? = null, awayScore: Int? = null) {
        viewModelScope.launch {
            repository.settleMatch(id, status, homeScore, awayScore)
        }
    }

    fun analyzeWithAi(match: BetMatchEntity, bankroll: Double) {
        _analyzingMatch.value = match
        _isAnalyzing.value = true
        _aiAnalysisResult.value = null

        viewModelScope.launch {
            try {
                val analysis = geminiService.analyzeMatch(
                    sport = match.sport,
                    league = match.league,
                    homeTeam = match.homeTeam,
                    awayTeam = match.awayTeam,
                    market = match.market,
                    tip = match.tip,
                    odds = match.odds,
                    round = match.roundNumber,
                    bankroll = bankroll
                )
                _aiAnalysisResult.value = analysis
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun closeAnalysisDialog() {
        _analyzingMatch.value = null
        _aiAnalysisResult.value = null
    }

    class Factory(private val repository: BettingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MatchTrackerViewModel(repository) as T
        }
    }
}
