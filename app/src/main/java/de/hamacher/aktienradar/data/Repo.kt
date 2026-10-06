package de.hamacher.aktienradar.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Ergebnis einer Aktualisierung, für Benachrichtigungen. */
data class Change(
    val item: WatchItem,
    val oldScore: Int?,
    val analysis: Analysis,
    val newlyBroken: List<Condition>,
)

object Repo {
    private const val PREFS = "aktienradar"
    private const val KEY_ITEMS = "watchlist"
    private const val KEY_EMAIL = "contact_email"

    private lateinit var prefs: SharedPreferences
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<WatchItem>>(emptyList())
    val items: StateFlow<List<WatchItem>> = _items.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _items.value = try {
            val arr = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
            (0 until arr.length()).map { WatchItem.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    var contactEmail: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value.trim()).apply()

    private fun userAgent(): String {
        val mail = contactEmail
        if (!mail.contains("@")) {
            throw SecException("Bitte zuerst in den Einstellungen eine Kontakt-E-Mail eintragen. Die SEC verlangt sie für jede Abfrage.")
        }
        return "Aktienradar/0.1 (private use) $mail"
    }

    fun find(ticker: String): WatchItem? = _items.value.firstOrNull { it.ticker == ticker }

    private suspend fun update(transform: (List<WatchItem>) -> List<WatchItem>) = mutex.withLock {
        val next = transform(_items.value)
        _items.value = next
        val arr = JSONArray().apply { next.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private suspend fun modify(ticker: String, f: (WatchItem) -> WatchItem) =
        update { list -> list.map { if (it.ticker == ticker) f(it) else it } }

    /** Fügt eine Aktie hinzu und lädt sofort ihre Daten. Liefert eine Fehlermeldung oder null. */
    suspend fun add(context: Context, ticker: String): String? {
        val t = ticker.trim().uppercase()
        if (t.isEmpty()) return "Bitte ein Ticker-Symbol eingeben."
        if (find(t) != null) return "$t ist bereits in der Watchlist."
        return try {
            val ua = userAgent()
            val info = withContext(Dispatchers.IO) { SecClient.lookupTicker(context, ua, t) }
                ?: return "$t wurde nicht in der SEC-Liste gefunden. Es werden nur bei der SEC meldepflichtige (v. a. US-)Unternehmen unterstützt."
            update { it + WatchItem(info.ticker, info.cik, info.name) }
            refresh(info.ticker)
            find(info.ticker)?.error
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e.toString()
        }
    }

    suspend fun remove(ticker: String) = update { list -> list.filterNot { it.ticker == ticker } }

    /** Lädt die SEC-Daten neu, berechnet den Score und prüft die These. */
    suspend fun refresh(ticker: String): Change? {
        val item = find(ticker) ?: return null
        return try {
            val ua = userAgent()
            val quarters = withContext(Dispatchers.IO) {
                FactParser.parse(SecClient.companyFacts(ua, item.cik))
            }
            val analysis = Analyzer.analyze(quarters)
            val current = find(ticker) ?: return null
            val broken = current.conditions.filter { it.evaluate(analysis) == false }
            val brokenIds = broken.map { it.id }.toSet()
            modify(ticker) {
                it.copy(
                    quarters = quarters,
                    lastUpdated = System.currentTimeMillis(),
                    lastScore = if (analysis.level == Level.NODATA) null else analysis.score,
                    brokenIds = brokenIds,
                    error = null,
                )
            }
            Change(current, current.lastScore, analysis, broken.filter { it.id !in current.brokenIds })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            modify(ticker) { it.copy(error = e.message ?: e.toString()) }
            null
        }
    }

    suspend fun setThesisNote(ticker: String, note: String) = modify(ticker) { it.copy(thesisNote = note) }

    suspend fun addCondition(ticker: String, condition: Condition) = modify(ticker) { item ->
        val analysis = Analyzer.analyze(item.quarters)
        val broken = if (condition.evaluate(analysis) == false) item.brokenIds + condition.id else item.brokenIds
        item.copy(conditions = item.conditions + condition, brokenIds = broken)
    }

    suspend fun removeCondition(ticker: String, id: String) = modify(ticker) { item ->
        item.copy(conditions = item.conditions.filterNot { it.id == id }, brokenIds = item.brokenIds - id)
    }
}
