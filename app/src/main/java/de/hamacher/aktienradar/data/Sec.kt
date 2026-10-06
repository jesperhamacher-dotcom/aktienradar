package de.hamacher.aktienradar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SecException(message: String) : Exception(message)

data class TickerInfo(val ticker: String, val cik: Int, val name: String)

/**
 * Zugriff auf die kostenlosen SEC-EDGAR-Schnittstellen.
 * Die SEC verlangt einen User-Agent mit Kontaktadresse und erlaubt max. 10 Anfragen/Sekunde.
 */
object SecClient {

    private const val TICKERS_URL = "https://www.sec.gov/files/company_tickers.json"
    private const val FACTS_URL = "https://data.sec.gov/api/xbrl/companyfacts/CIK%010d.json"
    private const val TICKER_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** Lädt eine URL; liefert null bei 404 (nicht vorhanden). */
    private fun getOrNull(url: String, userAgent: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            val code = conn.responseCode
            when {
                code == 404 -> return null
                code == 403 || code == 429 -> throw SecException(
                    "Die SEC hat die Anfrage abgelehnt (HTTP $code). Bitte in den Einstellungen eine echte Kontakt-E-Mail eintragen und später erneut versuchen."
                )
                code !in 200..299 -> throw SecException("SEC-Fehler HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String, userAgent: String): String =
        getOrNull(url, userAgent) ?: throw SecException("Keine SEC-Finanzdaten für dieses Unternehmen gefunden.")

    /** Alle bei der SEC gelisteten Ticker, nach CIK (erster Eintrag = Hauptaktie). Eine Woche zwischengespeichert. */
    fun tickerMap(context: Context, userAgent: String): Map<Int, TickerInfo> {
        val cache = File(context.filesDir, "company_tickers.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < TICKER_CACHE_MAX_AGE_MS
        val text = if (fresh) cache.readText() else {
            get(TICKERS_URL, userAgent).also { cache.writeText(it) }
        }
        val root = JSONObject(text)
        val result = LinkedHashMap<Int, TickerInfo>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val o = root.getJSONObject(keys.next())
            val cik = o.getInt("cik_str")
            if (!result.containsKey(cik)) {
                result[cik] = TickerInfo(o.getString("ticker").uppercase(), cik, o.getString("title"))
            }
        }
        return result
    }

    /** Ein Wert pro Unternehmen für ein Kalenderquartal, z. B. period = "CY2026Q2". Null, wenn es den Frame nicht gibt. */
    fun frame(userAgent: String, concept: String, unit: String, period: String): JSONArray? {
        val text = getOrNull("https://data.sec.gov/api/xbrl/frames/us-gaap/$concept/$unit/$period.json", userAgent)
            ?: return null
        return JSONObject(text).optJSONArray("data")
    }

    /** Sucht ein US-Ticker-Symbol in der offiziellen SEC-Liste (wird eine Woche zwischengespeichert). */
    fun lookupTicker(context: Context, userAgent: String, ticker: String): TickerInfo? {
        val cache = File(context.filesDir, "company_tickers.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < TICKER_CACHE_MAX_AGE_MS
        val text = if (fresh) cache.readText() else {
            get(TICKERS_URL, userAgent).also { cache.writeText(it) }
        }
        val root = JSONObject(text)
        val wanted = ticker.trim().uppercase()
        val keys = root.keys()
        while (keys.hasNext()) {
            val o = root.getJSONObject(keys.next())
            if (o.getString("ticker").equals(wanted, ignoreCase = true)) {
                return TickerInfo(o.getString("ticker").uppercase(), o.getInt("cik_str"), o.getString("title"))
            }
        }
        return null
    }

    fun companyFacts(userAgent: String, cik: Int): JSONObject =
        JSONObject(get(String.format(java.util.Locale.US, FACTS_URL, cik), userAgent))
}

/**
 * Wandelt die XBRL-"companyfacts" in Quartalswerte um.
 * 10-Q-Berichte enthalten Cashflows oft nur kumuliert (6 bzw. 9 Monate) und das 4. Quartal
 * steckt nur im Jahreswert – beides wird hier über Differenzen in echte Quartale zerlegt.
 */
object FactParser {

    private data class RawFact(val start: LocalDate?, val end: LocalDate, val value: Double, val filed: String)

    private val REVENUE = listOf(
        "Revenues",
        "RevenueFromContractWithCustomerExcludingAssessedTax",
        "RevenueFromContractWithCustomerIncludingAssessedTax",
        "SalesRevenueNet",
        "SalesRevenueGoodsNet",
        "SalesRevenueServicesNet",
    )
    private val GROSS_PROFIT = listOf("GrossProfit")
    private val OP_INCOME = listOf("OperatingIncomeLoss")
    private val NET_INCOME = listOf("NetIncomeLoss", "ProfitLoss")
    private val OCF = listOf(
        "NetCashProvidedByUsedInOperatingActivities",
        "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations",
    )
    private val CAPEX = listOf("PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets")
    private val SHARES = listOf("WeightedAverageNumberOfDilutedSharesOutstanding")

    fun parse(root: JSONObject, maxQuarters: Int = 16): List<Quarter> {
        val facts = root.optJSONObject("facts") ?: throw SecException("Antwort enthält keine Finanzdaten.")

        val revenue = merged(facts, REVENUE, "USD", additive = true)
        if (revenue.isEmpty()) throw SecException("Keine Umsatzdaten in den SEC-Meldungen gefunden (evtl. Bank, Versicherer oder ausländischer Emittent).")
        val gross = merged(facts, GROSS_PROFIT, "USD", additive = true)
        val op = merged(facts, OP_INCOME, "USD", additive = true)
        val net = merged(facts, NET_INCOME, "USD", additive = true)
        val ocf = merged(facts, OCF, "USD", additive = true)
        val capex = merged(facts, CAPEX, "USD", additive = true)
        var shares = merged(facts, SHARES, "shares", additive = false)
        if (shares.isEmpty()) {
            shares = instants(readFacts(facts, "dei", "EntityCommonStockSharesOutstanding", "shares"))
        }

        return revenue.keys.sorted().takeLast(maxQuarters).map { end ->
            Quarter(
                end = end,
                revenue = revenue[end],
                grossProfit = gross[end],
                opIncome = op[end],
                netIncome = net[end],
                ocf = ocf[end],
                capex = capex[end],
                shares = nearest(shares, end, maxDays = 60),
            )
        }
    }

    private fun merged(facts: JSONObject, concepts: List<String>, unit: String, additive: Boolean): Map<LocalDate, Double> {
        val result = HashMap<LocalDate, Double>()
        for (c in concepts) {
            quarterly(readFacts(facts, "us-gaap", c, unit), additive).forEach { (k, v) -> result.putIfAbsent(k, v) }
        }
        return result
    }

    private fun readFacts(facts: JSONObject, taxonomy: String, concept: String, unit: String): List<RawFact> {
        val arr = facts.optJSONObject(taxonomy)
            ?.optJSONObject(concept)
            ?.optJSONObject("units")
            ?.optJSONArray(unit) ?: return emptyList()
        val out = ArrayList<RawFact>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            try {
                out += RawFact(
                    start = if (o.has("start")) LocalDate.parse(o.getString("start")) else null,
                    end = LocalDate.parse(o.getString("end")),
                    value = o.getDouble("val"),
                    filed = o.optString("filed", ""),
                )
            } catch (_: Exception) {
                // fehlerhafte Einzelwerte überspringen
            }
        }
        return out
    }

    private fun days(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)

    private fun quarterly(raw: List<RawFact>, additive: Boolean): Map<LocalDate, Double> {
        // Pro Zeitraum den zuletzt gemeldeten Wert nehmen (berücksichtigt Korrekturen)
        val periods = raw.filter { it.start != null }
            .groupBy { Pair(it.start!!, it.end) }
            .values
            .map { group -> group.maxByOrNull { it.filed }!! }

        val result = HashMap<LocalDate, Double>()
        for (f in periods) {
            if (days(f.start!!, f.end) in 80..100) result[f.end] = f.value
        }
        if (additive) {
            for (group in periods.groupBy { it.start!! }.values) {
                val sorted = group.sortedBy { it.end }
                for (i in 1 until sorted.size) {
                    val a = sorted[i - 1]
                    val b = sorted[i]
                    if (days(a.end, b.end) in 80..100 && !result.containsKey(b.end)) {
                        result[b.end] = b.value - a.value
                    }
                }
            }
        }
        return result
    }

    private fun instants(raw: List<RawFact>): Map<LocalDate, Double> =
        raw.filter { it.start == null }
            .groupBy { it.end }
            .mapValues { (_, g) -> g.maxByOrNull { it.filed }!!.value }

    private fun nearest(series: Map<LocalDate, Double>, date: LocalDate, maxDays: Long): Double? =
        series.entries
            .filter { kotlin.math.abs(days(it.key, date)) <= maxDays }
            .minByOrNull { kotlin.math.abs(days(it.key, date)) }
            ?.value
}
