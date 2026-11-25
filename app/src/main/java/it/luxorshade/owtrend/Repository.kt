package it.luxorshade.owtrend

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Scarica i report dal repository GitHub pubblico (raw.githubusercontent.com)
 * e li tiene in una cache locale, così l'app funziona anche offline.
 */
class Repository(private val context: Context) {

    private val prefs = context.getSharedPreferences("owtrend", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "cache").apply { mkdirs() }

    /** Repository nel formato "proprietario/nome". Modificabile dalle Impostazioni. */
    var repo: String
        get() = prefs.getString(KEY_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
        set(value) = prefs.edit().putString(KEY_REPO, value.trim().trim('/')).apply()

    var branch: String
        get() = prefs.getString(KEY_BRANCH, DEFAULT_BRANCH) ?: DEFAULT_BRANCH
        set(value) = prefs.edit().putString(KEY_BRANCH, value.trim()).apply()

    var lastSeenId: String
        get() = prefs.getString(KEY_LAST_SEEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SEEN, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY, value).apply()

    private fun rawUrl(path: String) =
        "https://raw.githubusercontent.com/$repo/$branch/data/$path"

    private fun download(path: String): String {
        // Parametro anti-cache: raw.githubusercontent.com tiene i file in cache per qualche minuto
        val url = URL(rawUrl(path) + "?t=" + System.currentTimeMillis() / 60_000)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("HTTP $code per $path")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun cacheFile(path: String) = File(cacheDir, path.replace('/', '_'))

    /** Prova online; se non c'è rete usa la copia in cache. */
    private fun fetch(path: String): Pair<String, Boolean> {
        return try {
            val text = download(path)
            cacheFile(path).writeText(text, Charsets.UTF_8)
            text to true
        } catch (e: Exception) {
            val cached = cacheFile(path)
            if (cached.exists()) cached.readText(Charsets.UTF_8) to false else throw e
        }
    }

    data class LoadResult(
        val reports: List<Report>,
        val config: RemoteConfig?,
        val online: Boolean,
    )

    suspend fun loadAll(): LoadResult = withContext(Dispatchers.IO) {
        val (indexJson, online) = fetch("index.json")
        val summaries = Parser.index(indexJson)
        val reports = summaries.mapNotNull { s ->
            runCatching { Parser.report(fetch(s.file).first) }.getOrNull()
        }
        val config = runCatching { Parser.config(fetch("config.json").first) }.getOrNull()
        LoadResult(reports, config, online)
    }

    /** Usato dal controllo in background: restituisce solo l'ultimo report dell'indice. */
    suspend fun latestSummary(): ReportSummary? = withContext(Dispatchers.IO) {
        Parser.index(download("index.json")).firstOrNull()
    }

    companion object {
        const val DEFAULT_REPO = "Luxorshade/overwatch-trend-report"
        const val DEFAULT_BRANCH = "main"
        private const val KEY_REPO = "repo"
        private const val KEY_BRANCH = "branch"
        private const val KEY_LAST_SEEN = "last_seen_id"
        private const val KEY_NOTIFY = "notify"
    }
}
