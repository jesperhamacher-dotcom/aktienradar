package de.hamacher.aktienradar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Ein Kalenderquartal, wie es die SEC-Frames-API verwendet ("CY2026Q2"). */
data class Period(val year: Int, val q: Int) {
    val code: String get() = "CY${year}Q$q"
    val label: String get() = "Q$q $year"
    val end: LocalDate get() = LocalDate.of(year, q * 3, 1).plusMonths(1).minusDays(1)

    fun minus(n: Int): Period {
        val idx = year * 4 + (q - 1) - n
        return Period(idx / 4, idx % 4 + 1)
    }

    companion object {
        /** Das jüngste Quartal, für das die meisten Unternehmen schon berichtet haben (~80 Tage nach Quartalsende). */
        fun latestReported(today: LocalDate = LocalDate.now()): Period {
            var p = Period(today.year, (today.monthValue - 1) / 3 + 1)
            while (p.end.plusDays(80).isAfter(today)) p = p.minus(1)
            return p
        }
    }
}

data class Candidate(
    val cik: Int,
    val ticker: String,
    val name: String,
    val revenue: Double,        // letztes Quartal
    val growthNow: Double,      // ggü. Vorjahresquartal
    val growthBefore: Double?,  // Vorquartal ggü. dessen Vorjahresquartal
    val opMarginNow: Double?,
    val opMarginBefore: Double?, // gleiches Quartal im Vorjahr
    val score: Int,
    val reasons: List<String>,
) {
    val level: Level get() = levelFor(score)

    fun toJson(): JSONObject = JSONObject().apply {
        put("cik", cik); put("ticker", ticker); put("name", name); put("rev", revenue)
        put("g0", growthNow); putOpt("g1", growthBefore)
        putOpt("om0", opMarginNow); putOpt("om1", opMarginBefore)
        put("score", score)
        put("reasons", JSONArray().apply { reasons.forEach { put(it) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Candidate {
            val r = o.optJSONArray("reasons") ?: JSONArray()
            return Candidate(
                cik = o.getInt("cik"), ticker = o.getString("ticker"), name = o.getString("name"),
                revenue = o.getDouble("rev"), growthNow = o.getDouble("g0"),
                growthBefore = o.optDoubleOrNull("g1"),
                opMarginNow = o.optDoubleOrNull("om0"), opMarginBefore = o.optDoubleOrNull("om1"),
                score = o.getInt("score"),
                reasons = (0 until r.length()).map { r.getString(it) },
            )
        }
    }
}

data class ScanResult(
    val period: Period,
    val timestamp: Long,
    val companiesChecked: Int,
    val candidates: List<Candidate>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("year", period.year); put("q", period.q)
        put("ts", timestamp); put("checked", companiesChecked)
        put("candidates", JSONArray().apply { candidates.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): ScanResult {
            val c = o.getJSONArray("candidates")
            return ScanResult(
                Period(o.getInt("year"), o.getInt("q")), o.getLong("ts"), o.getInt("checked"),
                (0 until c.length()).map { Candidate.fromJson(c.getJSONObject(it)) },
            )
        }
    }
}

fun levelFor(score: Int): Level = when {
    score >= 75 -> Level.STRONG
    score >= 60 -> Level.WATCH
    score >= 40 -> Level.CHECK
    else -> Level.WEAK
}

/**
 * Durchsucht alle bei der SEC meldenden Unternehmen nach beschleunigtem Wachstum.
 * Pro Kennzahl und Quartal genügt eine Abfrage (Frames-API) – insgesamt rund 10 Abfragen.
 */
object Scanner {

    private val REVENUE_CONCEPTS = listOf("Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax")
    private const val OP_INCOME = "OperatingIncomeLoss"
    private const val MIN_REVENUE = 20_000_000.0   // Quartalsumsatz, filtert Mini-Firmen und Datenrauschen
    private const val MAX_GROWTH = 3.0             // > +300 % meist Sondereffekte (Übernahmen, SPACs)
    private const val FILE = "scan.json"

    fun load(context: Context): ScanResult? = try {
        val f = File(context.filesDir, FILE)
        if (f.exists()) ScanResult.fromJson(JSONObject(f.readText())) else null
    } catch (_: Exception) {
        null
    }

    private fun save(context: Context, result: ScanResult) {
        File(context.filesDir, FILE).writeText(result.toJson().toString())
    }

    /** Läuft blockierend – nur aus Dispatchers.IO aufrufen. */
    fun run(context: Context, userAgent: String, progress: (String) -> Unit = {}): ScanResult {
        progress("Lade Unternehmensliste …")
        val tickers = SecClient.tickerMap(context, userAgent)

        // Jüngstes Quartal mit ausreichend Meldungen finden
        var p0 = Period.latestReported()
        var firstFrame: JSONArray? = null
        for (attempt in 0 until 3) {
            progress("Prüfe Meldungen für ${p0.label} …")
            val f = SecClient.frame(userAgent, REVENUE_CONCEPTS[0], "USD", p0.code)
            if (f != null && f.length() > 1000) { firstFrame = f; break }
            p0 = p0.minus(1)
        }
        val periods = listOf(p0, p0.minus(1), p0.minus(4), p0.minus(5))

        // revenue[concept][period] = cik -> Wert
        val revenue = HashMap<String, Map<Period, Map<Int, Double>>>()
        for (concept in REVENUE_CONCEPTS) {
            val perPeriod = HashMap<Period, Map<Int, Double>>()
            for (p in periods) {
                progress("Lade Umsätze ${p.label} …")
                val arr = if (concept == REVENUE_CONCEPTS[0] && p == p0 && firstFrame != null) firstFrame
                else SecClient.frame(userAgent, concept, "USD", p.code)
                perPeriod[p] = toMap(arr)
                Thread.sleep(150)
            }
            revenue[concept] = perPeriod
        }
        progress("Lade operative Ergebnisse …")
        val op0 = toMap(SecClient.frame(userAgent, OP_INCOME, "USD", periods[0].code))
        val op4 = toMap(SecClient.frame(userAgent, OP_INCOME, "USD", periods[2].code))

        progress("Bewerte Unternehmen …")
        val allCiks = revenue.values.flatMap { it[p0]?.keys ?: emptySet() }.toSet()
        val candidates = ArrayList<Candidate>()
        var checked = 0
        for (cik in allCiks) {
            val info = tickers[cik] ?: continue
            // Pro Firma ein einheitliches Umsatzfeld verwenden
            val series = REVENUE_CONCEPTS.asSequence()
                .mapNotNull { revenue[it] }
                .firstOrNull { it[periods[0]]?.get(cik) != null && it[periods[2]]?.get(cik) != null }
                ?: continue
            val r0 = series[periods[0]]?.get(cik) ?: continue
            val r4 = series[periods[2]]?.get(cik) ?: continue
            val r1 = series[periods[1]]?.get(cik)
            val r5 = series[periods[3]]?.get(cik)
            checked++
            if (r0 < MIN_REVENUE || r4 < MIN_REVENUE / 2) continue

            val g0 = r0 / r4 - 1
            if (g0 > MAX_GROWTH) continue
            val g1 = if (r1 != null && r5 != null && r5 > 0) r1 / r5 - 1 else null
            val om0 = op0[cik]?.let { it / r0 }
            val om4 = op4[cik]?.let { it / r4 }

            val c = score(cik, info, r0, g0, g1, om0, om4)
            if (c.score >= 56) candidates += c
        }

        val result = ScanResult(
            period = p0,
            timestamp = System.currentTimeMillis(),
            companiesChecked = checked,
            candidates = candidates.sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { it.growthNow })
                .take(300),
        )
        save(context, result)
        return result
    }

    private fun toMap(arr: JSONArray?): Map<Int, Double> {
        if (arr == null) return emptyMap()
        val m = HashMap<Int, Double>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            m[o.getInt("cik")] = o.getDouble("val")
        }
        return m
    }

    /** Gleiche Logik und Gewichte wie die Detailanalyse, soweit die Daten reichen. */
    private fun score(
        cik: Int, info: TickerInfo, r0: Double, g0: Double, g1: Double?, om0: Double?, om4: Double?,
    ): Candidate {
        var pts = 0
        val reasons = mutableListOf<String>()
        when {
            g0 >= 0.25 -> { pts += 12; reasons += "Umsatz ${pct(g0)} ggü. Vorjahr" }
            g0 >= 0.10 -> { pts += 6; reasons += "Umsatz ${pct(g0)} ggü. Vorjahr" }
            g0 < 0 -> { pts -= 10; reasons += "Umsatz schrumpft (${pct(g0)})" }
        }
        if (g1 != null) {
            val accel = g0 - g1
            when {
                accel >= 0.03 -> { pts += 10; reasons += "Wachstum beschleunigt: ${pct(g1, false)} → ${pct(g0, false)}" }
                accel <= -0.05 -> { pts -= 10; reasons += "Wachstum verlangsamt: ${pct(g1, false)} → ${pct(g0, false)}" }
            }
        }
        if (om0 != null && om4 != null) {
            val d = om0 - om4
            when {
                d >= 0.01 -> { pts += 8; reasons += "Op. Marge steigt: ${pct(om4, false)} → ${pct(om0, false)}" }
                d <= -0.02 -> { pts -= 8; reasons += "Op. Marge sinkt: ${pct(om4, false)} → ${pct(om0, false)}" }
            }
        }
        return Candidate(
            cik = cik, ticker = info.ticker, name = info.name, revenue = r0,
            growthNow = g0, growthBefore = g1, opMarginNow = om0, opMarginBefore = om4,
            score = (50 + pts).coerceIn(0, 100), reasons = reasons,
        )
    }
}
