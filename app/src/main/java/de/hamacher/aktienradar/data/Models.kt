package de.hamacher.aktienradar.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Gemeldete Zahlen eines einzelnen Quartals (US-Dollar bzw. Stückzahl). */
data class Quarter(
    val end: LocalDate,
    val revenue: Double?,
    val grossProfit: Double?,
    val opIncome: Double?,
    val netIncome: Double?,
    val ocf: Double?,
    val capex: Double?,
    val shares: Double?,
) {
    val fcf: Double? get() = if (ocf != null) ocf - (capex ?: 0.0) else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("end", end.toString())
        putOpt("rev", revenue)
        putOpt("gp", grossProfit)
        putOpt("op", opIncome)
        putOpt("ni", netIncome)
        putOpt("ocf", ocf)
        putOpt("capex", capex)
        putOpt("sh", shares)
    }

    companion object {
        fun fromJson(o: JSONObject) = Quarter(
            end = LocalDate.parse(o.getString("end")),
            revenue = o.optDoubleOrNull("rev"),
            grossProfit = o.optDoubleOrNull("gp"),
            opIncome = o.optDoubleOrNull("op"),
            netIncome = o.optDoubleOrNull("ni"),
            ocf = o.optDoubleOrNull("ocf"),
            capex = o.optDoubleOrNull("capex"),
            shares = o.optDoubleOrNull("sh"),
        )
    }
}

/** Kennzahlen, die in einer These überwacht werden können. Werte in Anzeige-Einheiten. */
enum class Metric(val label: String, val unit: String) {
    REV_GROWTH("Umsatzwachstum ggü. Vorjahresquartal", "%"),
    GROSS_MARGIN("Bruttomarge (12 Monate)", "%"),
    OP_MARGIN("Operative Marge (12 Monate)", "%"),
    FCF_MARGIN("Free-Cashflow-Marge (12 Monate)", "%"),
    FCF_TTM("Free Cashflow (12 Monate)", "Mio. $"),
    SHARE_CHANGE("Aktienanzahl ggü. Vorjahr", "%"),
    SCORE("Potential-Score", "Punkte"),
}

data class Condition(
    val id: String = UUID.randomUUID().toString(),
    val metric: Metric,
    val atLeast: Boolean,
    val threshold: Double,
) {
    fun describe(): String =
        "${metric.label} ${if (atLeast) "≥" else "≤"} ${fmtNumber(threshold)} ${metric.unit}"

    /** true = erfüllt, false = verletzt, null = keine Daten */
    fun evaluate(analysis: Analysis): Boolean? {
        val v = analysis.value(metric) ?: return null
        return if (atLeast) v >= threshold else v <= threshold
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("metric", metric.name)
        put("atLeast", atLeast)
        put("threshold", threshold)
    }

    companion object {
        fun fromJson(o: JSONObject) = Condition(
            id = o.getString("id"),
            metric = Metric.valueOf(o.getString("metric")),
            atLeast = o.getBoolean("atLeast"),
            threshold = o.getDouble("threshold"),
        )
    }
}

data class WatchItem(
    val ticker: String,
    val cik: Int,
    val name: String,
    val quarters: List<Quarter> = emptyList(),
    val lastUpdated: Long = 0L,
    val lastScore: Int? = null,
    val thesisNote: String = "",
    val conditions: List<Condition> = emptyList(),
    val brokenIds: Set<String> = emptySet(),
    val error: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("ticker", ticker)
        put("cik", cik)
        put("name", name)
        put("quarters", JSONArray().apply { quarters.forEach { put(it.toJson()) } })
        put("lastUpdated", lastUpdated)
        putOpt("lastScore", lastScore)
        put("thesisNote", thesisNote)
        put("conditions", JSONArray().apply { conditions.forEach { put(it.toJson()) } })
        put("brokenIds", JSONArray().apply { brokenIds.forEach { put(it) } })
        putOpt("error", error)
    }

    companion object {
        fun fromJson(o: JSONObject): WatchItem {
            val q = o.optJSONArray("quarters") ?: JSONArray()
            val c = o.optJSONArray("conditions") ?: JSONArray()
            val b = o.optJSONArray("brokenIds") ?: JSONArray()
            return WatchItem(
                ticker = o.getString("ticker"),
                cik = o.getInt("cik"),
                name = o.optString("name", ""),
                quarters = (0 until q.length()).map { Quarter.fromJson(q.getJSONObject(it)) },
                lastUpdated = o.optLong("lastUpdated", 0L),
                lastScore = if (o.has("lastScore") && !o.isNull("lastScore")) o.getInt("lastScore") else null,
                thesisNote = o.optString("thesisNote", ""),
                conditions = (0 until c.length()).map { Condition.fromJson(c.getJSONObject(it)) },
                brokenIds = (0 until b.length()).map { b.getString(it) }.toSet(),
                error = if (o.has("error") && !o.isNull("error")) o.getString("error") else null,
            )
        }
    }
}

fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) getDouble(key) else null
