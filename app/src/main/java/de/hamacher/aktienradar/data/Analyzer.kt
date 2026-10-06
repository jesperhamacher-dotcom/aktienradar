package de.hamacher.aktienradar.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class QuarterMetrics(
    val end: LocalDate,
    val revenue: Double?,
    val revGrowth: Double?,      // ggü. Vorjahresquartal, als Anteil (0.25 = 25 %)
    val opMargin: Double?,       // Quartal
    val fcf: Double?,            // Quartal
    val ttmRevenue: Double?,
    val ttmGrossMargin: Double?,
    val ttmOpMargin: Double?,
    val ttmFcf: Double?,
    val ttmFcfMargin: Double?,
    val sharesYoY: Double?,
)

data class Signal(val positive: Boolean, val text: String, val points: Int)

enum class Level(val emoji: String, val label: String) {
    STRONG("🟢", "Starke Konstellation"),
    WATCH("🟡", "Beobachten – Potenzial entsteht"),
    CHECK("🟠", "Gemischt – These überprüfen"),
    WEAK("🔴", "Mehrere negative Veränderungen"),
    NODATA("⚪", "Zu wenig Daten"),
}

data class Analysis(
    val metrics: List<QuarterMetrics>,
    val score: Int,
    val level: Level,
    val signals: List<Signal>,
) {
    val latest: QuarterMetrics? get() = metrics.lastOrNull()

    /** Aktueller Wert einer Kennzahl in Anzeige-Einheiten (Prozent, Mio. $, Punkte). */
    fun value(metric: Metric): Double? {
        val m = latest ?: return null
        return when (metric) {
            Metric.REV_GROWTH -> m.revGrowth?.times(100)
            Metric.GROSS_MARGIN -> m.ttmGrossMargin?.times(100)
            Metric.OP_MARGIN -> m.ttmOpMargin?.times(100)
            Metric.FCF_MARGIN -> m.ttmFcfMargin?.times(100)
            Metric.FCF_TTM -> m.ttmFcf?.div(1_000_000)
            Metric.SHARE_CHANGE -> m.sharesYoY?.times(100)
            Metric.SCORE -> if (level == Level.NODATA) null else score.toDouble()
        }
    }
}

/**
 * Bewertet die Entwicklung (nicht den Stand) der Kennzahlen.
 * Die Punktgewichte sind bewusst einfache Heuristiken und nicht durch Backtests belegt.
 */
object Analyzer {

    private fun days(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)

    fun metrics(quarters: List<Quarter>): List<QuarterMetrics> {
        val q = quarters.sortedBy { it.end }

        fun yearAgo(i: Int): Quarter? = q.take(i).lastOrNull { days(it.end, q[i].end) in 340..390 }

        /** Summe über die letzten 4 Quartale, nur wenn diese lückenlos sind. */
        fun ttm(i: Int, sel: (Quarter) -> Double?): Double? {
            if (i < 3) return null
            val window = q.subList(i - 3, i + 1)
            if (days(window.first().end, window.last().end) !in 250..290) return null
            val values = window.map(sel)
            if (values.any { it == null }) return null
            return values.sumOf { it!! }
        }

        fun ratio(a: Double?, b: Double?): Double? =
            if (a != null && b != null && b != 0.0) a / b else null

        return q.indices.map { i ->
            val cur = q[i]
            val prev = yearAgo(i)
            val ttmRev = ttm(i) { it.revenue }
            val ttmFcf = ttm(i) { it.fcf }
            QuarterMetrics(
                end = cur.end,
                revenue = cur.revenue,
                revGrowth = if (prev?.revenue != null && cur.revenue != null && prev.revenue > 0)
                    cur.revenue / prev.revenue - 1 else null,
                opMargin = ratio(cur.opIncome, cur.revenue),
                fcf = cur.fcf,
                ttmRevenue = ttmRev,
                ttmGrossMargin = ratio(ttm(i) { it.grossProfit }, ttmRev),
                ttmOpMargin = ratio(ttm(i) { it.opIncome }, ttmRev),
                ttmFcf = ttmFcf,
                ttmFcfMargin = ratio(ttmFcf, ttmRev),
                sharesYoY = if (prev?.shares != null && cur.shares != null && prev.shares > 0)
                    cur.shares / prev.shares - 1 else null,
            )
        }
    }

    fun analyze(quarters: List<Quarter>): Analysis {
        val m = metrics(quarters)
        val signals = mutableListOf<Signal>()
        val last = m.lastOrNull()
        val growthCount = m.count { it.revGrowth != null }
        if (last == null || growthCount < 2) {
            return Analysis(m, 50, Level.NODATA, emptyList())
        }
        val ago = m.lastOrNull { days(it.end, last.end) in 340..390 }

        // 1) Aktuelles Umsatzwachstum
        last.revGrowth?.let { g ->
            when {
                g >= 0.25 -> signals += Signal(true, "Umsatz ${pct(g)} ggü. Vorjahresquartal", 12)
                g >= 0.10 -> signals += Signal(true, "Umsatz ${pct(g)} ggü. Vorjahresquartal", 6)
                g < 0 -> signals += Signal(false, "Umsatz schrumpft: ${pct(g)} ggü. Vorjahresquartal", -10)
                else -> {}
            }
        }

        // 2) Beschleunigung / Verlangsamung
        val growth = m.mapNotNull { it.revGrowth }.takeLast(4)
        if (growth.size == 4) {
            val earlier = (growth[0] + growth[1]) / 2
            val recent = (growth[2] + growth[3]) / 2
            val path = growth.joinToString(" → ") { pct(it, sign = false) }
            when {
                recent - earlier >= 0.03 -> signals += Signal(true, "Wachstum beschleunigt: $path", 10)
                recent - earlier <= -0.05 -> signals += Signal(false, "Wachstum verlangsamt sich: $path", -10)
            }
        }

        if (ago != null) {
            // 3) Operative Marge
            diff(last.ttmOpMargin, ago.ttmOpMargin)?.let { d ->
                val txt = "${pct(ago.ttmOpMargin!!, false)} → ${pct(last.ttmOpMargin!!, false)}"
                when {
                    d >= 0.01 -> signals += Signal(true, "Operative Marge steigt: $txt", 8)
                    d <= -0.02 -> signals += Signal(false, "Operative Marge sinkt: $txt", -8)
                }
            }
            // 4) Bruttomarge
            diff(last.ttmGrossMargin, ago.ttmGrossMargin)?.let { d ->
                val txt = "${pct(ago.ttmGrossMargin!!, false)} → ${pct(last.ttmGrossMargin!!, false)}"
                when {
                    d >= 0.01 -> signals += Signal(true, "Bruttomarge steigt: $txt", 4)
                    d <= -0.02 -> signals += Signal(false, "Bruttomarge sinkt: $txt", -4)
                }
            }
            // 5b) Free-Cashflow-Entwicklung
            val now = last.ttmFcf
            val before = ago.ttmFcf
            if (now != null && before != null && before != 0.0) {
                val change = (now - before) / abs(before)
                when {
                    change >= 0.15 -> signals += Signal(true, "Free Cashflow verbessert sich: ${pct(change)} (12 Monate)", 6)
                    change <= -0.25 -> signals += Signal(false, "Free Cashflow fällt: ${pct(change)} (12 Monate)", -8)
                }
            }
        }

        // 5a) Free Cashflow positiv/negativ
        last.ttmFcf?.let { f ->
            if (f > 0) signals += Signal(true, "Free Cashflow positiv: ${money(f)} (12 Monate)", 6)
            else signals += Signal(false, "Free Cashflow negativ: ${money(f)} (12 Monate)", -8)
        }

        // 6) Verwässerung / Rückkäufe
        last.sharesYoY?.let { s ->
            when {
                s >= 0.03 -> signals += Signal(false, "Verwässerung: Aktienanzahl ${pct(s)} ggü. Vorjahr", -6)
                s <= -0.01 -> signals += Signal(true, "Aktienrückkäufe: Aktienanzahl ${pct(s)} ggü. Vorjahr", 4)
            }
        }

        val score = (50 + signals.sumOf { it.points }).coerceIn(0, 100)
        val level = when {
            score >= 75 -> Level.STRONG
            score >= 60 -> Level.WATCH
            score >= 40 -> Level.CHECK
            else -> Level.WEAK
        }
        return Analysis(m, score, level, signals.sortedByDescending { abs(it.points) })
    }

    private fun diff(a: Double?, b: Double?): Double? = if (a != null && b != null) a - b else null
}
