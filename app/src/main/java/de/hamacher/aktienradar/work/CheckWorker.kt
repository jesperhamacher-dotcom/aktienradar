package de.hamacher.aktienradar.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.hamacher.aktienradar.data.Level
import de.hamacher.aktienradar.data.Repo
import de.hamacher.aktienradar.data.pct
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Prüft regelmäßig alle Aktien der Watchlist und meldet relevante Veränderungen. */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        for (item in Repo.items.value) {
            val change = Repo.refresh(item.ticker) ?: continue
            val a = change.analysis
            val baseId = item.ticker.hashCode()

            if (change.newlyBroken.isNotEmpty()) {
                Notifier.notify(
                    applicationContext, baseId,
                    "⚠️ These gefährdet: ${item.ticker}",
                    change.newlyBroken.map { "Nicht mehr erfüllt: ${it.describe()}" },
                    item.ticker,
                )
            }

            val old = change.oldScore
            if (old != null && a.level != Level.NODATA && abs(a.score - old) >= 10) {
                val up = a.score > old
                val reasons = a.signals.filter { it.positive == up }.take(4).map { it.text }
                Notifier.notify(
                    applicationContext, baseId + 1,
                    "${a.level.emoji} ${item.ticker}: Score $old → ${a.score}",
                    listOf(a.level.label) + reasons,
                    item.ticker,
                )
            }
            delay(300) // SEC-Limit schonen
        }
        scanIfDue()
        return Result.success()
    }

    /** Markt-Scan etwa einmal pro Woche; meldet neue starke Kandidaten. */
    private suspend fun scanIfDue() {
        val last = Repo.scan.value?.timestamp ?: 0L
        if (System.currentTimeMillis() - last < 6L * 24 * 60 * 60 * 1000) return
        val (result, previous) = try {
            Repo.runScan(applicationContext) ?: return
        } catch (e: Exception) {
            return
        }
        val fresh = result.candidates.filter { it.score >= 75 && it.ticker !in previous }
        if (fresh.isEmpty()) return
        Notifier.notify(
            applicationContext, 4711,
            "🚨 ${fresh.size} neue Kandidaten im Radar",
            fresh.take(6).map { "${it.ticker} · Score ${it.score} · Umsatz ${pct(it.growthNow)}" },
            ticker = "",
        )
    }

    companion object {
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("watchlist-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CheckWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
