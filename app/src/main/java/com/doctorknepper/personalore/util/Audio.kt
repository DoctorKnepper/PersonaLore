package com.doctorknepper.personalore.util

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records voice notes as AAC in .m4a files. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    fun start(out: File): Boolean = runCatching {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r; file = out; startedAt = System.currentTimeMillis()
        true
    }.getOrElse { recorder?.release(); recorder = null; out.delete(); false }

    /** Returns the file and its duration, or null if nothing usable was recorded. */
    fun stop(): Pair<File, Long>? {
        val r = recorder ?: return null
        val f = file
        val dur = System.currentTimeMillis() - startedAt
        recorder = null; file = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || f == null || dur < 500) { f?.delete(); return null }
        return f to dur
    }

    fun cancel() { stop()?.first?.delete() }
}

/** Plays a decrypted voice note straight from memory, so no plain copy is written to disk. */
private class BytesDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val n = minOf(size.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }
    override fun getSize() = data.size.toLong()
    override fun close() {}
}

/** One shared player so only one voice note plays at a time. */
object VoicePlayer {
    private var player: MediaPlayer? = null
    var playingFile: String? = null; private set
    private var onDone: (() -> Unit)? = null

    fun play(name: String, done: () -> Unit) {
        stop()
        val bytes = com.doctorknepper.personalore.data.Store.readMedia(name)
        if (bytes == null) { done(); return }
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(BytesDataSource(bytes))
                setOnCompletionListener { stop() }
                prepare(); start()
            }
            playingFile = name
            onDone = done
        }.onFailure { stop(); done() }
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        playingFile = null
        onDone?.invoke(); onDone = null
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
