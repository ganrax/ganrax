package com.example.domain.util

import com.example.data.local.entity.BetMatchEntity

data class ResolvedMatchDisplay(
    val homeTeam: String,
    val awayTeam: String,
    val fullMatchTitle: String,
    val strategyName: String,
    val isGenericTeams: Boolean
)

object MatchDisplayHelper {

    fun resolve(match: BetMatchEntity): ResolvedMatchDisplay {
        return resolve(
            homeTeam = match.homeTeam,
            awayTeam = match.awayTeam,
            tip = match.tip,
            notes = match.notes
        )
    }

    fun resolve(
        homeTeam: String,
        awayTeam: String,
        tip: String,
        notes: String = ""
    ): ResolvedMatchDisplay {
        var home = homeTeam.trim()
        var away = awayTeam.trim()

        // 1. If homeTeam contains " vs " or " - " and awayTeam is empty or generic
        val isAwayGeneric = away.isBlank() ||
                away.equals("Vendég", ignoreCase = true) ||
                away.equals("Vendég csapat", ignoreCase = true) ||
                away.equals("Away", ignoreCase = true)

        if (isAwayGeneric) {
            val splitPattern = when {
                home.contains(" vs ", ignoreCase = true) -> Regex("""\s+vs\s+""", RegexOption.IGNORE_CASE)
                home.contains(" vs. ", ignoreCase = true) -> Regex("""\s+vs\.\s+""", RegexOption.IGNORE_CASE)
                home.contains(" - ") -> Regex("""\s+-\s+""")
                home.contains(" – ") -> Regex("""\s+–\s+""")
                home.contains(" — ") -> Regex("""\s+—\s+""")
                else -> null
            }
            if (splitPattern != null) {
                val parts = home.split(splitPattern)
                if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    home = parts[0].trim()
                    away = parts[1].trim()
                }
            }
        }

        // 2. If both home and away are generic or blank, try to extract from notes
        val isHomeGeneric = home.isBlank() ||
                home.equals("Hazai", ignoreCase = true) ||
                home.equals("Hazai csapat", ignoreCase = true) ||
                home.equals("Home", ignoreCase = true)

        if (isHomeGeneric && isAwayGeneric) {
            if (notes.isNotBlank()) {
                val segments = notes.split("|").map { it.trim() }
                for (seg in segments) {
                    val splitPattern = when {
                        seg.contains(" vs ", ignoreCase = true) -> Regex("""\s+vs\s+""", RegexOption.IGNORE_CASE)
                        seg.contains(" vs. ", ignoreCase = true) -> Regex("""\s+vs\.\s+""", RegexOption.IGNORE_CASE)
                        seg.contains(" - ") && !seg.contains("Mentve") -> Regex("""\s+-\s+""")
                        seg.contains(" – ") && !seg.contains("Mentve") -> Regex("""\s+–\s+""")
                        else -> null
                    }
                    if (splitPattern != null) {
                        val parts = seg.split(splitPattern)
                        if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                            home = parts[0].trim()
                            away = parts[1].trim()
                            break
                        }
                    }
                }
            }
        }

        // Clean any standings like (4th) or (1st)
        home = home.replace(Regex("""\(\s*\d+(?:st|nd|rd|th)?\s*\)""", RegexOption.IGNORE_CASE), "").trim()
        away = away.replace(Regex("""\(\s*\d+(?:st|nd|rd|th)?\s*\)""", RegexOption.IGNORE_CASE), "").trim()

        val isStillGeneric = (home.isBlank() || home.equals("Hazai", ignoreCase = true) || home.equals("Hazai csapat", ignoreCase = true)) &&
                (away.isBlank() || away.equals("Vendég", ignoreCase = true) || away.equals("Vendég csapat", ignoreCase = true))

        val displayHome = home.ifBlank { "Hazai csapat" }
        val displayAway = away.ifBlank { "Vendég csapat" }
        val title = if (displayAway.isNotBlank() && !isStillGeneric) {
            "$displayHome vs $displayAway"
        } else if (!isStillGeneric) {
            displayHome
        } else {
            "Hazai csapat vs Vendég csapat"
        }

        val strategy = tip.ifBlank { "Telegram Stratégia" }

        return ResolvedMatchDisplay(
            homeTeam = displayHome,
            awayTeam = displayAway,
            fullMatchTitle = title,
            strategyName = strategy,
            isGenericTeams = isStillGeneric
        )
    }
}
