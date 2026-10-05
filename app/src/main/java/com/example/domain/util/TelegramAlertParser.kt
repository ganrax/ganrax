package com.example.domain.util

import com.example.domain.model.ParsedTelegramAlert
import java.net.URLEncoder

object TelegramAlertParser {

    fun parseMessages(rawText: String): List<ParsedTelegramAlert> {
        if (rawText.isBlank()) return emptyList()

        // Split text by alert headers or bracketed timestamps
        val messageBlocks = splitIntoAlertBlocks(rawText)

        return messageBlocks.mapNotNull { block ->
            parseSingleAlert(block.trim())
        }
    }

    private fun splitIntoAlertBlocks(text: String): List<String> {
        val blocks = mutableListOf<String>()
        val lines = text.lines()
        val currentBlock = StringBuilder()

        val isHeaderRegex = Regex("""^(\[\d{4}\.\s*\d{1,2}\.\s*\d{1,2}\.\s*\d{1,2}:\d{2}\]|⚽️\s*ganrax\s*Alerts:|🔔|⚡)""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[202") || (trimmed.contains("ganrax Alerts:", ignoreCase = true) && currentBlock.isNotEmpty())) {
                if (currentBlock.isNotBlank()) {
                    blocks.add(currentBlock.toString())
                    currentBlock.clear()
                }
            }
            currentBlock.appendLine(line)
        }

        if (currentBlock.isNotBlank()) {
            blocks.add(currentBlock.toString())
        }

        // If no timestamp splits were found, try fallback block detection
        if (blocks.size <= 1 && text.contains(" vs ", ignoreCase = true)) {
            val vsLinesCount = lines.count { it.contains(" vs ", ignoreCase = true) }
            if (vsLinesCount > 1) {
                // Multi-match splitting by vs proximity
                return splitByMultipleMatches(lines)
            }
        }

        return if (blocks.isNotEmpty()) blocks else listOf(text)
    }

    private fun splitByMultipleMatches(lines: List<String>): List<String> {
        val blocks = mutableListOf<String>()
        var current = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[20") || trimmed.startsWith("🔔") || trimmed.startsWith("⚡")) {
                if (current.isNotBlank()) {
                    blocks.add(current.toString())
                    current = StringBuilder()
                }
            }
            current.appendLine(line)
        }
        if (current.isNotBlank()) {
            blocks.add(current.toString())
        }
        return blocks
    }

    fun parseSingleAlert(text: String): ParsedTelegramAlert? {
        if (text.isBlank()) return null

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        var timestamp = ""
        var strategyName = ""
        var league = ""
        var homeTeam = ""
        var awayTeam = ""
        var matchName = ""
        var timer = ""
        var score = ""
        var corners = ""
        var momentum = ""
        var attacks = ""
        var dangerousAttacks = ""
        var shotsOnTarget = ""
        var possession = ""
        var liveOdds1X2 = ""
        var bttsOdds = ""
        var overUnderOdds = ""

        // 1. Extract Timestamp if present [YYYY. MM. DD. HH:MM]
        val timeMatch = Regex("""\[(\d{4}\.\s*\d{1,2}\.\s*\d{1,2}\.\s*\d{1,2}:\d{2})\]""").find(text)
        if (timeMatch != null) {
            timestamp = timeMatch.groupValues[1]
        }

        // 2. Find Strategy Header
        for (line in lines) {
            if (line.contains("ganrax Alerts:", ignoreCase = true) || line.startsWith("🔔") || line.startsWith("⚡")) {
                val cleaned = line
                    .replace(Regex("""^\[\d{4}\.\s*\d{1,2}\.\s*\d{1,2}\.\s*\d{1,2}:\d{2}\]"""), "")
                    .replace("⚽️ ganrax Alerts:", "")
                    .replace("⚽️ Alerts:", "")
                    .trim()
                if (cleaned.isNotBlank()) {
                    strategyName = cleaned
                    break
                }
            }
        }

        if (strategyName.isBlank()) {
            // Fallback strategy from first line if it looks like an alert
            val firstLine = lines.firstOrNull() ?: ""
            if (firstLine.contains("Action Ready", ignoreCase = true) ||
                firstLine.contains("Both Teams", ignoreCase = true) ||
                firstLine.contains("Over", ignoreCase = true) ||
                firstLine.contains("Under", ignoreCase = true)) {
                strategyName = firstLine
            } else {
                strategyName = "🔔 ganrax Élő Tét Értesítés"
            }
        }

        // 3. Find Match line (Home vs Away)
        var vsLineIndex = -1
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains(" vs ", ignoreCase = true) &&
                !line.contains("Goals:", ignoreCase = true) &&
                !line.contains("Odds", ignoreCase = true) &&
                !line.startsWith("(") &&
                !line.contains("Strike Rate", ignoreCase = true)) {

                // Found the teams line!
                matchName = line
                val parts = line.split(Regex("""\s+vs\s+""", RegexOption.IGNORE_CASE))
                if (parts.size >= 2) {
                    homeTeam = parts[0].trim()
                    awayTeam = parts[1].trim()
                }
                vsLineIndex = i
                break
            }
        }

        // If no " vs " line, check for team pattern or return basic parsed object if matchName found
        if (matchName.isBlank()) {
            // Check for dash separated teams
            for (i in lines.indices) {
                val line = lines[i]
                if (line.contains(" - ") &&
                    !line.startsWith("Goals:") &&
                    !line.startsWith("Timer:") &&
                    !line.startsWith("Corners:") &&
                    !line.startsWith("Momentum:") &&
                    !line.startsWith("Shots") &&
                    !line.startsWith("Attacks:") &&
                    !line.startsWith("Possession") &&
                    !line.contains("🟥") && !line.contains("🟩") && !line.contains("🟨")) {
                    val parts = line.split(" - ")
                    if (parts.size == 2 && parts[0].length > 2 && parts[1].length > 2) {
                        homeTeam = parts[0].trim()
                        awayTeam = parts[1].trim()
                        matchName = "$homeTeam vs $awayTeam"
                        vsLineIndex = i
                        break
                    }
                }
            }
        }

        if (matchName.isBlank()) {
            return null // No match participants identified
        }

        // 4. Find League / Country (usually the line right above the teams line)
        if (vsLineIndex > 0) {
            val candidate = lines[vsLineIndex - 1]
            if (!candidate.contains("ganrax Alerts:") &&
                !candidate.contains("Timer:") &&
                !candidate.startsWith("🟥") && !candidate.startsWith("🟩")) {
                league = candidate
            }
        }

        if (league.isBlank()) {
            // Search for emoji country flag or league keywords
            for (line in lines) {
                if (line.any { it.code in 0x1F1E6..0x1F1FF } || // Country flag unicode range
                    line.contains("League", ignoreCase = true) ||
                    line.contains("Liga", ignoreCase = true) ||
                    line.contains("Division", ignoreCase = true) ||
                    line.contains("Cup", ignoreCase = true) ||
                    line.contains("Premier", ignoreCase = true) ||
                    line.contains("Serie", ignoreCase = true)) {
                    if (line != matchName && !line.contains("Strike Rate")) {
                        league = line
                        break
                    }
                }
            }
        }

        // 5. Extract In-Game Stats & Odds
        for (i in lines.indices) {
            val line = lines[i]
            when {
                line.startsWith("Timer:", ignoreCase = true) -> timer = line.removePrefix("Timer:").trim()
                line.startsWith("Goals:", ignoreCase = true) -> score = line.removePrefix("Goals:").trim()
                line.startsWith("Corners:", ignoreCase = true) -> corners = line.removePrefix("Corners:").trim()
                line.startsWith("Momentum:", ignoreCase = true) -> momentum = line.removePrefix("Momentum:").trim()
                line.startsWith("Attacks:", ignoreCase = true) -> attacks = line.removePrefix("Attacks:").trim()
                line.startsWith("Dangerous Attacks:", ignoreCase = true) -> dangerousAttacks = line.removePrefix("Dangerous Attacks:").trim()
                line.startsWith("Shots On Target:", ignoreCase = true) -> shotsOnTarget = line.removePrefix("Shots On Target:").trim()
                line.startsWith("Possession %:", ignoreCase = true) -> possession = line.removePrefix("Possession %:").trim()

                line.contains("1X2 Live Odds:", ignoreCase = true) && i + 1 < lines.size -> {
                    liveOdds1X2 = lines[i + 1]
                }
                line.contains("Both Teams To Score:", ignoreCase = true) && i + 1 < lines.size -> {
                    bttsOdds = lines[i + 1]
                }
                line.contains("Over/Under 2.50 Odds:", ignoreCase = true) && i + 1 < lines.size -> {
                    overUnderOdds = "O/U 2.5: ${lines[i + 1]}"
                }
                line.contains("Over/Under 1.50 Odds:", ignoreCase = true) && i + 1 < lines.size && overUnderOdds.isBlank() -> {
                    overUnderOdds = "O/U 1.5: ${lines[i + 1]}"
                }
            }
        }

        // 6. Generate Google Search and Flashscore Links
        val encodedQuery = try {
            URLEncoder.encode(matchName, "UTF-8")
        } catch (e: Exception) {
            matchName.replace(" ", "+")
        }

        val encodedFlashscore = try {
            URLEncoder.encode("$matchName flashscore", "UTF-8")
        } catch (e: Exception) {
            "${matchName.replace(" ", "+")}+flashscore"
        }

        val googleSearchUrl = "https://www.google.com/search?q=$encodedQuery"
        val flashscoreSearchUrl = "https://www.google.com/search?q=$encodedFlashscore"

        return ParsedTelegramAlert(
            timestamp = timestamp,
            strategyName = strategyName,
            league = league,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            matchName = matchName,
            googleSearchUrl = googleSearchUrl,
            flashscoreSearchUrl = flashscoreSearchUrl,
            timer = timer,
            score = score,
            corners = corners,
            momentum = momentum,
            attacks = attacks,
            dangerousAttacks = dangerousAttacks,
            shotsOnTarget = shotsOnTarget,
            possession = possession,
            liveOdds1X2 = liveOdds1X2,
            bttsOdds = bttsOdds,
            overUnderOdds = overUnderOdds,
            rawText = text
        )
    }
}
