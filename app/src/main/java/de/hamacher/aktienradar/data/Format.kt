package de.hamacher.aktienradar.data

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

private val DE = Locale.GERMANY

/** Anteil (0.153) als Prozent ("+15,3 %"). */
fun pct(v: Double, sign: Boolean = true): String =
    String.format(DE, if (sign) "%+.1f %%" else "%.1f %%", v * 100)

/** US-Dollar-Betrag kompakt ("12,3 Mrd. $"). */
fun money(v: Double): String = when {
    abs(v) >= 1e9 -> String.format(DE, "%.1f Mrd. $", v / 1e9)
    abs(v) >= 1e6 -> String.format(DE, "%.0f Mio. $", v / 1e6)
    else -> String.format(DE, "%.0f $", v)
}

fun fmtNumber(v: Double): String =
    if (v == Math.floor(v) && abs(v) < 1e12) String.format(DE, "%.0f", v) else String.format(DE, "%.1f", v)

/** Kalenderquartal, in dem das Quartal endet ("Q3 2025"). */
fun quarterLabel(end: LocalDate): String {
    // Quartale, die in den ersten Tagen eines Monats enden, dem Vormonat zuordnen
    val d = if (end.dayOfMonth <= 7) end.minusDays(8) else end
    return "Q${(d.monthValue - 1) / 3 + 1} ${d.year}"
}
