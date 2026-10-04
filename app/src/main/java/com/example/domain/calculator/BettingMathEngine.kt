package com.example.domain.calculator

import com.example.data.local.entity.DayPlanEntity
import com.example.domain.model.CalculatorMode
import com.example.domain.model.PresetOddsRow
import com.example.domain.model.RoundStakeResult
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

object BettingMathEngine {

    private val numberFormatter = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.forLanguageTag("hu-HU")))
    private val currencyFormatter = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.forLanguageTag("hu-HU")))

    fun formatNumber(value: Double): String = numberFormatter.format(value)
    fun formatCurrency(value: Double, currency: String = "Ft"): String = "${currencyFormatter.format(value.roundToInt())} $currency"

    /**
     * Generates 100-day compound plan according to the Excel formulas:
     * - Day 1 Bank = initialBank
     * - Day n Bank = initialBank * (1 + rate)^(n - 1)
     * - MIN ROLL (4 körre) = Bank / 49.25
     * - MAX ROLL (bank / 20) = Bank / 20.0
     * - TERV PLUSZ = Bank * rate (10% napi profit)
     */
    fun generate100DayPlan(
        initialBank: Double = 10000.0,
        dailyProfitPercent: Double = 10.0
    ): List<DayPlanEntity> {
        val rate = dailyProfitPercent / 100.0
        val list = mutableListOf<DayPlanEntity>()

        for (day in 1..100) {
            val targetBank = initialBank * (1.0 + rate).pow(day - 1)
            val minRoll = targetBank / 49.25
            val maxRoll = targetBank / 20.0
            val targetProfit = targetBank * rate

            list.add(
                DayPlanEntity(
                    dayNumber = day,
                    targetBank = targetBank,
                    minRoll = minRoll,
                    maxRoll = maxRoll,
                    targetProfit = targetProfit,
                    actualBalance = null,
                    actualProfit = null,
                    status = "PENDING",
                    notes = ""
                )
            )
        }
        return list
    }

    /**
     * Calculates dynamic stake progression across up to 6 rounds for a given target profit or loss recovery.
     */
    fun calculateProgression(
        currentBank: Double,
        baseStake: Double,
        targetProfit: Double,
        oddsList: List<Double>,
        mode: CalculatorMode
    ): List<RoundStakeResult> {
        val results = mutableListOf<RoundStakeResult>()
        var accumulatedLoss = 0.0

        for (i in oddsList.indices) {
            val round = i + 1
            val odds = oddsList[i].coerceAtLeast(1.05)
            val divisor = odds - 1.0

            val stake: Double = when {
                round == 1 && mode == CalculatorMode.TARGET_PROFIT -> {
                    baseStake
                }
                mode == CalculatorMode.TARGET_PROFIT -> {
                    (targetProfit + accumulatedLoss) / divisor
                }
                else -> { // BREAK_EVEN
                    if (round == 1) {
                        baseStake
                    } else {
                        accumulatedLoss / divisor
                    }
                }
            }

            val totalInvested = accumulatedLoss + stake
            val potentialReturn = stake * odds
            val netProfit = potentialReturn - totalInvested
            val bankPercentage = if (currentBank > 0) (stake / currentBank) * 100.0 else 0.0

            results.add(
                RoundStakeResult(
                    round = round,
                    odds = odds,
                    stake = stake,
                    totalInvested = totalInvested,
                    potentialReturn = potentialReturn,
                    netProfit = netProfit,
                    bankPercentage = bankPercentage
                )
            )

            accumulatedLoss += stake
        }

        return results
    }

    /**
     * Preset odds table matching the Excel spreadsheet (1.12 to 3.03).
     */
    val PRESET_ODDS = listOf(
        1.12, 1.21, 1.25, 1.33, 1.36, 1.39, 1.40, 1.44, 1.50, 1.52, 1.53, 1.57,
        1.61, 1.64, 1.66, 1.72, 1.76, 1.80, 1.83, 1.89, 1.90, 2.00, 2.28, 2.50, 2.78, 3.03
    )

    fun getPresetTable(
        baseStake: Double = 200.0,
        mode: CalculatorMode = CalculatorMode.TARGET_PROFIT
    ): List<PresetOddsRow> {
        val targetProfit = baseStake
        return PRESET_ODDS.map { odds ->
            val divisor = odds - 1.0
            val s1 = baseStake
            val s2 = if (mode == CalculatorMode.TARGET_PROFIT) (targetProfit + s1) / divisor else s1 / divisor
            val s3 = if (mode == CalculatorMode.TARGET_PROFIT) (targetProfit + s1 + s2) / divisor else (s1 + s2) / divisor
            val s4 = if (mode == CalculatorMode.TARGET_PROFIT) (targetProfit + s1 + s2 + s3) / divisor else (s1 + s2 + s3) / divisor
            PresetOddsRow(
                odds = odds,
                round2Stake = s2,
                round3Stake = s3,
                round4Stake = s4
            )
        }
    }

    /**
     * Computes Kelly Criterion suggested stake percentage:
     * f* = (bp - q) / b
     * where b = odds - 1, p = probability of winning, q = 1 - p
     */
    fun calculateKellyStake(
        bankroll: Double,
        odds: Double,
        estimatedWinProbabilityPercent: Double,
        fraction: Double = 0.5 // Half-Kelly for safety
    ): Double {
        if (odds <= 1.0 || estimatedWinProbabilityPercent <= 0.0) return 0.0
        val p = estimatedWinProbabilityPercent / 100.0
        val q = 1.0 - p
        val b = odds - 1.0
        val kellyFraction = (b * p - q) / b
        if (kellyFraction <= 0) return 0.0
        val recommendedFraction = (kellyFraction * fraction).coerceIn(0.01, 0.20)
        return bankroll * recommendedFraction
    }

    /**
     * Computes Expected Value (EV) in percentage.
     * EV = (Prob * Odds - 1) * 100%
     */
    fun calculateExpectedValue(odds: Double, winProbabilityPercent: Double): Double {
        val p = winProbabilityPercent / 100.0
        return (p * odds - 1.0) * 100.0
    }
}
