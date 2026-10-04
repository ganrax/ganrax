package com.example.network.gemini

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class MatchAiAnalysis(
    val summary: String,
    val recommendation: String,
    val winProbabilityEstimate: Double,
    val expectedValue: Double,
    val riskScore: Int, // 1 to 10
    val suggestedRound: Int,
    val keyFactors: List<String>
)

class GeminiSportsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey: String
        get() = BuildConfig.GEMINI_API_KEY.ifBlank { "" }

    suspend fun analyzeMatch(
        sport: String,
        league: String,
        homeTeam: String,
        awayTeam: String,
        market: String,
        tip: String,
        odds: Double,
        round: Int,
        bankroll: Double
    ): MatchAiAnalysis = withContext(Dispatchers.IO) {
        val prompt = """
            Elemezd ezt a sportfogadási mérkőzést a 100 napos 10%-os kamatos kamat és többkörös (1-4 körös) tétkezelő stratégia szempontjából:
            - Sport: $sport
            - Bajnokság: $league
            - Mérkőzés: $homeTeam vs $awayTeam
            - Piac / Tipp: $market -> $tip
            - Szorzó (Odds): $odds
            - Stratégiai kör: $round. kör
            - Teljes Bankroll: $bankroll Ft

            Kérlek, add meg a strukturált elemzést JSON formátumban az alábbi mezőkkel:
            {
              "summary": "Rövid, lényegretörő meccsösszefoglaló és formaelemzés magyarul",
              "recommendation": "Konkrét fogadási javaslat (ajánlott / óvatosan / kerülendő)",
              "winProbabilityEstimate": 65.0, // becsült nyerési esély százalékban (0-100)
              "expectedValue": 5.2, // becsült várható érték %-ban (EV = Prob*Odds - 1)
              "riskScore": 4, // 1 (nagyon biztonságos) - 10 (nagyon kockázatos)
              "suggestedRound": 1, // Hányadik körben optimális megtenni ezt a tippet (1, 2, 3 vagy 4)
              "keyFactors": ["Kulcstényező 1", "Kulcstényező 2", "Kulcstényező 3"]
            }
        """.trimIndent()

        if (apiKey.isBlank()) {
            return@withContext fallbackAnalysis(homeTeam, awayTeam, tip, odds, round)
        }

        try {
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.4)
                }
                put("generationConfig", genConfig)

                // Google Search Grounding for up to date sports knowledge
                val tools = JSONArray().apply {
                    put(JSONObject().put("googleSearch", JSONObject()))
                }
                put("tools", tools)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseStr = response.body?.string() ?: ""
                val resObj = JSONObject(responseStr)
                val candidates = resObj.optJSONArray("candidates")
                val firstCand = candidates?.optJSONObject(0)
                val content = firstCand?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text") ?: ""

                val cleanJson = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val parsed = JSONObject(cleanJson)

                val keyFactorsList = mutableListOf<String>()
                val kfArray = parsed.optJSONArray("keyFactors")
                if (kfArray != null) {
                    for (i in 0 until kfArray.length()) {
                        keyFactorsList.add(kfArray.getString(i))
                    }
                } else {
                    keyFactorsList.addAll(listOf("Forma és statisztika", "Helyszíni előny", "Odds érték"))
                }

                return@withContext MatchAiAnalysis(
                    summary = parsed.optString("summary", "$homeTeam vs $awayTeam formaelemzés kész."),
                    recommendation = parsed.optString("recommendation", "Értékes odds, a stratégia alapján játszható."),
                    winProbabilityEstimate = parsed.optDouble("winProbabilityEstimate", (1.0 / odds) * 100.0),
                    expectedValue = parsed.optDouble("expectedValue", 3.5),
                    riskScore = parsed.optInt("riskScore", if (odds > 1.8) 6 else 3),
                    suggestedRound = parsed.optInt("suggestedRound", round),
                    keyFactors = keyFactorsList
                )
            } else {
                fallbackAnalysis(homeTeam, awayTeam, tip, odds, round)
            }
        } catch (e: Exception) {
            fallbackAnalysis(homeTeam, awayTeam, tip, odds, round)
        }
    }

    suspend fun sendChatMessage(
        conversationHistory: List<ChatMessage>,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "AI Stratéga Válasz (Helyi offline mód):\nA beállított stratégia a 100 napos kamatos kamatra és a többkörös tétkezelésre épül. Minden nap a bank 10%-os növelése a cél. Ha egy tipp nem jön be, a következő körben a képlet: S = (Kitűzött Profit + Elvesztett Tét) / (Odds - 1). Kármentés esetén S = Elvesztett Tét / (Odds - 1). Tartsd a fegyelmet és ne lépd túl a Max Roll (Bank / 20) határt!"
        }

        val systemInstruction = """
            Te vagy a TétMester Pro beépített mesterséges intelligencia sportfogadási szakértője és stratégája.
            A felhasználó egy precíz, matematikai alapú 100 napos tétkezelési stratégiát követ:
            - Napi cél: a bank 10%-os növelése kamatos kamattal.
            - Kezdő bank: tipikusan 10.000 Ft (100. napra ~125M Ft elméleti cél).
            - Min Roll (4 körre osztva): Bank / 49.25
            - Max Roll (napi max kockázat): Bank / 20.0
            - Többkörös tétlépcső (1-4 kör):
              1 alaptétnyi profithoz: S_k = (Alaptét/Profit + Összes addigi veszteség) / (Odds_k - 1)
              Kármentés (nullázó): S_k = (Összes addigi veszteség) / (Odds_k - 1)
            Mindig magyar nyelven, szakértő, fegyelmezett, higgadt és motiváló hangnemben válaszolj.
            Számolj konkrét téteket ha a felhasználó megadja az oddsot és az előző veszteségeit!
        """.trimIndent()

        try {
            val contents = JSONArray()
            // Add previous turns
            for (msg in conversationHistory.takeLast(10)) {
                val role = if (msg.role == "user") "user" else "model"
                contents.put(
                    JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                    }
                )
            }
            // Add latest user message
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )

            val requestJson = JSONObject().apply {
                put("contents", contents)
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                put("generationConfig", JSONObject().put("temperature", 0.7))
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseStr = response.body?.string() ?: ""
                val resObj = JSONObject(responseStr)
                val candidates = resObj.optJSONArray("candidates")
                val firstCand = candidates?.optJSONObject(0)
                val content = firstCand?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                parts?.optJSONObject(0)?.optString("text") ?: "Nem érkezett válasz."
            } else {
                "Hiba történt az AI szolgáltatás hívásakor (${response.code}). Kérlek próbáld újra később."
            }
        } catch (e: Exception) {
            "Hálózati hiba az AI lekérdezés során: ${e.localizedMessage}"
        }
    }

    private fun fallbackAnalysis(
        homeTeam: String,
        awayTeam: String,
        tip: String,
        odds: Double,
        round: Int
    ): MatchAiAnalysis {
        val impliedProb = ((1.0 / odds) * 100.0).coerceIn(10.0, 95.0)
        val risk = when {
            odds < 1.40 -> 2
            odds < 1.65 -> 4
            odds < 1.90 -> 6
            else -> 8
        }
        val rec = when {
            round == 1 && odds <= 1.70 -> "Kiváló 1. körös választás, jó érték arány."
            round > 2 -> "Magasabb körben válassz alacsonyabb oddsot (1.30 - 1.55) a kockázat minimalizálására."
            else -> "Fegyelmezett téttel játszható a stratégia szerint."
        }
        return MatchAiAnalysis(
            summary = "$homeTeam vs $awayTeam: A $tip opció $odds szorzóval ${impliedProb.toInt()}% valószínűséggel kecsegtet a matematikai modell szerint.",
            recommendation = rec,
            winProbabilityEstimate = impliedProb,
            expectedValue = 2.8,
            riskScore = risk,
            suggestedRound = if (odds > 1.70) 1 else 2,
            keyFactors = listOf(
                "Matematikai odds szorzó: $odds",
                "Kör stratégiai kockázat szintje: $round. kör",
                "Ajánlott fegyelmezett bankroll menedzsment"
            )
        )
    }
}
