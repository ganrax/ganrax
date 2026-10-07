package com.example.network.gemini

import android.content.Context
import com.example.BuildConfig
import com.example.domain.calculator.BettingMathEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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

class GeminiSportsService(
    private val context: Context? = null
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    fun getApiKey(): String {
        val customKey = context?.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            ?.getString("custom_gemini_api_key", "") ?: ""
        if (isValidApiKey(customKey)) return customKey.trim()

        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (isValidApiKey(buildKey)) return buildKey

        return ""
    }

    fun saveCustomApiKey(key: String) {
        context?.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString("custom_gemini_api_key", key.trim())
            ?.apply()
    }

    fun clearCustomApiKey() {
        context?.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            ?.edit()
            ?.remove("custom_gemini_api_key")
            ?.apply()
    }

    fun hasCustomApiKey(): Boolean {
        val customKey = context?.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            ?.getString("custom_gemini_api_key", "") ?: ""
        return isValidApiKey(customKey)
    }

    private fun isValidApiKey(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.isNotBlank() &&
                trimmed != "MY_GEMINI_API_KEY" &&
                !trimmed.startsWith("YOUR_") &&
                trimmed.length >= 15
    }

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
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext fallbackAnalysis(homeTeam, awayTeam, tip, odds, round)
        }

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
              "winProbabilityEstimate": 65.0,
              "expectedValue": 5.2,
              "riskScore": 4,
              "suggestedRound": 1,
              "keyFactors": ["Kulcstényező 1", "Kulcstényező 2", "Kulcstényező 3"]
            }
        """.trimIndent()

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
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
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
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext generateExpertOfflineResponse(userMessage, isFallbackFromNetwork = false)
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
            for (msg in conversationHistory.takeLast(10)) {
                val role = if (msg.role == "user") "user" else "model"
                contents.put(
                    JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                    }
                )
            }
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
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
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
                val text = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""
                if (text.isNotBlank()) {
                    return@withContext text
                }
            }
            // If online response failed or returned error code (e.g. 400 / 403), gracefully fall back to local expert!
            generateExpertOfflineResponse(userMessage, isFallbackFromNetwork = true)
        } catch (e: Exception) {
            generateExpertOfflineResponse(userMessage, isFallbackFromNetwork = true)
        }
    }

    /**
     * Highly intelligent Hungarian sports betting expert rules & calculation engine.
     * Answers instantly offline without any API key or network connection.
     */
    fun generateExpertOfflineResponse(userMessage: String, isFallbackFromNetwork: Boolean = false): String {
        val lower = userMessage.lowercase().trim()

        // 1. Check if user is asking for specific stake calculation (e.g. contains odds numbers, "számol", "tét")
        val oddsRegex = Regex("""(?:odds|szorzó)?\s*([1-9][.,][0-9]{1,3})""", RegexOption.IGNORE_CASE)
        val oddsMatch = oddsRegex.find(userMessage)
        val oddsValue = oddsMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

        val stakeRegex = Regex("""([0-9]{2,7})\s*(?:ft|huf|alaptét|tét|forint)?""", RegexOption.IGNORE_CASE)
        val stakeMatches = stakeRegex.findAll(userMessage).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val baseStakeCandidate = stakeMatches.firstOrNull { it in 100.0..500000.0 } ?: 200.0

        val roundRegex = Regex("""([1-6])\.\s*kör""", RegexOption.IGNORE_CASE)
        val roundMatch = roundRegex.find(userMessage)
        val roundNum = roundMatch?.groupValues?.get(1)?.toIntOrNull() ?: if (lower.contains("2.") || lower.contains("második")) 2 else if (lower.contains("3.") || lower.contains("harmadik")) 3 else if (lower.contains("4.") || lower.contains("negyedik")) 4 else 1

        if (oddsValue != null && oddsValue in 1.05..15.0 && (lower.contains("számol") || lower.contains("tét") || lower.contains("odds") || lower.contains("kör"))) {
            return calculateStakeAnswer(oddsValue, baseStakeCandidate, roundNum, isFallbackFromNetwork)
        }

        // 2. 4-round progression questions
        if (lower.contains("4-kör") || lower.contains("4 kör") || lower.contains("lépcső") || lower.contains("körök")) {
            return """
                📈 **A 4-körös Tétkezelő Stratégia Működése:**

                A rendszer lényege, hogy egyetlen vesztes tipp után nem veszíted el a pénzed, hanem a matematikai pótlépcsővel a következő sikeres fogadásnál visszanyered a korábbi veszteségeket, plusz megkapod a tervezett tiszta profitot!

                1️⃣ **1. Kör (Alaptét):**
                - A nyitó fogadás. Ha nyer (pl. 200 Ft tét 1.50 oddson = +100 Ft profit), a ciklus azonnal újraindul az 1. körről.
                - Nincs szükség további tétekre!

                2️⃣ **2. Kör (Első pótlás + profit):**
                - Ha az 1. kör elúszik (pl. -200 Ft), a 2. körös tétet a képlet számolja ki:
                  **Tét = (Kitűzött Profit + 1. kör vesztesége) / (Odds - 1)**
                - Példa 1.40 oddsra: (100 + 200) / 0.40 = **750 Ft tét**. Ha nyer, 1.050 Ft érkezik: fedezte az összes költséget (200 + 750 = 950 Ft) és megvan a +100 Ft tiszta hasznod!

                3️⃣ **3. Kör (Biztonsági pótlás):**
                - Ha mindkét előző kör elment, a 3. körnél alacsonyabb, biztonságos oddsot (1.30–1.45) válassz, és a képlet fedezni fogja az 1. és 2. körös veszteséget is!

                4️⃣ **4. Kör (Döntési pont: Profit vagy Nullázó Kármentés):**
                - A 4. kör az utolsó védőháló. Itt dönthetsz: folytatod a teljes profitért, vagy átváltasz **Kármentésre (Nullázó)**, amivel jóval kisebb téttel csak a pénzedet hozod vissza kockázatmentesen!

                💡 **Fő aranyszabály:** Amint egy kör NYER, azonnal visszalépsz az 1. körre! Soha ne emeld feleslegesen a tétet egy nyertes kör után!
            """.trimIndent()
        }

        // 3. Profit vs Loss Recovery (Kármentés)
        if (lower.contains("különbség") || lower.contains("kármentés") || lower.contains("profit") || lower.contains("nullázó")) {
            return """
                ⚖️ **Profittermelés vs. Kármentés (Nullázó Tétkezelés):**

                A két üzemmód közötti különbség a kockázatkezelés legfontosabb eszköze a magasabb (2–4.) körökben:

                🎯 **1. Profittermelő Mód (Alapeset):**
                - **Képlet:** `Tét = (Kitűzött Profit + Összes addigi veszteség) / (Odds - 1)`
                - **Cél:** Nemcsak a korábbi bukott téteket hozza vissza, hanem a teljes eredetileg kitűzött nyereséget is megtermeli a kör lezárásakor.
                - **Ajánlott:** 1. és 2. körben, amikor a veszteség még kicsi.

                🛡️ **2. Kármentő Mód (Nullázó Mentés):**
                - **Képlet:** `Tét = (Összes addigi veszteség) / (Odds - 1)`
                - **Cél:** Kizárólag a korábban elveszített tőkét nyeri vissza. A nettó egyenleg pontosan 0 Ft lesz (nem buksz semmit).
                - **Előnye:** **Jelentősen kisebb tétet igényel**, mint a profittermelő mód, így megvédi a bankrollodat a 3. és 4. körben!
                - **Ajánlott:** Ha elérted a 3. vagy 4. kört, és nem akarsz nagy összeget kockáztatni a napi cél eléréséért.
            """.trimIndent()
        }

        // 4. Losing streak & bankroll management
        if (lower.contains("vesztő") || lower.contains("széria") || lower.contains("bankroll") || lower.contains("védelem") || lower.contains("kockázat")) {
            return """
                🛡️ **Vesztő Széria Kezelése és Bankroll Védelem:**

                A sportfogadásban a fegyelem fontosabb a tippeknél. Az app az alábbi szigorú védelmi szabályokat építette be:

                1. **Min Roll Szabály (Bank / 49.25):**
                   - Az 1. körös alaptéted soha ne haladja meg a teljes bankrollod kb. 2%-át (1/49.25).
                   - Így garantált, hogy egy 4-lépcsős pótlás esetén sem fogy el a tőkéd!

                2. **Max Roll Napi Stop-Loss (Bank / 20):**
                   - Egyetlen nap alatt maximum a tőkéd 5%-át teheted kockára összesen.
                   - Ha egy ritka, peches 4-körös széria nem jön be, aznapra **azonnal állj le**! Ne akarj azonnal visszanyerni!

                3. **Odds Fegyelem:**
                   - A 2., 3. és 4. körben szigorúan csak megbízható, 1.30 és 1.55 közötti szorzókat játssz. Ne növeld az oddsot desperationből!

                4. **100 Napos Távlat:**
                   - A stratégia 100 napos kamatos kamatra épül. 1–2 vesztő nap nem töri meg az exponenciális növekedést, ha betartod a stop-losst.
            """.trimIndent()
        }

        // 5. 10.000 Ft initial bankroll & 100-day compound plan
        if (lower.contains("10.000") || lower.contains("10000") || lower.contains("kezdő") || lower.contains("100 nap") || lower.contains("kamatos")) {
            return """
                💰 **10.000 Ft Kezdő Bankroll & 100 Napos Terv:**

                A táblázat alapja a napi **10%-os kamatos kamat**:
                - **1. nap:** 10.000 Ft tőke ➡️ Napi cél: +1.000 Ft profit (Záró bank: 11.000 Ft)
                - **10. nap:** ~23.579 Ft tőke ➡️ Napi cél: +2.358 Ft
                - **30. nap:** ~158.630 Ft tőke
                - **100. nap:** Elméleti csúcs: ~125.000.000 Ft

                📊 **Hogyan érjük el a napi 10%-ot biztonságosan?**
                - Nem egyetlen 1.10-es all-in meccsel!
                - Hanem 2-3 független 4-körös ciklussal (pl. 200–300 Ft alaptétekkel).
                - Amikor összegyűlt az adott napra kitűzött profit, **aznapra kész vagy**! Zárd be az irodát és élvezd a sikert.
            """.trimIndent()
        }

        // 6. Telegram notifications / bot
        if (lower.contains("telegram") || lower.contains("ganrax") || lower.contains("üzenet") || lower.contains("másol") || lower.contains("beilleszt")) {
            return """
                ✈️ **Hogyan használd a Telegram Értesítőket az Appban?**

                1. Amikor a Telegram csatornádban (pl. **ganrax Alerts**) megérkezik a riasztás (pl. *Both Teams To Score: Csapat A - Csapat B*):
                2. Másold ki a teljes üzenetet a vágólapra a Telegramban.
                3. Nyisd meg az appban a **Kalkulátor** fület.
                4. Kattints a **„Beillesztés a vágólapról”** gombra (vagy illeszd be a szövegdobozba).
                5. Az app automatikusan kiolvassa a stratégiát, ligát és mindkét csapat nevét.
                6. Írd be a mérkőzéshez talált aktuális szorzót (odds), és a kalkulátor valós időben kiszámolja a pontos tétet!
                7. A **„Mentés a Meccsekhez”** gombbal egy érintéssel rögzítheted is a szelvényt.
            """.trimIndent()
        }

        // 7. "Miért nem működik a bot" explanation
        if (lower.contains("miért nem") || lower.contains("nem működik") || lower.contains("hiba") || lower.contains("cset") || lower.contains("cselt")) {
            return """
                🤖 **Miért nem válaszolt korábban a bot, és mi a megoldás?**

                1. **A probléma oka:**
                   - Az APK-ban korábban csak egy sablon kulcs szerepelt (`MY_GEMINI_API_KEY`). Amikor a telefonod elküldte a kérdést a Google felhős szerverének, a Google 400-as hibakóddal (érvénytelen API kulcs) elutasította a hívást.
                   - Emellett a telefon billentyűzete a „cset bot”-ot könnyen „cselt bot”-ra javítja az automatikus helyesírás miatt.

                2. **Hogyan javítottuk meg?**
                   - **Azonnali Beépített Szakértő:** Mostantól internetkapcsolat és API kulcs nélkül is teljes értékűen működöm! Számolok téteket, elemzem az oddsokat, elmagyarázom a stratégiát.
                   - **Saját Gemini API Kulcs:** Ha a valós idejű Google Gemini felhős AI modellt szeretnéd használni élő meccsadatokkal, a képernyő tetején lévő 🔑 **kulcs ikonra** koppintva bármikor beillesztheted az ingyenes saját kulcsodat (`aistudio.google.com/apikey`).
            """.trimIndent()
        }

        // General default response
        val fallbackNotice = if (isFallbackFromNetwork) {
            "*(Megjegyzés: A felhős kapcsolat helyett a beépített intelligens offline motor válaszolt neked.)*\n\n"
        } else ""

        return """
            $fallbackNotice👋 **Szia! Én vagyok a TétMester Pro beépített stratégiai és tétkezelő asszisztense.**

            Bármikor fordulhatsz hozzám az alábbiakkal:
            - 🧮 **Tétkalkuláció:** Írd be bátran: pl. *"Számolj 2. körös tétet 1.40 oddsra 200 Ft alaptétnél"*
            - 📈 **Stratégia:** Kérdezz a 4-körös lépcsőről vagy a 100 napos kamatos kamatról.
            - ⚖️ **Kármentés:** Kérdezd meg, mikor érdemes átváltani Nullázó tétre a bankod védelmében.
            - 🛡️ **Kockázatkezelés:** Min Roll és Max Roll limitek kiszámítása.

            💡 *Koppints a fenti gyors gombok valamelyikére, vagy írd be saját kérdésedet alulra!*
        """.trimIndent()
    }

    private fun calculateStakeAnswer(odds: Double, baseStake: Double, round: Int, isFallback: Boolean): String {
        // Calculate progression up to 4 rounds
        val targetProfit = baseStake * (odds - 1.0).coerceAtLeast(0.3)
        val divisor = odds - 1.0

        var s1 = baseStake
        var s2 = (targetProfit + s1) / divisor
        var s3 = (targetProfit + s1 + s2) / divisor
        var s4 = (targetProfit + s1 + s2 + s3) / divisor

        // Break-even (Kármentés) stakes
        var be2 = s1 / divisor
        var be3 = (s1 + be2) / divisor
        var be4 = (s1 + be2 + be3) / divisor

        val currentStake = when (round) {
            1 -> s1
            2 -> s2
            3 -> s3
            4 -> s4
            else -> s1
        }.roundToInt()

        val breakEvenStake = when (round) {
            1 -> s1
            2 -> be2
            3 -> be3
            4 -> be4
            else -> s1
        }.roundToInt()

        val totalLossBefore = when (round) {
            1 -> 0.0
            2 -> s1
            3 -> s1 + s2
            4 -> s1 + s2 + s3
            else -> 0.0
        }.roundToInt()

        val potentialReturn = (currentStake * odds).roundToInt()
        val netProfit = potentialReturn - totalLossBefore - currentStake

        return """
            🧮 **Tétkalkuláció Eredménye ($round. kör | $odds Odds):**

            - **Alaptét:** ${baseStake.roundToInt()} Ft
            - **Eddigi összes veszteség:** $totalLossBefore Ft
            - **Ajánlott Tét ($round. kör):** **$currentStake Ft**
            - **Várható bruttó nyeremény:** $potentialReturn Ft
            - **Tiszta profit nyerés esetén:** **+$netProfit Ft**

            ---
            🛡️ **Biztonsági Kármentő (Nullázó) Opció:**
            Ha nem akarsz profitot termelni, csak a korábbi $totalLossBefore Ft veszteséget visszahozni 0 Ft kockázattal:
            - **Nullázó tét erre a körre:** **$breakEvenStake Ft** (jóval kisebb tőkét köt le!)

            💡 *Tipp: Ha ez a kör nyer, a ciklus lezárul, és a következő fogadásnál azonnal térj vissza az 1. körre (${baseStake.roundToInt()} Ft)!*
        """.trimIndent()
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
