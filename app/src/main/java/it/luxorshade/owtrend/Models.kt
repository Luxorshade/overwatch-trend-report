package it.luxorshade.owtrend

import org.json.JSONArray
import org.json.JSONObject

/** Voce dell'indice: elenco dei report disponibili (data/index.json). */
data class ReportSummary(
    val id: String,
    val week: String,
    val summary: String,
    val file: String,
)

data class OverwatchNews(
    val title: String,
    val detail: String,
    val date: String,
    val idea: String,
    val url: String,
)

data class TrendAudio(
    val title: String,
    val artist: String,
    val stage: String,
    val use: String,
    val idea: String,
)

data class TrendHashtag(
    val tag: String,
    val why: String,
)

data class SourceLink(
    val title: String,
    val url: String,
)

/** Report settimanale completo (data/reports/AAAA-MM-GG.json). */
data class Report(
    val id: String,
    val week: String,
    val generatedAt: String,
    val summary: String,
    val sources: List<String>,
    val overwatch: List<OverwatchNews>,
    val audio: List<TrendAudio>,
    val hashtags: List<TrendHashtag>,
    val notes: String,
    val links: List<SourceLink>,
) {
    /** Testo unico usato dalla ricerca. */
    val searchText: String by lazy {
        buildString {
            append(week).append(' ').append(summary).append(' ').append(notes).append(' ')
            overwatch.forEach { append(it.title).append(' ').append(it.detail).append(' ').append(it.idea).append(' ') }
            audio.forEach { append(it.title).append(' ').append(it.artist).append(' ').append(it.use).append(' ').append(it.idea).append(' ') }
            hashtags.forEach { append(it.tag).append(' ').append(it.why).append(' ') }
        }.lowercase()
    }
}

/** Impostazioni lette da data/config.json nel repository. */
data class RemoteConfig(
    val webEnabled: Boolean,
    val dashboardEnabled: Boolean,
    val dashboardNote: String,
    val schedule: String,
)

object Parser {

    private fun JSONObject.str(key: String) = optString(key, "")

    private inline fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(block) }
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it, "") }.filter { it.isNotBlank() }
    }

    fun index(json: String): List<ReportSummary> {
        val root = JSONObject(json)
        return root.optJSONArray("reports").mapObjects {
            ReportSummary(it.str("id"), it.str("week"), it.str("summary"), it.str("file"))
        }.sortedByDescending { it.id }
    }

    fun report(json: String): Report {
        val o = JSONObject(json)
        return Report(
            id = o.str("id"),
            week = o.str("week"),
            generatedAt = o.str("generatedAt"),
            summary = o.str("summary"),
            sources = o.optJSONArray("sources").strings(),
            overwatch = o.optJSONArray("overwatch").mapObjects {
                OverwatchNews(it.str("title"), it.str("detail"), it.str("date"), it.str("idea"), it.str("url"))
            },
            audio = o.optJSONArray("audio").mapObjects {
                TrendAudio(it.str("title"), it.str("artist"), it.str("stage"), it.str("use"), it.str("idea"))
            },
            hashtags = o.optJSONArray("hashtags").mapObjects {
                TrendHashtag(it.str("tag"), it.str("why"))
            },
            notes = o.str("notes"),
            links = o.optJSONArray("links").mapObjects { SourceLink(it.str("title"), it.str("url")) },
        )
    }

    fun config(json: String): RemoteConfig {
        val o = JSONObject(json)
        val src = o.optJSONObject("sources") ?: JSONObject()
        val web = src.optJSONObject("web") ?: JSONObject()
        val dash = src.optJSONObject("instagramDashboard") ?: JSONObject()
        return RemoteConfig(
            webEnabled = web.optBoolean("enabled", true),
            dashboardEnabled = dash.optBoolean("enabled", false),
            dashboardNote = dash.optString("note", ""),
            schedule = o.optString("schedule", ""),
        )
    }
}
