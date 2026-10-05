package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.BetMatchEntity
import com.example.data.repository.BettingRepository
import com.example.domain.model.ParsedTelegramAlert
import com.example.domain.util.TelegramAlertParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TelegramParserViewModel(
    private val repository: BettingRepository? = null
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _parsedAlerts = MutableStateFlow<List<ParsedTelegramAlert>>(emptyList())
    val parsedAlerts: StateFlow<List<ParsedTelegramAlert>> = _parsedAlerts.asStateFlow()

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Load default sample if initial list is empty
        loadSampleData(autoParse = true)
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun parse() {
        val text = _inputText.value
        if (text.isBlank()) {
            _statusMessage.value = "Kérlek illessz be legalább egy Telegram értesítést!"
            return
        }

        _isParsing.value = true
        val results = TelegramAlertParser.parseMessages(text)
        _parsedAlerts.value = results
        _isParsing.value = false

        _statusMessage.value = if (results.isNotEmpty()) {
            "Sikeresen kinyerve: ${results.size} mérkőzés!"
        } else {
            "Nem sikerült mérkőzést azonosítani a megadott szövegben."
        }
    }

    fun loadSampleData(autoParse: Boolean = false) {
        val sample = """
[2026. 10. 04. 20:52] ⚽️ ganrax Alerts: 🔔 ⚡Second Half Action Ready

🇮🇱 Israel Liga Bet South 
Bnei Yehud vs Maccabi Amishav Petah Tikva
🟥🟩🟥🟩🟥 - 🟩🟩🟨🟩🟥

Timer: 50'
Last Goal: Away at 20' (30 minutes ago)

Goals: 0 - 1
Corners: 3 - 1
Momentum: 105 - 53
Shots On Target: 5 - 5
Shots Off Target: 11 - 2
Attacks: 69 - 56
Dangerous Attacks: 39 - 23
Yellow Cards: 2 - 0
Red Cards: 0 - 0
Penalties: 0 - 0
Substitutions: 0 - 1
Possession %: 66 - 34

Goals Scored Avg. (Last 5): 3 - 1.8
Corners For Avg. (Last 5): 7 - 4.8

1X2 Pre-Match Odds:
2.55 3.80 2.20
1X2 Live Odds:
3.75 3.40 1.91
Over/Under 1.50 Odds:
1.14 5.00
Over/Under 2.50 Odds:
1.73 2.00
Both Teams To Score:
1.36 3.00

🎯 Strike Rate: 91% overall (329 picks) · N/A league (1 pick)


[2026. 10. 04. 21:11] ⚽️ ganrax Alerts: 🔔 Both Teams to Score (Favorite conceded first)

🇪🇸 Spain Primera Division RFEF Group 2 (4th vs 19th)
Real Zaragoza vs Teruel
🟥🟩🟩🟩🟩 - 🟨🟥🟥🟥🟨

Timer: 11'
Last Goal: Away at 6' (5 minutes ago)

Goals: 0 - 1
Corners: 0 - 0
Momentum: 20 - 30
Shots On Target: 1 - 1
Shots Off Target: 0 - 0
Attacks: 6 - 6
Dangerous Attacks: 2 - 4
Yellow Cards: 0 - 1
Red Cards: 0 - 0
Penalties: 0 - 1
Substitutions: 0 - 0
Possession %: 31 - 69

Goals Scored Avg. (Last 5): 1.6 - 0.2
Corners For Avg. (Last 5): 6.8 - 3.8

1X2 Pre-Match Odds:
1.33 4.00 8.00
1X2 Live Odds:
2.40 3.40 2.75
Over/Under 1.50 Odds:
1.07 7.50
Over/Under 2.50 Odds:
1.36 3.00
Both Teams To Score:
1.17 4.50

🎯 Strike Rate: 91% overall (617 picks) · N/A league (1 pick)
        """.trimIndent()

        _inputText.value = sample
        if (autoParse) {
            _parsedAlerts.value = TelegramAlertParser.parseMessages(sample)
        }
    }

    fun clearAll() {
        _inputText.value = ""
        _parsedAlerts.value = emptyList()
        _statusMessage.value = null
    }

    fun copyFormattedListToClipboard(context: Context) {
        val alerts = _parsedAlerts.value
        if (alerts.isEmpty()) {
            Toast.makeText(context, "Nincs másolható mérkőzés!", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.appendLine("⚽️ TÉTMESTER PRO - KINYERT TELEGRAM ÉRTESÍTÉSEK")
        sb.appendLine("--------------------------------------------------")

        alerts.forEachIndexed { index, alert ->
            sb.appendLine("\n### ${index + 1}. Mérkőzés")
            if (alert.strategyName.isNotBlank()) sb.appendLine("• Stratégia: ${alert.strategyName}")
            if (alert.league.isNotBlank()) sb.appendLine("• Bajnokság: ${alert.league}")
            sb.appendLine("• Csapatok: ${alert.matchName}")
            if (alert.score.isNotBlank()) sb.appendLine("• Állás: ${alert.score} (${alert.timer})")
            sb.appendLine("• Google Keresés: ${alert.googleSearchUrl}")
        }

        sb.appendLine("\n--------------------------------------------------")
        sb.appendLine("Gyors linkek összesítő:")
        alerts.forEachIndexed { index, alert ->
            sb.appendLine("${index + 1}. ${alert.matchName} -> ${alert.googleSearchUrl}")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Telegram Kinyert Mérkőzések", sb.toString())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, "Formázott mérkőzéslista és linkek másolva a vágólapra!", Toast.LENGTH_LONG).show()
    }

    fun copySingleUrl(context: Context, url: String, label: String = "Link") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label másolva a vágólapra!", Toast.LENGTH_SHORT).show()
    }

    fun addMatchToTracker(alert: ParsedTelegramAlert, context: Context, roundNumber: Int = 1) {
        val repo = repository ?: return
        viewModelScope.launch {
            try {
                val config = repo.strategyConfig.first()
                val baseStake = config?.baseStake ?: 203.0
                val roundStake = when (roundNumber) {
                    1 -> baseStake
                    2 -> (baseStake * 3.5).toInt().toDouble()
                    3 -> (baseStake * 11.25).toInt().toDouble()
                    4 -> (baseStake * 33.5).toInt().toDouble()
                    else -> baseStake
                }

                val odds = alert.liveOdds1X2.split(" ").firstOrNull()?.toDoubleOrNull() ?: 1.40

                val entity = BetMatchEntity(
                    homeTeam = alert.homeTeam.ifBlank { "Hazai csapat" },
                    awayTeam = alert.awayTeam.ifBlank { "Vendég csapat" },
                    league = alert.league.ifBlank { "Ismeretlen liga" },
                    odds = odds,
                    stake = roundStake,
                    roundNumber = roundNumber,
                    matchTime = alert.timestamp.ifBlank { "Élő" },
                    liveMinute = alert.timer.ifBlank { null },
                    market = if (alert.strategyName.contains("Both Teams", ignoreCase = true)) "BTTS" else "1X2",
                    tip = if (alert.strategyName.contains("Both Teams", ignoreCase = true)) "Mindkét csapat szerez gólt" else "1X2",
                    notes = "${alert.strategyName} | Google: ${alert.googleSearchUrl}",
                    status = "LIVE"
                )

                repo.insertMatch(entity)
                Toast.makeText(context, "${alert.matchName} hozzáadva a Meccsekhez (${roundStake.toInt()} Ft tét)! ", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Hiba a meccs mentésekor: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    class Factory(private val repository: BettingRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TelegramParserViewModel(repository) as T
        }
    }
}
