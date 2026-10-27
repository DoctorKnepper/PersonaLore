package com.doctorknepper.personalore.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.doctorknepper.personalore.ui.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * All journal data lives in the app's private storage on the phone, encrypted (see [Crypto]):
 *  - entries.json        every entry, including the trash
 *  - prompts.json        favorites, hidden, answered, custom prompts, series progress
 *  - media/              photos and voice recordings
 *  - snapshots/          the last 7 daily copies of entries.json and prompts.json
 *  - shared preferences  look, lock and reminder settings, and the wrapped data key
 * Nothing is ever sent off the device.
 */
object Store {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    lateinit var keyWrapper: KeyWrapper
    private val io = Executors.newSingleThreadExecutor()

    const val TRASH_DAYS = 30L
    const val SNAPSHOTS_KEPT = 7

    private var dataKey: SecretKey? = null

    /** True once the data key is in memory and the journal is loaded. */
    var unlocked by mutableStateOf(false); private set
    /** Set if the journal can't be opened at all (e.g. the phone's keystore lost the key). */
    var openError by mutableStateOf<String?>(null); private set
    /** One-off messages for the user (shown as a snackbar). */
    var notice by mutableStateOf<String?>(null)
    /** A recovery key to show once (after upgrading a 1.1 PIN lock). */
    var recoveryKeyToShow by mutableStateOf<String?>(null)
    /** Set after unlocking with the recovery key: the user must choose a new PIN. */
    var mustSetNewPin by mutableStateOf(false)

    private var allEntries by mutableStateOf<List<Entry>>(emptyList())
    val entries: List<Entry> get() = allEntries.filter { it.deletedAt == null }
    val trash: List<Entry> get() = allEntries.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    var promptState by mutableStateOf(PromptState()); private set

    // Settings (observable)
    var colorway by mutableStateOf("amber"); private set
    var themeMode by mutableStateOf(ThemeMode.DARK); private set
    var lockEnabled by mutableStateOf(false); private set
    var biometricEnabled by mutableStateOf(false); private set
    var reminderEnabled by mutableStateOf(false); private set
    var reminderHour by mutableStateOf(21); private set
    var reminderMinute by mutableStateOf(0); private set
    var blockScreenshots by mutableStateOf(true); private set
    /** Seconds away before the lock comes back. 0 = as soon as you leave the app. */
    var relockAfterSec by mutableStateOf(0); private set

    private val filesDir get() = appContext.filesDir
    val mediaDir: File get() = File(filesDir, "media").apply { mkdirs() }
    private val snapshotDir get() = File(filesDir, "snapshots").apply { mkdirs() }
    private val entriesFile get() = File(filesDir, "entries.json")
    private val promptsFile get() = File(filesDir, "prompts.json")
    private val tempDir get() = File(appContext.cacheDir, "rec").apply { mkdirs() }

    private var initialized = false

    fun init(context: Context, wrapper: KeyWrapper = AndroidKeystoreWrapper()) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        keyWrapper = wrapper
        prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        loadSettings()
        tempDir.listFiles()?.forEach { it.delete() } // never leave unencrypted recordings behind
        if (!lockEnabled) unlockWithoutPin()
    }

    /** Tests only: wait for pending saves, then forget everything in memory so [init] runs again. */
    fun resetForTest() {
        io.submit {}.get()
        initialized = false; dataKey = null; unlocked = false; openError = null; notice = null
        recoveryKeyToShow = null; mustSetNewPin = false
        allEntries = emptyList(); promptState = PromptState()
        MediaCache.clear()
    }

    /** Tests only: wait for pending saves. */
    fun flushForTest() { io.submit {}.get() }

    private fun loadSettings() {
        colorway = prefs.getString("colorway", "amber") ?: "amber"
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString("themeMode", "DARK")!!) }.getOrDefault(ThemeMode.DARK)
        lockEnabled = prefs.getBoolean("lockEnabled", false)
        biometricEnabled = prefs.getBoolean("biometricEnabled", false) && prefs.contains("dekBio")
        reminderEnabled = prefs.getBoolean("reminderEnabled", false)
        reminderHour = prefs.getInt("reminderHour", 21)
        reminderMinute = prefs.getInt("reminderMinute", 0)
        blockScreenshots = prefs.getBoolean("blockScreenshots", true)
        relockAfterSec = prefs.getInt("relockAfterSec", 0)
    }

    // =====================================================================
    // Keys and unlocking
    // =====================================================================

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
    private fun pref(key: String) = prefs.getString(key, null)?.let(::unb64)

    /** 1.1 stored a PIN hash and plain files; the first PIN unlock in 1.2 converts everything. */
    private val legacyPin get() = lockEnabled && !prefs.contains("dekPin") && prefs.contains("pinHash")

    private fun unlockWithoutPin() {
        val wrapped = pref("dekKs")
        if (wrapped == null) {
            // First run (or upgrading from 1.1 without a lock): make a data key.
            val dek = Crypto.newDataKey()
            prefs.edit().putString("dekKs", b64(keyWrapper.wrap(dek.encoded))).commit()
            onUnlocked(dek)
            return
        }
        runCatching { Crypto.keyFrom(keyWrapper.unwrap(wrapped)) }
            .onSuccess { onUnlocked(it) }
            .onFailure { openError = "PersonaLore couldn't open its encryption key on this phone. You can erase the journal and restore it from a backup file." }
    }

    sealed interface PinResult {
        data object Ok : PinResult
        /** Upgraded from 1.1: show this recovery key once. */
        data class OkWithNewRecoveryKey(val recoveryKey: String) : PinResult
        data object Wrong : PinResult
        data class LockedOut(val ms: Long) : PinResult
        data object KeystoreLost : PinResult
    }

    fun lockoutRemainingMs(): Long = (prefs.getLong("lockoutUntil", 0) - System.currentTimeMillis()).coerceAtLeast(0)

    private fun registerFailure() {
        val n = prefs.getInt("failedAttempts", 0) + 1
        val e = prefs.edit().putInt("failedAttempts", n)
        if (n >= 5) e.putLong("lockoutUntil", System.currentTimeMillis() + (30_000L shl (n - 5).coerceAtMost(5))) // 30s, 1m, 2m … 16m
        e.apply()
    }

    private fun clearFailures() = prefs.edit().remove("failedAttempts").remove("lockoutUntil").apply()

    /** Slow (key derivation): call off the main thread. */
    fun unlockWithPin(pin: String): PinResult {
        lockoutRemainingMs().let { if (it > 0) return PinResult.LockedOut(it) }
        if (legacyPin) {
            if (prefs.getString("pinHash", null) != legacyHash(pin)) { registerFailure(); return PinResult.Wrong }
            val dek = pref("dekKs")?.let { runCatching { Crypto.keyFrom(keyWrapper.unwrap(it)) }.getOrNull() } ?: Crypto.newDataKey()
            dataKey = dek
            val rk = writePinWraps(pin, newRecovery = true)!!
            prefs.edit().remove("pinHash").remove("pinSalt").remove("dekKs").commit()
            onUnlocked(dek)
            return PinResult.OkWithNewRecoveryKey(rk)
        }
        val wrapped = pref("dekPin") ?: return PinResult.Wrong
        val ks = runCatching { Crypto.unwrapWithSecret(pin, wrapped) }.getOrElse { registerFailure(); return PinResult.Wrong }
        val dek = runCatching { Crypto.keyFrom(keyWrapper.unwrap(ks)) }.getOrElse { return PinResult.KeystoreLost }
        onUnlocked(dek)
        return PinResult.Ok
    }

    /** Slow: call off the main thread. On success the journal is open; the caller must then ask for a new PIN. */
    fun unlockWithRecoveryKey(input: String): Boolean {
        val wrapped = pref("dekRec") ?: return false
        val dek = runCatching { Crypto.keyFrom(Crypto.unwrapWithSecret(Crypto.normalizeRecoveryKey(input), wrapped)) }.getOrNull() ?: return false
        onUnlocked(dek)
        return true
    }

    val hasRecoveryKey get() = prefs.contains("dekRec")

    /** Cipher to hand to BiometricPrompt for unlocking, or null if fingerprint unlock isn't usable. */
    fun biometricUnlockCipher(): Cipher? {
        if (!biometricEnabled) return null
        val wrapped = pref("dekBio") ?: return null
        return BiometricKey.decryptCipher(wrapped.copyOfRange(0, 12)) ?: run { disableBiometric(); null }
    }

    fun unlockWithBiometric(cipher: Cipher): Boolean {
        val wrapped = pref("dekBio") ?: return false
        val dek = runCatching { Crypto.keyFrom(cipher.doFinal(wrapped, 12, wrapped.size - 12)) }.getOrNull() ?: return false
        onUnlocked(dek)
        return true
    }

    fun biometricEnrollCipher(): Cipher = BiometricKey.encryptCipher()

    fun finishBiometricEnroll(cipher: Cipher) {
        val dek = dataKey ?: return
        prefs.edit().putString("dekBio", b64(cipher.iv + cipher.doFinal(dek.encoded))).putBoolean("biometricEnabled", true).apply()
        biometricEnabled = true
    }

    fun disableBiometric() {
        BiometricKey.delete()
        prefs.edit().remove("dekBio").putBoolean("biometricEnabled", false).apply()
        biometricEnabled = false
    }

    /** Wraps the data key with the PIN (and optionally a fresh recovery key, returned). Requires unlocked. */
    private fun writePinWraps(pin: String, newRecovery: Boolean): String? {
        val dek = dataKey ?: error("Journal is locked")
        val pinWrap = Crypto.wrapWithSecret(pin, keyWrapper.wrap(dek.encoded))
        val e = prefs.edit().putString("dekPin", b64(pinWrap)).putInt("pinLength", pin.length).putBoolean("lockEnabled", true)
        var rk: String? = null
        if (newRecovery || !prefs.contains("dekRec")) {
            rk = Crypto.newRecoveryKey()
            e.putString("dekRec", b64(Crypto.wrapWithSecret(Crypto.normalizeRecoveryKey(rk), dek.encoded)))
        }
        e.remove("dekKs").commit()
        lockEnabled = true
        clearFailures()
        return rk
    }

    /** Turns the lock on. Slow: call off the main thread. Returns the recovery key to show once. */
    fun enableLock(pin: String): String = writePinWraps(pin, newRecovery = true)!!

    /** Slow: call off the main thread. */
    fun changePin(pin: String) { writePinWraps(pin, newRecovery = false) }

    /** Slow: call off the main thread. Replaces the old recovery key, which stops working. */
    fun regenerateRecoveryKey(): String {
        val dek = dataKey ?: error("Journal is locked")
        val rk = Crypto.newRecoveryKey()
        prefs.edit().putString("dekRec", b64(Crypto.wrapWithSecret(Crypto.normalizeRecoveryKey(rk), dek.encoded))).commit()
        return rk
    }

    fun disableLock() {
        val dek = dataKey ?: return
        prefs.edit().putString("dekKs", b64(keyWrapper.wrap(dek.encoded)))
            .remove("dekPin").remove("dekRec").remove("pinLength").remove("pinHash").putBoolean("lockEnabled", false).commit()
        disableBiometric()
        lockEnabled = false
    }

    val pinLength: Int get() = prefs.getInt("pinLength", 4)

    /** Forget the data key and loaded journal. The lock screen will be needed to open it again. */
    fun lock() {
        if (!lockEnabled) return
        unlocked = false
        dataKey = null
        allEntries = emptyList()
        promptState = PromptState()
        MediaCache.clear()
    }

    /** Deletes the whole journal and all keys. Settings like colorway are kept. */
    fun eraseEverything() {
        io.submit {}.get()
        listOf(entriesFile, promptsFile).forEach { it.delete() }
        mediaDir.deleteRecursively(); snapshotDir.deleteRecursively(); undoDir.deleteRecursively()
        filesDir.listFiles()?.filter { it.name.startsWith("entries.json") || it.name.endsWith(".tmp") }?.forEach { it.delete() }
        BiometricKey.delete()
        keyWrapper.reset()
        prefs.edit().remove("dekKs").remove("dekPin").remove("dekRec").remove("dekBio").remove("pinHash").remove("pinSalt")
            .remove("pinLength").remove("failedAttempts").remove("lockoutUntil").remove("lastWrittenDay")
            .putBoolean("lockEnabled", false).putBoolean("biometricEnabled", false).commit()
        lockEnabled = false; biometricEnabled = false
        dataKey = null; unlocked = false; openError = null
        allEntries = emptyList(); promptState = PromptState()
        MediaCache.clear()
        unlockWithoutPin()
    }

    private fun legacyHash(pin: String): String {
        val salt = prefs.getString("pinSalt", "") ?: ""
        return MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun onUnlocked(dek: SecretKey) {
        dataKey = dek
        openError = null
        clearFailures()
        encryptLegacyFiles()
        loadData()
        purgeTrash()
        takeSnapshotIfDue()
        unlocked = true
    }

    // =====================================================================
    // Encrypted files
    // =====================================================================

    private fun key() = dataKey ?: error("Journal is locked")

    private fun readEncrypted(f: File): ByteArray = Crypto.decrypt(key(), f.readBytes())

    private fun writeEncrypted(f: File, plain: ByteArray, k: SecretKey = key()) = writeAtomic(f, Crypto.encrypt(k, plain))

    /** Write to a temp file, flush it to disk, then rename over the old file, so a crash mid-save can't corrupt it. */
    private fun writeAtomic(f: File, bytes: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(f)) { f.delete(); check(tmp.renameTo(f)) { "Couldn't save ${f.name}" } }
    }

    /** Converts files written by 1.1 (plain text) to encrypted files. */
    private fun encryptLegacyFiles() {
        val k = key()
        (listOf(entriesFile, promptsFile) + (mediaDir.listFiles()?.toList() ?: emptyList())).forEach { f ->
            if (f.isFile && !f.name.endsWith(".tmp")) {
                val bytes = f.readBytes()
                if (bytes.isNotEmpty() && !Crypto.isEncrypted(bytes)) writeAtomic(f, Crypto.encrypt(k, bytes))
            }
        }
    }

    private fun parseEntries(bytes: ByteArray): List<Entry> {
        val a = JSONArray(bytes.decodeToString())
        return (0 until a.length()).map { Entry.fromJson(a.getJSONObject(it)) }
    }

    private fun loadData() {
        allEntries = if (!entriesFile.exists()) emptyList() else runCatching { parseEntries(readEncrypted(entriesFile)) }.getOrElse {
            // Damaged file: keep it aside and fall back to the newest snapshot that opens.
            entriesFile.renameTo(File(filesDir, "entries.json.damaged-${System.currentTimeMillis()}"))
            val recovered = snapshots().firstNotNullOfOrNull { (date, dir) ->
                runCatching { date to parseEntries(readEncrypted(File(dir, "entries.json"))) }.getOrNull()
            }
            if (recovered != null) {
                notice = "Your journal file was damaged, so it was restored from the ${recovered.first} snapshot."
                persistEntries()
                recovered.second
            } else {
                notice = "Your journal file was damaged and no snapshot could be opened. Restore a backup from Settings."
                emptyList()
            }
        }
        promptState = runCatching { PromptState.fromJson(JSONObject(readEncrypted(promptsFile).decodeToString())) }.getOrDefault(PromptState())
    }

    // =====================================================================
    // Snapshots
    // =====================================================================

    /** Newest first. */
    fun snapshots(): List<Pair<LocalDate, File>> =
        (snapshotDir.listFiles()?.mapNotNull { d -> runCatching { LocalDate.parse(d.name) to d }.getOrNull() } ?: emptyList())
            .filter { File(it.second, "entries.json").exists() }
            .sortedByDescending { it.first }

    /** Once a day, copy the encrypted journal into snapshots/<date>/ and keep the newest 7. */
    private fun takeSnapshotIfDue() {
        if (!entriesFile.exists()) return
        val dir = File(snapshotDir, LocalDate.now().toString())
        if (File(dir, "entries.json").exists()) return
        dir.mkdirs()
        entriesFile.copyTo(File(dir, "entries.json"), overwrite = true)
        if (promptsFile.exists()) promptsFile.copyTo(File(dir, "prompts.json"), overwrite = true)
        snapshots().drop(SNAPSHOTS_KEPT).forEach { it.second.deleteRecursively() }
    }

    fun snapshotEntryCount(dir: File): Int? =
        runCatching { parseEntries(readEncrypted(File(dir, "entries.json"))).count { it.deletedAt == null } }.getOrNull()

    /** Replaces entries and prompt progress with a snapshot. Off the main thread. Can be undone. */
    fun restoreSnapshot(dir: File): Result<Int> = runOnIo {
        runCatching {
            val e = parseEntries(readEncrypted(File(dir, "entries.json")))
            val p = File(dir, "prompts.json").takeIf { it.exists() }?.let { PromptState.fromJson(JSONObject(readEncrypted(it).decodeToString())) }
            saveUndoCopy(moveMedia = false)
            allEntries = e
            writeEncrypted(entriesFile, JSONArray(e.map { it.toJson() }).toString().toByteArray())
            if (p != null) { promptState = p; writeEncrypted(promptsFile, p.toJson().toString().toByteArray()) }
            updateLastWritten()
            e.count { it.deletedAt == null }
        }
    }

    // ---------- undo for "replace" restores and roll-backs ----------

    private val undoDir get() = File(filesDir, "before-restore")

    /**
     * Keeps the journal as it was right before a replace or roll-back, so it can be put back.
     * [moveMedia] moves the photos and voice notes aside too (a replace restore would otherwise delete them).
     */
    private fun saveUndoCopy(moveMedia: Boolean) {
        undoDir.deleteRecursively()
        undoDir.mkdirs()
        if (entriesFile.exists()) entriesFile.copyTo(File(undoDir, "entries.json"))
        if (promptsFile.exists()) promptsFile.copyTo(File(undoDir, "prompts.json"))
        val media = File(undoDir, "media").apply { mkdirs() }
        if (moveMedia) mediaDir.listFiles()?.forEach { it.renameTo(File(media, it.name)) }
        File(undoDir, "taken").writeText(System.currentTimeMillis().toString())
    }

    /** When the undo copy was taken, or null if there's nothing to undo. */
    fun undoRestoreTakenAt(): Long? =
        File(undoDir, "taken").takeIf { it.exists() && File(undoDir, "entries.json").exists() }?.readText()?.toLongOrNull()

    /** Puts the journal back how it was before the last replace or roll-back. Off the main thread. */
    fun undoLastRestore(): Result<Int> = runOnIo {
        runCatching {
            val e = parseEntries(readEncrypted(File(undoDir, "entries.json")))
            val p = File(undoDir, "prompts.json").takeIf { it.exists() }?.let { PromptState.fromJson(JSONObject(readEncrypted(it).decodeToString())) }
            File(undoDir, "media").listFiles()?.forEach { f -> val dest = File(mediaDir, f.name); if (!dest.exists()) f.renameTo(dest) }
            allEntries = e
            writeEncrypted(entriesFile, JSONArray(e.map { it.toJson() }).toString().toByteArray())
            if (p != null) { promptState = p; writeEncrypted(promptsFile, p.toJson().toString().toByteArray()) }
            undoDir.deleteRecursively()
            deleteUnusedMedia(mediaDir.listFiles()?.map { it.name } ?: emptyList())
            MediaCache.clear()
            updateLastWritten()
            e.count { it.deletedAt == null }
        }
    }

    // =====================================================================
    // Entries and trash
    // =====================================================================

    fun entry(id: String) = allEntries.firstOrNull { it.id == id }

    fun newId(): String = UUID.randomUUID().toString()

    fun saveEntry(e: Entry) {
        val old = entry(e.id)
        allEntries = allEntries.filterNot { it.id == e.id } + e
        persistEntries()
        // Count a prompt as answered the first time an entry uses it.
        if (e.promptId != null && old?.promptId != e.promptId) markAnswered(e.promptId)
        old?.let { o -> deleteUnusedMedia((o.photos - e.photos.toSet()) + (o.audio.map { it.file } - e.audio.map { it.file }.toSet())) }
    }

    /** Moves an entry to the trash. */
    fun deleteEntry(id: String) = setDeleted(id, System.currentTimeMillis())

    fun restoreEntry(id: String) = setDeleted(id, null)

    private fun setDeleted(id: String, at: Long?) {
        val e = entry(id) ?: return
        allEntries = allEntries.map { if (it.id == id) e.copy(deletedAt = at) else it }
        persistEntries()
    }

    fun deleteForever(id: String) {
        val e = entry(id) ?: return
        allEntries = allEntries.filterNot { it.id == id }
        persistEntries()
        deleteUnusedMedia(e.photos + e.audio.map { it.file })
    }

    fun emptyTrash() = trash.forEach { deleteForever(it.id) }

    private fun purgeTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_DAYS * 86_400_000
        allEntries.filter { (it.deletedAt ?: Long.MAX_VALUE) < cutoff }.forEach { deleteForever(it.id) }
    }

    private fun deleteUnusedMedia(names: Collection<String>) {
        val used = allEntries.flatMap { it.photos + it.audio.map { a -> a.file } }.toSet()
        names.filterNot { it in used }.forEach { File(mediaDir, it).delete(); MediaCache.remove(it) }
    }

    /** Deletes media written for an entry that was then discarded without saving. */
    fun discardMedia(names: Collection<String>) = deleteUnusedMedia(names)

    val allTags: List<String> get() = entries.flatMap { it.tags }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }

    private fun persistEntries() {
        val snapshot = allEntries
        val k = dataKey ?: return
        updateLastWritten()
        io.execute {
            takeSnapshotIfDue()
            writeEncrypted(entriesFile, JSONArray(snapshot.map { it.toJson() }).toString().toByteArray(), k)
        }
    }

    /** Lets the reminder know you've written today without needing to unlock the journal. */
    private fun updateLastWritten() {
        val last = entries.maxOfOrNull { it.date }
        prefs.edit().putString("lastWrittenDay", last?.toString()).apply()
    }

    val wroteToday: Boolean get() = prefs.getString("lastWrittenDay", null) == LocalDate.now().toString()

    // =====================================================================
    // Media
    // =====================================================================

    /** Copies a picked photo in: fixes rotation, shrinks it, strips EXIF (including GPS), and encrypts it. */
    fun importPhoto(uri: Uri): String? = runCatching {
        val jpeg = PhotoImport.process(appContext, uri)
        val name = "photo_${UUID.randomUUID()}.jpg"
        writeEncrypted(File(mediaDir, name), jpeg)
        name
    }.getOrNull()

    fun newTempAudioFile(): File = File(tempDir, "rec_${UUID.randomUUID()}.m4a")

    /** Encrypts a finished recording into the journal and deletes the plain temp file. */
    fun adoptAudio(temp: File): String? = runCatching {
        val name = "voice_${UUID.randomUUID()}.m4a"
        writeEncrypted(File(mediaDir, name), temp.readBytes())
        name
    }.also { temp.delete() }.getOrNull()

    fun readMedia(name: String): ByteArray? = runCatching { readEncrypted(File(mediaDir, name)) }.getOrNull()

    // =====================================================================
    // Prompts
    // =====================================================================

    fun updatePrompts(f: (PromptState) -> PromptState) {
        promptState = f(promptState)
        val snapshot = promptState
        val k = dataKey ?: return
        io.execute { writeEncrypted(promptsFile, snapshot.toJson().toString().toByteArray(), k) }
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

    fun deleteCustomPrompt(id: String) = updatePrompts { st -> st.copy(custom = st.custom.filterNot { it.id == id }) }

    fun startSeries(id: String) = updatePrompts { it.copy(seriesStarted = it.seriesStarted + (id to LocalDate.now().toEpochDay())) }

    fun stopSeries(id: String) = updatePrompts { it.copy(seriesStarted = it.seriesStarted - id) }

    /** Picks today's prompt once per day (call outside composition). Prefers prompts not yet answered. */
    fun ensureDailyPrompt() {
        if (!unlocked) return
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

    // =====================================================================
    // Settings
    // =====================================================================

    fun chooseColorway(id: String) { colorway = id; prefs.edit().putString("colorway", id).apply() }
    fun chooseThemeMode(m: ThemeMode) { themeMode = m; prefs.edit().putString("themeMode", m.name).apply() }
    fun chooseBlockScreenshots(on: Boolean) { blockScreenshots = on; prefs.edit().putBoolean("blockScreenshots", on).apply() }
    fun setRelockAfter(sec: Int) { relockAfterSec = sec; prefs.edit().putInt("relockAfterSec", sec).apply() }

    fun setReminder(enabled: Boolean, hour: Int = reminderHour, minute: Int = reminderMinute) {
        reminderEnabled = enabled; reminderHour = hour; reminderMinute = minute
        prefs.edit().putBoolean("reminderEnabled", enabled).putInt("reminderHour", hour).putInt("reminderMinute", minute).apply()
    }

    // =====================================================================
    // Backup, restore and export
    // =====================================================================

    /** Runs on the save thread so it can't interleave with a pending save. Call from a background coroutine. */
    private fun <T> runOnIo(block: () -> T): T = io.submit(Callable(block)).get()

    /**
     * Writes a .zip with every entry (and the trash), prompt progress, look settings and all photos and
     * voice notes. The backup is NOT encrypted, so it can be restored on a new phone; keep it somewhere private.
     */
    fun exportBackup(uri: Uri): Result<Int> = runOnIo {
        runCatching {
            appContext.contentResolver.openOutputStream(uri)!!.use { os ->
                ZipOutputStream(os).use { zip ->
                    fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                    put("entries.json", JSONArray(allEntries.map { it.toJson() }).toString(2).toByteArray())
                    put("prompts.json", promptState.toJson().toString(2).toByteArray())
                    put("settings.json", JSONObject().put("colorway", colorway).put("themeMode", themeMode.name)
                        .put("app", "PersonaLore").put("backupVersion", 2).toString(2).toByteArray())
                    mediaDir.listFiles()?.filterNot { it.name.endsWith(".tmp") }?.forEach { f -> put("media/" + f.name, readEncrypted(f)) }
                }
            }
            entries.size
        }
    }

    data class RestoreResult(val added: Int, val skipped: Int)

    /**
     * Restores a backup. Merge (the default) adds entries that aren't already here and keeps everything else;
     * replace swaps the whole journal for the backup's contents.
     */
    fun restoreBackup(uri: Uri, merge: Boolean): Result<RestoreResult> = runOnIo {
        val staging = File(filesDir, "restore_staging").apply { deleteRecursively(); mkdirs() }
        runCatching {
            var entriesJson: String? = null
            var promptsJson: String? = null
            var settingsJson: String? = null
            appContext.contentResolver.openInputStream(uri)!!.use { ins ->
                ZipInputStream(ins).use { zip ->
                    var ze = zip.nextEntry
                    while (ze != null) {
                        val name = ze.name
                        when {
                            name == "entries.json" -> entriesJson = zip.readBytes().decodeToString()
                            name == "prompts.json" -> promptsJson = zip.readBytes().decodeToString()
                            name == "settings.json" -> settingsJson = zip.readBytes().decodeToString()
                            // Encrypt media as it comes in; File(name).name stops paths escaping the folder.
                            name.startsWith("media/") && !ze.isDirectory -> writeEncrypted(File(staging, File(name).name), zip.readBytes())
                        }
                        ze = zip.nextEntry
                    }
                }
            }
            val incoming = parseEntries((entriesJson ?: error("This file isn't a PersonaLore backup.")).toByteArray())
            val incomingPrompts = promptsJson?.let { runCatching { PromptState.fromJson(JSONObject(it)) }.getOrNull() } ?: PromptState()
            val result: RestoreResult
            if (merge) {
                val have = allEntries.map { it.id }.toSet()
                val add = incoming.filter { it.id !in have }
                allEntries = allEntries + add
                staging.listFiles()?.forEach { f -> val dest = File(mediaDir, f.name); if (!dest.exists()) f.renameTo(dest) }
                promptState = mergePrompts(promptState, incomingPrompts)
                result = RestoreResult(add.size, incoming.size - add.size)
            } else {
                saveUndoCopy(moveMedia = true) // instead of deleting, so the replace can be undone
                staging.listFiles()?.forEach { it.renameTo(File(mediaDir, it.name)) }
                allEntries = incoming
                promptState = incomingPrompts
                settingsJson?.let { s -> runCatching { val o = JSONObject(s); chooseColorway(o.getString("colorway")); chooseThemeMode(ThemeMode.valueOf(o.getString("themeMode"))) } }
                result = RestoreResult(incoming.size, 0)
            }
            MediaCache.clear()
            writeEncrypted(entriesFile, JSONArray(allEntries.map { it.toJson() }).toString().toByteArray())
            writeEncrypted(promptsFile, promptState.toJson().toString().toByteArray())
            updateLastWritten()
            result
        }.also { staging.deleteRecursively() }
    }

    private fun mergePrompts(a: PromptState, b: PromptState): PromptState {
        val customIds = a.custom.map { it.id }.toSet()
        return a.copy(
            favorites = a.favorites + b.favorites,
            hidden = a.hidden + b.hidden,
            answered = (a.answered.keys + b.answered.keys).associateWith { maxOf(a.answered[it] ?: 0, b.answered[it] ?: 0) },
            custom = a.custom + b.custom.filter { it.id !in customIds },
            customCategories = (a.customCategories + b.customCategories).distinct(),
            seriesStarted = b.seriesStarted + a.seriesStarted,
        )
    }

    /** A human-readable copy of the journal: Markdown files plus the photos and voice notes they link to. */
    fun exportMarkdown(uri: Uri): Result<Int> = runOnIo {
        runCatching {
            val list = entries.sortedWith(compareBy<Entry> { it.date }.thenBy { it.createdAt })
            appContext.contentResolver.openOutputStream(uri)!!.use { os ->
                ZipOutputStream(os).use { zip ->
                    fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                    val used = mutableSetOf<String>()
                    val all = StringBuilder("# PersonaLore journal\n\nExported ${LocalDate.now()} · ${list.size} entries\n")
                    list.forEachIndexed { i, e ->
                        all.append("\n---\n\n").append(Markdown.entry(e, mediaPrefix = "media/"))
                        val file = "entries/${e.date}-${"%03d".format(i + 1)}-${Markdown.slug(e)}.md"
                        put(file, Markdown.entry(e, mediaPrefix = "../media/").toByteArray())
                        used += e.photos + e.audio.map { it.file }
                    }
                    put("journal.md", all.toString().toByteArray())
                    used.forEach { n -> readMedia(n)?.let { put("media/$n", it) } }
                }
            }
            list.size
        }
    }
}
