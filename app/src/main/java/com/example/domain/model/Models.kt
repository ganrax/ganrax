package com.example.domain.model

enum class DayStatus(val displayName: String) {
    PENDING("Függőben"),
    COMPLETED("Teljesítve"),
    FAILED("Vesztes"),
    IN_PROGRESS("Folyamatban");

    companion object {
        fun fromString(value: String): DayStatus = entries.find { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

enum class MatchStatus(val displayName: String) {
    PENDING("Függőben"),
    LIVE("Élőben"),
    WON("Nyertes"),
    LOST("Vesztes"),
    VOID("Törölve/Érvénytelen");

    companion object {
        fun fromString(value: String): MatchStatus = entries.find { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

enum class CalculatorMode(val title: String, val subtitle: String) {
    TARGET_PROFIT("1 Alaptétnyi Profithoz", "Minden körben a kitűzött nyereséget hozzuk ki"),
    BREAK_EVEN("Kármentés (Nullázó)", "Mennyit tegyek fel, hogy nullára jöjjek ki?")
}

data class RoundStakeResult(
    val round: Int,
    val odds: Double,
    val stake: Double,
    val totalInvested: Double,
    val potentialReturn: Double,
    val netProfit: Double,
    val bankPercentage: Double
)

data class PresetOddsRow(
    val odds: Double,
    val round2Stake: Double,
    val round3Stake: Double,
    val round4Stake: Double
)

data class StrategyStats(
    val totalDays: Int = 100,
    val completedDays: Int = 0,
    val failedDays: Int = 0,
    val currentStreak: Int = 0,
    val startingBank: Double = 10000.0,
    val currentBank: Double = 10000.0,
    val totalTargetDay100: Double = 125278294.0,
    val totalProfitRealized: Double = 0.0,
    val totalMatches: Int = 0,
    val wonMatches: Int = 0,
    val lostMatches: Int = 0,
    val winRate: Double = 0.0,
    val roi: Double = 0.0
)

data class AppUpdateInfo(
    val currentVersionCode: Int = 1,
    val currentVersionName: String = "1.0.0",
    val latestVersionCode: Int = 2,
    val latestVersionName: String = "1.1.0",
    val releaseNotes: String = "Új funkciók: Élő odds kalkulátor, intelligens kármentés javaslatok, finomhangolt 100 napos kamatos kamat görbe és javított mérkőzéskövetés.",
    val downloadUrl: String = "https://example.com/tetmester_pro_latest.apk",
    val isUpdateAvailable: Boolean = false,
    val fileSizeMb: Double = 18.5,
    val releaseDate: String = "2026-10-04"
)

data class ParsedTelegramAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String = "",
    val strategyName: String = "",
    val league: String = "",
    val homeTeam: String = "",
    val awayTeam: String = "",
    val matchName: String = "",
    val googleSearchUrl: String = "",
    val flashscoreSearchUrl: String = "",
    val timer: String = "",
    val score: String = "",
    val corners: String = "",
    val momentum: String = "",
    val attacks: String = "",
    val dangerousAttacks: String = "",
    val shotsOnTarget: String = "",
    val possession: String = "",
    val liveOdds1X2: String = "",
    val bttsOdds: String = "",
    val overUnderOdds: String = "",
    val rawText: String = ""
)
