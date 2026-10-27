package com.doctorknepper.personalore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.doctorknepper.personalore.data.AudioClip
import com.doctorknepper.personalore.data.Crypto
import com.doctorknepper.personalore.data.Entry
import com.doctorknepper.personalore.data.KeyWrapper
import com.doctorknepper.personalore.data.Store
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipFile

/** Stand-in for the Android Keystore, which Robolectric doesn't provide. */
class TestKeyWrapper : KeyWrapper {
    private var key = Crypto.newDataKey()
    override fun wrap(plain: ByteArray) = Crypto.encrypt(key, plain)
    override fun unwrap(wrapped: ByteArray) = Crypto.decrypt(key, wrapped)
    override fun reset() { key = Crypto.newDataKey() }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DataSafetyTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val wrapper = TestKeyWrapper()
    private val files get() = ctx.filesDir
    private val prefs get() = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secret = "the lilac bush behind the old house"

    private fun entry(id: String, body: String = secret, date: LocalDate = LocalDate.now(), photos: List<String> = emptyList(), audio: List<AudioClip> = emptyList()) =
        Entry(id, date, 1, 1, "", body, 4, listOf("family"), photos, audio, null, null)

    private fun start() { Store.resetForTest(); Store.init(ctx, wrapper) }

    private fun restart() { Store.flushForTest(); start() }

    private fun clearLockout() = prefs.edit().remove("lockoutUntil").remove("failedAttempts").commit()

    @Before fun setUp() { Store.resetForTest(); files.listFiles()?.forEach { it.deleteRecursively() }; prefs.edit().clear().commit() }
    @After fun tearDown() { Store.resetForTest() }

    @Test fun entriesAreEncryptedOnDisk() {
        start()
        Store.saveEntry(entry("a"))
        Store.flushForTest()
        val raw = File(files, "entries.json").readBytes()
        assertTrue(Crypto.isEncrypted(raw))
        assertFalse(String(raw, Charsets.ISO_8859_1).contains("lilac"))
        restart()
        assertEquals(secret, Store.entry("a")!!.body)
    }

    @Test fun plainFilesFromVersion11AreEncryptedOnUpgrade() {
        File(files, "entries.json").writeText(JSONArray(listOf(entry("old", photos = listOf("p.jpg")).toJson())).toString())
        File(files, "media").mkdirs(); File(files, "media/p.jpg").writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17))
        start()
        assertTrue(Store.unlocked)
        assertEquals(1, Store.entries.size)
        assertTrue(Crypto.isEncrypted(File(files, "entries.json").readBytes()))
        assertTrue(Crypto.isEncrypted(File(files, "media/p.jpg").readBytes()))
        assertEquals(17, Store.readMedia("p.jpg")!!.size)
    }

    @Test fun version11PinLockUpgradesAndGivesRecoveryKey() {
        val hash = MessageDigest.getInstance("SHA-256").digest("salt1234".toByteArray()).joinToString("") { "%02x".format(it) }
        prefs.edit().putBoolean("lockEnabled", true).putString("pinSalt", "salt").putString("pinHash", hash).putInt("pinLength", 4).commit()
        File(files, "entries.json").writeText(JSONArray(listOf(entry("old").toJson())).toString())
        start()
        assertFalse(Store.unlocked)
        assertEquals(Store.PinResult.Wrong, Store.unlockWithPin("0000"))
        val r = Store.unlockWithPin("1234")
        assertTrue(r is Store.PinResult.OkWithNewRecoveryKey)
        assertEquals(1, Store.entries.size)
        assertTrue(Crypto.isEncrypted(File(files, "entries.json").readBytes()))
        assertFalse(prefs.contains("pinHash"))
        Store.lock()
        assertEquals(Store.PinResult.Ok, Store.unlockWithPin("1234"))
    }

    @Test fun pinRecoveryKeyAndLockout() {
        start()
        Store.saveEntry(entry("a"))
        val rk = Store.enableLock("2468")
        assertFalse("no-lock key must be removed", prefs.contains("dekKs"))
        Store.lock()
        assertFalse(Store.unlocked)
        assertTrue(Store.entries.isEmpty())

        // A fresh start with the lock on stays locked.
        restart()
        assertFalse(Store.unlocked)

        repeat(4) { assertEquals(Store.PinResult.Wrong, Store.unlockWithPin("1111")) }
        assertEquals(Store.PinResult.Wrong, Store.unlockWithPin("1111"))
        assertTrue(Store.unlockWithPin("2468") is Store.PinResult.LockedOut)
        clearLockout()
        assertEquals(Store.PinResult.Ok, Store.unlockWithPin("2468"))
        assertEquals(secret, Store.entry("a")!!.body)

        // Forgot PIN: recovery key works even typed sloppily, then a new PIN replaces the old one.
        Store.lock()
        assertFalse(Store.unlockWithRecoveryKey("AAAAA-BBBBB-CCCCC-DDDDD"))
        assertTrue(Store.unlockWithRecoveryKey(rk.lowercase().replace("-", " ")))
        Store.changePin("9753")
        Store.lock(); clearLockout()
        assertEquals(Store.PinResult.Wrong, Store.unlockWithPin("2468"))
        assertEquals(Store.PinResult.Ok, Store.unlockWithPin("9753"))

        // A new recovery key retires the old one.
        val rk2 = Store.regenerateRecoveryKey()
        Store.lock()
        assertFalse(Store.unlockWithRecoveryKey(rk))
        assertTrue(Store.unlockWithRecoveryKey(rk2))

        // Keystore wiped: PIN can't open it, recovery key still can.
        wrapper.reset(); Store.lock(); clearLockout()
        assertEquals(Store.PinResult.KeystoreLost, Store.unlockWithPin("9753"))
        assertTrue(Store.unlockWithRecoveryKey(rk2))
        assertEquals(1, Store.entries.size)

        // Turning the lock off keeps the data readable without a PIN.
        Store.disableLock()
        restart()
        assertTrue(Store.unlocked)
        assertEquals(1, Store.entries.size)
    }

    @Test fun eraseEverythingStartsFresh() {
        start()
        Store.saveEntry(entry("a"))
        Store.enableLock("1234")
        Store.lock()
        Store.eraseEverything()
        assertTrue(Store.unlocked)
        assertFalse(Store.lockEnabled)
        assertTrue(Store.entries.isEmpty())
    }

    @Test fun trashUndoAndPurge() {
        start()
        File(files, "media").mkdirs()
        Store.saveEntry(entry("a", audio = listOf(AudioClip("v.m4a", 1000, "grandma's song"))))
        File(Store.mediaDir, "v.m4a").writeBytes(Crypto.encrypt(Crypto.newDataKey(), byteArrayOf(1))) // placeholder file
        assertTrue(Store.entry("a")!!.searchText.contains("grandma"))

        Store.deleteEntry("a")
        assertTrue(Store.entries.isEmpty()); assertEquals(1, Store.trash.size)
        Store.restoreEntry("a")
        assertEquals(1, Store.entries.size); assertTrue(Store.trash.isEmpty())

        Store.deleteEntry("a")
        Store.deleteForever("a")
        assertTrue(Store.trash.isEmpty())
        assertFalse(File(Store.mediaDir, "v.m4a").exists())

        // Items older than 30 days are purged on the next unlock.
        Store.saveEntry(entry("b").copy(deletedAt = System.currentTimeMillis() - 31L * 86_400_000))
        Store.saveEntry(entry("c").copy(deletedAt = System.currentTimeMillis() - 2L * 86_400_000))
        restart()
        assertNull(Store.entry("b"))
        assertNotNull(Store.entry("c"))
    }

    @Test fun restoreMergesByDefaultAndCanReplace() {
        start()
        Store.saveEntry(entry("a", body = "first"))
        Store.saveEntry(entry("b", body = "second"))
        Store.toggleFavorite("childhood:0")
        val backup = File(ctx.cacheDir, "backup.zip")
        assertEquals(2, Store.exportBackup(Uri.fromFile(backup)).getOrThrow())

        Store.deleteEntry("a"); Store.deleteForever("a")
        Store.saveEntry(entry("c", body = "written after the backup"))
        Store.saveEntry(entry("b", body = "edited after the backup"))

        val merged = Store.restoreBackup(Uri.fromFile(backup), merge = true).getOrThrow()
        assertEquals(1, merged.added); assertEquals(1, merged.skipped)
        assertEquals(setOf("a", "b", "c"), Store.entries.map { it.id }.toSet())
        assertEquals("edited after the backup", Store.entry("b")!!.body)
        assertTrue("childhood:0" in Store.promptState.favorites)

        Store.restoreBackup(Uri.fromFile(backup), merge = false).getOrThrow()
        assertEquals(setOf("a", "b"), Store.entries.map { it.id }.toSet())
        assertEquals("second", Store.entry("b")!!.body)
        restart()
        assertEquals(2, Store.entries.size)
    }

    @Test fun replaceRestoreAndRollbackCanBeUndone() {
        start()
        Store.saveEntry(entry("a", body = "in the backup"))
        val backup = File(ctx.cacheDir, "backup2.zip")
        Store.exportBackup(Uri.fromFile(backup)).getOrThrow()

        // Written after the backup, with a voice note that the backup doesn't have.
        val tmp = Store.newTempAudioFile().apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val voice = Store.adoptAudio(tmp)!!
        Store.saveEntry(entry("b", body = "written after the backup", audio = listOf(AudioClip(voice, 1000, "grandma's story"))))
        Store.flushForTest()

        Store.restoreBackup(Uri.fromFile(backup), merge = false).getOrThrow()
        assertEquals(setOf("a"), Store.entries.map { it.id }.toSet())
        assertNull(Store.readMedia(voice)) // set aside, not readable as live media
        assertNotNull(Store.undoRestoreTakenAt())

        assertEquals(2, Store.undoLastRestore().getOrThrow())
        assertEquals("written after the backup", Store.entry("b")!!.body)
        assertEquals(64, Store.readMedia(voice)!!.size)
        assertNull(Store.undoRestoreTakenAt())

        // Rolling back to a snapshot is undoable too, and survives a restart.
        val snap = Store.snapshots().first().second
        Store.restoreSnapshot(snap).getOrThrow()
        restart()
        assertNotNull(Store.undoRestoreTakenAt())
        Store.undoLastRestore().getOrThrow()
        assertEquals(setOf("a", "b"), Store.entries.map { it.id }.toSet())
        assertEquals(64, Store.readMedia(voice)!!.size)
    }

    @Test fun searchCoversBodiesAndVoiceLabels() {
        start()
        Store.saveEntry(entry("a", body = "We drove to the lake at dawn"))
        Store.saveEntry(entry("b", body = "", audio = listOf(AudioClip("x.m4a", 500, "Grandma's story about the farm"))))
        assertTrue("lake" in Store.entry("a")!!.searchText)
        assertTrue("farm" in Store.entry("b")!!.searchText)
    }

    @Test fun damagedJournalFallsBackToSnapshot() {
        start()
        Store.saveEntry(entry("a"))
        restart() // unlock takes today's snapshot
        assertEquals(1, Store.snapshots().size)
        Store.flushForTest()
        File(files, "entries.json").writeBytes("garbage".toByteArray())
        start()
        assertEquals(1, Store.entries.size)
        assertNotNull(Store.notice)
        assertTrue(files.listFiles()!!.any { it.name.startsWith("entries.json.damaged") })
    }

    @Test fun markdownExportIsReadable() {
        start()
        Store.saveEntry(entry("a").copy(title = "The old house"))
        val out = File(ctx.cacheDir, "md.zip")
        assertEquals(1, Store.exportMarkdown(Uri.fromFile(out)).getOrThrow())
        ZipFile(out).use { z ->
            val journal = z.getInputStream(z.getEntry("journal.md")).readBytes().decodeToString()
            assertTrue(journal.contains("## The old house"))
            assertTrue(journal.contains(secret))
            assertTrue(z.entries().toList().any { it.name.startsWith("entries/") && it.name.endsWith(".md") })
        }
    }

    @Test fun photoImportStripsLocation() {
        start()
        val src = File(ctx.cacheDir, "gps.jpg")
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        src.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(src.absolutePath).apply { setLatLong(37.77, -122.42); saveAttributes() }
        assertNotNull(ExifInterface(src.absolutePath).latLong)

        val name = Store.importPhoto(Uri.fromFile(src))!!
        assertTrue(Crypto.isEncrypted(File(Store.mediaDir, name).readBytes()))
        val stored = File(ctx.cacheDir, "stored.jpg").apply { writeBytes(Store.readMedia(name)!!) }
        assertNull(ExifInterface(stored.absolutePath).latLong)
    }
}
