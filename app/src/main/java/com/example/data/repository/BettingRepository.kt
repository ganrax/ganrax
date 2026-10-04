package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BetMatchEntity
import com.example.data.local.entity.DayPlanEntity
import com.example.data.local.entity.StrategyConfigEntity
import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.DayStatus
import com.example.domain.model.MatchStatus
import com.example.domain.model.StrategyStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BettingRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val dayPlanDao = database.dayPlanDao()
    private val betMatchDao = database.betMatchDao()
    private val configDao = database.strategyConfigDao()

    val allDayPlans: Flow<List<DayPlanEntity>> = dayPlanDao.getAllDayPlans()
    val allMatches: Flow<List<BetMatchEntity>> = betMatchDao.getAllMatches()
    val strategyConfig: Flow<StrategyConfigEntity?> = configDao.getConfigFlow()

    val strategyStats: Flow<StrategyStats> = combine(
        allDayPlans,
        allMatches,
        strategyConfig
    ) { days, matches, config ->
        val cfg = config ?: StrategyConfigEntity()
        val completed = days.count { it.status == "COMPLETED" }
        val failed = days.count { it.status == "FAILED" }

        // Streak calculation
        var streak = 0
        for (day in days) {
            if (day.status == "COMPLETED") {
                streak++
            } else if (day.status == "FAILED") {
                streak = 0
            }
        }

        val wonBets = matches.filter { it.status == "WON" }
        val lostBets = matches.filter { it.status == "LOST" }
        val totalFinishedBets = wonBets.size + lostBets.size
        val winRate = if (totalFinishedBets > 0) (wonBets.size.toDouble() / totalFinishedBets) * 100.0 else 0.0

        val totalStaked = matches.filter { it.status == "WON" || it.status == "LOST" }.sumOf { it.stake }
        val totalWonReturn = wonBets.sumOf { it.stake * it.odds }
        val netProfit = totalWonReturn - totalStaked
        val roi = if (totalStaked > 0) (netProfit / totalStaked) * 100.0 else 0.0

        val currentBank = if (completed > 0) {
            val lastCompleted = days.filter { it.status == "COMPLETED" }.maxByOrNull { it.dayNumber }
            lastCompleted?.actualBalance ?: lastCompleted?.targetBank ?: cfg.currentBank
        } else {
            cfg.currentBank
        }

        StrategyStats(
            totalDays = days.size.coerceAtLeast(100),
            completedDays = completed,
            failedDays = failed,
            currentStreak = streak,
            startingBank = cfg.initialBank,
            currentBank = currentBank,
            totalTargetDay100 = days.lastOrNull()?.targetBank ?: 125278294.0,
            totalProfitRealized = netProfit,
            totalMatches = matches.size,
            wonMatches = wonBets.size,
            lostMatches = lostBets.size,
            winRate = winRate,
            roi = roi
        )
    }.flowOn(Dispatchers.Default)

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        val existingConfig = configDao.getConfig()
        if (existingConfig == null) {
            val initialBank = 10000.0
            val calculatedBase = (initialBank / 49.25).toInt().toDouble().coerceAtLeast(1.0)
            val defaultConfig = StrategyConfigEntity(
                initialBank = initialBank,
                dailyProfitPercent = 10.0,
                currentBank = initialBank,
                activeDay = 1,
                currency = "Ft",
                baseStake = calculatedBase
            )
            configDao.insertOrUpdate(defaultConfig)
            val plans = BettingMathEngine.generate100DayPlan(10000.0, 10.0)
            dayPlanDao.insertAll(plans)
        }
    }

    suspend fun updateConfig(config: StrategyConfigEntity, regenerateDays: Boolean = false) = withContext(Dispatchers.IO) {
        val calculatedBase = (config.currentBank / 49.25).toInt().toDouble().coerceAtLeast(1.0)
        configDao.insertOrUpdate(config.copy(baseStake = calculatedBase))
        if (regenerateDays) {
            val plans = BettingMathEngine.generate100DayPlan(config.initialBank, config.dailyProfitPercent)
            dayPlanDao.clearAll()
            dayPlanDao.insertAll(plans)
        }
    }

    suspend fun updateDayPlan(plan: DayPlanEntity) = withContext(Dispatchers.IO) {
        dayPlanDao.update(plan)
    }

    suspend fun setDayStatus(
        dayNumber: Int,
        status: DayStatus,
        actualBalance: Double? = null,
        actualProfit: Double? = null
    ) = withContext(Dispatchers.IO) {
        val current = dayPlanDao.getDayPlan(dayNumber)
        if (current != null) {
            val newBalance = actualBalance ?: when (status) {
                DayStatus.COMPLETED -> current.targetBank + current.targetProfit
                DayStatus.FAILED -> current.targetBank * 0.8
                else -> current.actualBalance
            }
            val newProfit = actualProfit ?: when (status) {
                DayStatus.COMPLETED -> current.targetProfit
                DayStatus.FAILED -> -(current.targetBank * 0.2)
                else -> current.actualProfit
            }
            dayPlanDao.updateDayStatus(dayNumber, status.name, newBalance, newProfit)

            val config = configDao.getConfig()
            if (config != null) {
                val nextDay = if (status == DayStatus.COMPLETED) (dayNumber + 1).coerceAtMost(100) else config.activeDay
                val newBank = newBalance ?: config.currentBank
                val newBaseStake = (newBank / 49.25).toInt().toDouble().coerceAtLeast(1.0)
                configDao.update(config.copy(activeDay = nextDay, currentBank = newBank, baseStake = newBaseStake))
            }
        }
    }

    suspend fun insertMatch(match: BetMatchEntity): Long = withContext(Dispatchers.IO) {
        betMatchDao.insertMatch(match)
    }

    suspend fun updateMatch(match: BetMatchEntity) = withContext(Dispatchers.IO) {
        betMatchDao.updateMatch(match)
    }

    suspend fun deleteMatch(matchId: Long) = withContext(Dispatchers.IO) {
        betMatchDao.deleteMatchById(matchId)
    }

    suspend fun settleMatch(matchId: Long, status: MatchStatus, homeScore: Int? = null, awayScore: Int? = null) = withContext(Dispatchers.IO) {
        val match = betMatchDao.getMatchById(matchId)
        betMatchDao.updateScoreAndStatus(matchId, status.name, homeScore, awayScore, null)

        if (match != null && (match.status == "PENDING" || match.status == "LIVE")) {
            val config = configDao.getConfig()
            if (config != null) {
                val delta = when (status) {
                    MatchStatus.WON -> match.stake * (match.odds - 1.0)
                    MatchStatus.LOST -> -match.stake
                    else -> 0.0
                }
                if (delta != 0.0) {
                    val updatedBank = (config.currentBank + delta).coerceAtLeast(100.0)
                    val updatedBase = (updatedBank / 49.25).toInt().toDouble().coerceAtLeast(1.0)
                    configDao.update(config.copy(currentBank = updatedBank, baseStake = updatedBase))
                }
            }
        }
    }

    fun getMatchesForDay(dayNumber: Int): Flow<List<BetMatchEntity>> = betMatchDao.getMatchesForDay(dayNumber)

    suspend fun exportDataAsCsv(): String = withContext(Dispatchers.IO) {
        val days = dayPlanDao.getAllDayPlans().first()
        val sb = StringBuilder()
        sb.append("NAP,BANK,MIN ROLL (4 KÖRRE),MAX ROLL (BANK/20),TERV PLUSZ,EGYENLEG,TELJESÍTETT BANK,ÁLLAPOT,MEGJEGYZÉS\n")
        for (d in days) {
            sb.append("${d.dayNumber},${d.targetBank.toInt()},${d.minRoll.toInt()},${d.maxRoll.toInt()},${d.targetProfit.toInt()},${d.actualProfit?.toInt() ?: ""},${d.actualBalance?.toInt() ?: ""},${d.status},\"${d.notes}\"\n")
        }
        sb.toString()
    }

    suspend fun exportDataAsJson(): String = withContext(Dispatchers.IO) {
        val days = dayPlanDao.getAllDayPlans().first()
        val matches = betMatchDao.getAllMatches().first()
        val config = configDao.getConfig()

        val root = JSONObject()
        val daysArray = JSONArray()
        for (d in days) {
            val obj = JSONObject()
            obj.put("dayNumber", d.dayNumber)
            obj.put("targetBank", d.targetBank)
            obj.put("minRoll", d.minRoll)
            obj.put("maxRoll", d.maxRoll)
            obj.put("targetProfit", d.targetProfit)
            obj.put("actualBalance", d.actualBalance ?: JSONObject.NULL)
            obj.put("actualProfit", d.actualProfit ?: JSONObject.NULL)
            obj.put("status", d.status)
            obj.put("notes", d.notes)
            daysArray.put(obj)
        }
        root.put("dayPlans", daysArray)

        val matchesArray = JSONArray()
        for (m in matches) {
            val obj = JSONObject()
            obj.put("dayNumber", m.dayNumber ?: JSONObject.NULL)
            obj.put("roundNumber", m.roundNumber)
            obj.put("sport", m.sport)
            obj.put("league", m.league)
            obj.put("homeTeam", m.homeTeam)
            obj.put("awayTeam", m.awayTeam)
            obj.put("matchTime", m.matchTime)
            obj.put("market", m.market)
            obj.put("tip", m.tip)
            obj.put("odds", m.odds)
            obj.put("stake", m.stake)
            obj.put("status", m.status)
            obj.put("homeScore", m.homeScore ?: JSONObject.NULL)
            obj.put("awayScore", m.awayScore ?: JSONObject.NULL)
            obj.put("notes", m.notes)
            matchesArray.put(obj)
        }
        root.put("matches", matchesArray)

        if (config != null) {
            val configObj = JSONObject()
            configObj.put("initialBank", config.initialBank)
            configObj.put("dailyProfitPercent", config.dailyProfitPercent)
            configObj.put("currentBank", config.currentBank)
            configObj.put("activeDay", config.activeDay)
            configObj.put("currency", config.currency)
            configObj.put("baseStake", config.baseStake)
            root.put("config", configObj)
        }

        root.toString(2)
    }

    suspend fun resetToDefault() = withContext(Dispatchers.IO) {
        val defaultConfig = StrategyConfigEntity(
            initialBank = 10000.0,
            dailyProfitPercent = 10.0,
            currentBank = 10000.0,
            activeDay = 1,
            currency = "Ft",
            baseStake = 200.0
        )
        configDao.insertOrUpdate(defaultConfig)
        val plans = BettingMathEngine.generate100DayPlan(10000.0, 10.0)
        dayPlanDao.clearAll()
        dayPlanDao.insertAll(plans)
    }
}
