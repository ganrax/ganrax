package com.example

import com.example.domain.calculator.BettingMathEngine
import com.example.domain.model.CalculatorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ExampleUnitTest {
    @Test
    fun test100DayCompoundFormula() {
        val plans = BettingMathEngine.generate100DayPlan(10000.0, 10.0)
        assertEquals(100, plans.size)

        // Day 1
        assertEquals(10000.0, plans[0].targetBank, 0.01)
        assertEquals(10000.0 / 49.25, plans[0].minRoll, 0.01)
        assertEquals(500.0, plans[0].maxRoll, 0.01)
        assertEquals(1000.0, plans[0].targetProfit, 0.01)

        // Day 2 (11,000)
        assertEquals(11000.0, plans[1].targetBank, 0.01)
        assertEquals(11000.0 / 49.25, plans[1].minRoll, 0.01)
        assertEquals(550.0, plans[1].maxRoll, 0.01)
        assertEquals(1100.0, plans[1].targetProfit, 0.01)

        // Day 100
        val day100 = plans[99]
        assertTrue(day100.targetBank > 125000000.0)
    }

    @Test
    fun testMultiRoundTargetProfitProgression() {
        // At odds 1.50, base stake 200:
        // Round 1: 200
        // Round 2: (200 + 200) / 0.5 = 800
        // Round 3: (200 + 200 + 800) / 0.5 = 2400
        // Round 4: (200 + 200 + 800 + 2400) / 0.5 = 7200
        val odds = listOf(1.50, 1.50, 1.50, 1.50)
        val results = BettingMathEngine.calculateProgression(
            currentBank = 10000.0,
            baseStake = 200.0,
            targetProfit = 200.0,
            oddsList = odds,
            mode = CalculatorMode.TARGET_PROFIT
        )

        assertEquals(4, results.size)
        assertEquals(200.0, results[0].stake, 0.1)
        assertEquals(800.0, results[1].stake, 0.1)
        assertEquals(2400.0, results[2].stake, 0.1)
        assertEquals(7200.0, results[3].stake, 0.1)

        // Round 1 net profit is baseStake * (odds - 1) = 200 * 0.5 = 100
        assertEquals(100.0, results[0].netProfit, 0.1)
        // Subsequent rounds recover losses and deliver target profit (200)
        assertEquals(200.0, results[1].netProfit, 0.1)
        assertEquals(200.0, results[2].netProfit, 0.1)
        assertEquals(200.0, results[3].netProfit, 0.1)
    }

    @Test
    fun testBreakEvenKarmentesProgression() {
        // At odds 1.50, base stake 200:
        // Round 1: 200
        // Round 2 (Loss 200): 200 / 0.5 = 400
        // Round 3 (Loss 200+400=600): 600 / 0.5 = 1200
        // Round 4 (Loss 600+1200=1800): 1800 / 0.5 = 3600
        val odds = listOf(1.50, 1.50, 1.50, 1.50)
        val results = BettingMathEngine.calculateProgression(
            currentBank = 10000.0,
            baseStake = 200.0,
            targetProfit = 200.0,
            oddsList = odds,
            mode = CalculatorMode.BREAK_EVEN
        )

        assertEquals(4, results.size)
        assertEquals(200.0, results[0].stake, 0.1)
        assertEquals(400.0, results[1].stake, 0.1)
        assertEquals(1200.0, results[2].stake, 0.1)
        assertEquals(3600.0, results[3].stake, 0.1)

        // In break-even, net profit after round 1 is 0 (break-even)
        assertEquals(0.0, results[1].netProfit, 0.1)
        assertEquals(0.0, results[2].netProfit, 0.1)
        assertEquals(0.0, results[3].netProfit, 0.1)
    }
}
