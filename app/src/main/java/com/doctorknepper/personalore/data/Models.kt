package com.doctorknepper.personalore.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class AudioClip(val file: String, val durationMs: Long) {
    fun toJson() = JSONObject().put("file", file).put("durationMs", durationMs)
    companion object {
        fun fromJson(o: JSONObject) = AudioClip(o.getString("file"), o.optLong("durationMs", 0))
    }
}

data class Entry(
    val id: String,
    val date: LocalDate,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String,
    val body: String,
    val mood: Int?, // 1..5
    val tags: List<String>,
    val photos: List<String>,
    val audio: List<AudioClip>,
    val promptId: String?,
    val promptText: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("date", date.toString())
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("title", title)
        .put("body", body)
        .put("mood", mood ?: JSONObject.NULL)
        .put("tags", JSONArray(tags))
        .put("photos", JSONArray(photos))
        .put("audio", JSONArray(audio.map { it.toJson() }))
        .put("promptId", promptId ?: JSONObject.NULL)
        .put("promptText", promptText ?: JSONObject.NULL)

    val preview: String get() = body.trim().replace(Regex("\\s+"), " ").take(160)

    companion object {
        fun fromJson(o: JSONObject) = Entry(
            id = o.getString("id"),
            date = LocalDate.parse(o.getString("date")),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            title = o.optString("title"),
            body = o.optString("body"),
            mood = if (o.isNull("mood")) null else o.optInt("mood"),
            tags = o.optJSONArray("tags").strings(),
            photos = o.optJSONArray("photos").strings(),
            audio = o.optJSONArray("audio")?.let { a -> (0 until a.length()).map { AudioClip.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
            promptId = if (o.isNull("promptId")) null else o.optString("promptId"),
            promptText = if (o.isNull("promptText")) null else o.optString("promptText"),
        )
    }
}

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

data class Mood(val value: Int, val emoji: String, val label: String)

val Moods = listOf(
    Mood(1, "😞", "Rough"),
    Mood(2, "😕", "Low"),
    Mood(3, "😐", "Okay"),
    Mood(4, "🙂", "Good"),
    Mood(5, "😄", "Great"),
)

fun moodOf(v: Int?) = Moods.firstOrNull { it.value == v }

/** A prompt from the built-in library, a guided series, or one the user wrote. */
data class Prompt(
    val id: String,
    val category: String,
    val text: String,
    val followUps: List<String> = emptyList(),
    val custom: Boolean = false,
)

data class GuidedSeries(
    val id: String,
    val title: String,
    val description: String,
    val prompts: List<Prompt>,
)

data class CustomPrompt(val id: String, val category: String, val text: String) {
    fun toJson() = JSONObject().put("id", id).put("category", category).put("text", text)
    fun asPrompt() = Prompt(id, category, text, custom = true)
    companion object {
        fun fromJson(o: JSONObject) = CustomPrompt(o.getString("id"), o.getString("category"), o.getString("text"))
    }
}

/** Everything the prompt generator remembers. */
data class PromptState(
    val favorites: Set<String> = emptySet(),
    val hidden: Set<String> = emptySet(),
    val answered: Map<String, Int> = emptyMap(),
    val custom: List<CustomPrompt> = emptyList(),
    val customCategories: List<String> = emptyList(),
    val seriesStarted: Map<String, Long> = emptyMap(), // seriesId -> start date epochDay
    val dailyDate: Long = -1, // epochDay the daily prompt was chosen
    val dailyId: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("favorites", JSONArray(favorites.toList()))
        .put("hidden", JSONArray(hidden.toList()))
        .put("answered", JSONObject(answered as Map<*, *>))
        .put("custom", JSONArray(custom.map { it.toJson() }))
        .put("customCategories", JSONArray(customCategories))
        .put("seriesStarted", JSONObject(seriesStarted as Map<*, *>))
        .put("dailyDate", dailyDate)
        .put("dailyId", dailyId ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): PromptState {
            val ans = o.optJSONObject("answered")
            val ser = o.optJSONObject("seriesStarted")
            return PromptState(
                favorites = o.optJSONArray("favorites").strings().toSet(),
                hidden = o.optJSONArray("hidden").strings().toSet(),
                answered = ans?.keys()?.asSequence()?.associateWith { ans.getInt(it) } ?: emptyMap(),
                custom = o.optJSONArray("custom")?.let { a -> (0 until a.length()).map { CustomPrompt.fromJson(a.getJSONObject(it)) } } ?: emptyList(),
                customCategories = o.optJSONArray("customCategories").strings(),
                seriesStarted = ser?.keys()?.asSequence()?.associateWith { ser.getLong(it) } ?: emptyMap(),
                dailyDate = o.optLong("dailyDate", -1),
                dailyId = if (o.isNull("dailyId")) null else o.optString("dailyId"),
            )
        }
    }
}
