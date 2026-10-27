package com.doctorknepper.personalore.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.doctorknepper.personalore.ui.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * All journal data lives in the app's private storage on the phone:
 *  - entries.json        every entry
 *  - prompts.json        favorites, hidden, answered, custom prompts, series progress
 *  - media/              photos and voice recordings
 *  - shared preferences  look, lock and reminder settings
 * Nothing is ever sent off the device.
 */
object Store {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val io = Executors.newSingleThreadExecutor()

    var entries by mutableStateOf<List<Entry>>(emptyList()); private set
    var promptState by mutableStateOf(PromptState()); private set

    // Settings (observable)
    var colorway by mutableStateOf("amber"); private set
    var themeMode by mutableStateOf(ThemeMode.DARK); private set
    var lockEnabled by mutableStateOf(false); private set
    var biometricEnabled by mutableStateOf(false); private set
    var reminderEnabled by mutableStateOf(false); private set
    var reminderHour by mutableStateOf(21); private set
    var reminderMinute by mutableStateOf(0); private set

    val mediaDir: File get() = File(appContext.filesDir, "media").apply { mkdirs() }
    private val entriesFile get() = File(appContext.filesDir, "entries.json")
    private val promptsFile get() = File(appContext.filesDir, "prompts.json")

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        loadSettings()
        loadData()
    }

    private fun loadSettings() {
        colorway = prefs.getString("colorway", "amber") ?: "amber"
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("themeMode", "DARK")!!) }.getOrDefault(ThemeMode.DARK)
        lockEnabled = prefs.getBoolean("lockEnabled", false)
        biometricEnabled = prefs.getBoolean("biometricEnabled", false)
        reminderEnabled = prefs.getBoolean("reminderEnabled", false)
        reminderHour = prefs.getInt("reminderHour", 21)
        reminderMinute = prefs.getInt("reminderMinute", 0)
    }

    private fun loadData() {
        entries = runCatching {
            val a = JSONArray(entriesFile.readText())
            (0 until a.length()).map { Entry.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedForDisplay()
        promptState = runCatching { PromptState.fromJson(JSONObject(promptsFile.readText())) }.getOrDefault(PromptState())
    }

    private fun List<Entry>.sortedForDisplay() = sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })

    // ---------- entries ----------

    fun entry(id: String) = entries.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString()

    fun saveEntry(e: Entry) {
        val old = entry(e.id)
        entries = (entries.filterNot { it.id == e.id } + e).sortedForDisplay()
        persistEntries()
        // Count a prompt as answered the first time an entry uses it.
        if (e.promptId != null && old?.promptId != e.promptId) markAnswered(e.promptId)
        // Remove media files the entry no longer uses.
        old?.let { o ->
            (o.photos - e.photos.toSet()).forEach { File(mediaDir, it).delete() }
            (o.audio.map { it.file } - e.audio.map { it.file }.toSet()).forEach { File(mediaDir, it).delete() }
        }
    }

    fun deleteEntry(id: String) {
        val e = entry(id) ?: return
        entries = entries.filterNot { it.id == id }
        persistEntries()
        e.photos.forEach { File(mediaDir, it).delete() }
        e.audio.forEach { File(mediaDir, it.file).delete() }
    }

    val allTags: List<String> get() = entries.flatMap { it.tags }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }

    private fun persistEntries() {
        val snapshot = entries
        io.execute {
            val a = JSONArray(snapshot.map { it.toJson() })
            writeAtomic(entriesFile, a.toString())
        }
    }

    /** Copies a picked photo into private storage and returns its file name. */
    fun importPhoto(uri: Uri): String? = runCatching {
        val ext = appContext.contentResolver.getType(uri)?.substringAfter('/')?.take(5) ?: "jpg"
        val name = "photo_${UUID.randomUUID()}.$ext"
        appContext.contentResolver.openInputStream(uri)!!.use { input ->
            File(mediaDir, name).outputStream().use { input.copyTo(it) }
        }
        name
    }.getOrNull()

    fun newAudioFile(): File = File(mediaDir, "voice_${UUID.randomUUID()}.m4a")

    /** Deletes media written for an entry that was then discarded without saving. */
    fun discardMedia(names: Collection<String>) {
        val used = entries.flatMap { it.photos + it.audio.map { a -> a.file } }.toSet()
        names.filterNot { it in used }.forEach { File(mediaDir, it).delete() }
    }

    // ---------- prompts ----------

    fun updatePrompts(f: (PromptState) -> PromptState) {
        promptState = f(promptState)
        val snapshot = promptState
        io.execute { writeAtomic(promptsFile, snapshot.toJson().toString()) }
    }

    private fun markAnswered(id: String) = updatePrompts {
        it.copy(answered = it.answered + (id to ((it.answered[id] ?: 0) + 1)))
    }

    fun toggleFavorite(id: String) = updatePrompts {
        it.copy(favorites = if (id in it.favorites) it.favorites - id else it.favorites + id)
    }

    fun toggleHidden(id: String) = updatePrompts {
        it.copy(hidden = if (id in it.hidden) it.hidden - id else it.hidden + id)
    }

    fun addCustomPrompt(category: String, text: String) = updatePrompts {
        val cats = if (category in PromptLibrary.categoryNames || category in it.customCategories) it.customCategories else it.customCategories + category
        it.copy(custom = it.custom + CustomPrompt("custom:" + newId(), category, text), customCategories = cats)
    }

    fun deleteCustomPrompt(id: String) = updatePrompts { st ->
        st.copy(custom = st.custom.filterNot { it.id == id })
    }

    fun startSeries(id: String) = updatePrompts {
        it.copy(seriesStarted = it.seriesStarted + (id to LocalDate.now().toEpochDay()))
    }

    fun stopSeries(id: String) = updatePrompts { it.copy(seriesStarted = it.seriesStarted - id) }

    /** Picks today's prompt once per day (call outside composition). Prefers prompts not yet answered. */
    fun ensureDailyPrompt() {
        val today = LocalDate.now().toEpochDay()
        val st = promptState
        if (st.dailyDate == today && PromptLibrary.find(st.dailyId, st) != null) return
        val pool = PromptLibrary.allPrompts(st).filter { it.id !in st.hidden }
        if (pool.isEmpty()) return
        val fresh = pool.filter { (st.answered[it.id] ?: 0) == 0 }.ifEmpty { pool }
        val pick = fresh[java.util.Random(today).nextInt(fresh.size)]
        updatePrompts { it.copy(dailyDate = today, dailyId = pick.id) }
    }

    /** Swap today's prompt for another one. */
    fun rerollDailyPrompt() {
        val st = promptState
        val pool = PromptLibrary.allPrompts(st).filter { it.id !in st.hidden && it.id != st.dailyId }
        if (pool.isEmpty()) return
        val fresh = pool.filter { (st.answered[it.id] ?: 0) == 0 }.ifEmpty { pool }
        updatePrompts { it.copy(dailyDate = LocalDate.now().toEpochDay(), dailyId = fresh.random().id) }
    }

    val dailyPrompt: Prompt? get() = promptState.let { st ->
        if (st.dailyDate == LocalDate.now().toEpochDay()) PromptLibrary.find(st.dailyId, st) else null
    }

    // ---------- settings ----------

    fun chooseColorway(id: String) { colorway = id; prefs.edit().putString("colorway", id).apply() }
    fun chooseThemeMode(m: ThemeMode) { themeMode = m; prefs.edit().putString("themeMode", m.name).apply() }

    fun setPin(pin: String) {
        prefs.edit().putString("pinHash", hash(pin)).putInt("pinLength", pin.length).putBoolean("lockEnabled", true).apply()
        lockEnabled = true
    }

    fun disableLock() {
        prefs.edit().remove("pinHash").putBoolean("lockEnabled", false).putBoolean("biometricEnabled", false).apply()
        lockEnabled = false; biometricEnabled = false
    }

    fun checkPin(pin: String) = prefs.getString("pinHash", null) == hash(pin)

    val pinLength: Int get() = prefs.getInt("pinLength", 4)

    fun setBiometric(on: Boolean) { biometricEnabled = on; prefs.edit().putBoolean("biometricEnabled", on).apply() }

    fun setReminder(enabled: Boolean, hour: Int = reminderHour, minute: Int = reminderMinute) {
        reminderEnabled = enabled; reminderHour = hour; reminderMinute = minute
        prefs.edit().putBoolean("reminderEnabled", enabled).putInt("reminderHour", hour).putInt("reminderMinute", minute).apply()
    }

    private fun hash(pin: String): String {
        val salt = prefs.getString("pinSalt", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("pinSalt", it).apply() }
        return MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    // ---------- backup ----------

    /** Writes a .zip with every entry, prompt progress, look settings and all media. */
    fun exportBackup(uri: Uri): Result<Int> = runCatching {
        appContext.contentResolver.openOutputStream(uri)!!.use { os ->
            ZipOutputStream(os).use { zip ->
                fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                put("entries.json", JSONArray(entries.map { it.toJson() }).toString(2).toByteArray())
                put("prompts.json", promptState.toJson().toString(2).toByteArray())
                put("settings.json", JSONObject().put("colorway", colorway).put("themeMode", themeMode.name)
                    .put("app", "PersonaLore").put("backupVersion", 1).toString(2).toByteArray())
                mediaDir.listFiles()?.forEach { f ->
                    zip.putNextEntry(ZipEntry("media/" + f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        }
        entries.size
    }

    /** Replaces everything with the contents of a backup file. */
    fun restoreBackup(uri: Uri): Result<Int> = runCatching {
        var entriesJson: String? = null
        var promptsJson: String? = null
        var settingsJson: String? = null
        val tmp = File(appContext.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        appContext.contentResolver.openInputStream(uri)!!.use { ins ->
            ZipInputStream(ins).use { zip ->
                var ze = zip.nextEntry
                while (ze != null) {
                    val name = ze.name
                    when {
                        name == "entries.json" -> entriesJson = zip.readBytes().decodeToString()
                        name == "prompts.json" -> promptsJson = zip.readBytes().decodeToString()
                        name == "settings.json" -> settingsJson = zip.readBytes().decodeToString()
                        name.startsWith("media/") && !ze.isDirectory -> {
                            val safe = File(name).name // never write outside the folder
                            File(tmp, safe).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    ze = zip.nextEntry
                }
            }
        }
        val parsed = JSONArray(entriesJson ?: error("This file isn't a PersonaLore backup."))
        val newEntries = (0 until parsed.length()).map { Entry.fromJson(parsed.getJSONObject(it)) }
        mediaDir.listFiles()?.forEach { it.delete() }
        tmp.listFiles()?.forEach { it.renameTo(File(mediaDir, it.name)) }
        tmp.deleteRecursively()
        entries = newEntries.sortedForDisplay()
        writeAtomic(entriesFile, JSONArray(entries.map { it.toJson() }).toString())
        promptState = promptsJson?.let { runCatching { PromptState.fromJson(JSONObject(it)) }.getOrNull() } ?: PromptState()
        writeAtomic(promptsFile, promptState.toJson().toString())
        settingsJson?.let { s -> runCatching { val o = JSONObject(s); chooseColorway(o.getString("colorway")); chooseThemeMode(ThemeMode.valueOf(o.getString("themeMode"))) } }
        entries.size
    }

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}
