package io.ymusic.mse

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * Plays one MSE host's PCM ring through Media3's [DefaultAudioSink]: a mixer AudioTrack that equalizer
 * apps can attach to, playout timestamps for the host's A/V clock, and a new track after audioserver
 * dies. DefaultAudioSink allows a single looper, so every sink call runs on [thread].
 */
@OptIn(UnstableApi::class)
class MseAudioOutput(private val host: Long) {
    private val thread = HandlerThread("mse-audio", Process.THREAD_PRIORITY_AUDIO).apply { start() }
    private val handler = Handler(thread.looper)
    private val buffer: ByteBuffer = ByteBuffer.allocateDirect(CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    private val state = IntArray(STATE_SIZE)
    private val step = Runnable { step() }
    private lateinit var sink: DefaultAudioSink

    @Volatile
    private var released = false
    private var gen = -1
    private var rate = 0
    private var channels = 0
    private var configured = false
    private var configuredAtMs = 0L
    private var configureWarned = ""
    private var running = false
    private var failing = false

    /** Frames in [buffer] the sink has not taken yet; 0 = [buffer] is free. */
    private var heldFrames = 0
    private var heldSilence = false

    /** Frames handed to the sink since its last flush, silence included: the timestamp base. */
    private var sinkFrames = 0L

    /** Sink frame where this generation starts: 0 after a flush, later on a track kept running. */
    private var baseFrames = 0L

    /** Start padding (see [padToStart]): [padFrames] of silence written after sink frame [padAt]. */
    private var padAt = 0L
    private var padFrames = 0L
    private var starvedSinceMs = 0L
    private var underrunLoggedAtMs = 0L

    /** Highest position reported in this generation: AudioTrack timestamps can step backwards. */
    private var reportedFrames = -1L

    /** Sink frame where this generation's audio ended (see [padToEnd]); -1 while it is still coming. */
    private var endAt = -1L

    @Volatile
    private var currentAudioSessionId = 0

    val audioSessionId: Int
        get() = currentAudioSessionId

    init {
        instances[host] = this
        handler.post {
            try {
                @Suppress("DEPRECATION")
                sink = DefaultAudioSink.Builder()
                    .setAudioTrackBufferSizeProvider(
                        DefaultAudioTrackBufferSizeProvider.Builder()
                            .setMinPcmBufferDurationUs(MIN_TRACK_BUFFER_US)
                            .setMaxPcmBufferDurationUs(MAX_TRACK_BUFFER_US)
                            .build(),
                    )
                    .build()
                sink.setListener(SinkListener())
                sink.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                )
                try {
                    MseHostNative.dacAttach(host, true)
                } catch (_: Throwable) {}
                step()
            } catch (t: Throwable) {
                MseLogBridge.fire(LOG_WARN, "mse audio sink init failed: $t")
            }
        }
    }

    /** Blocks until the sink is gone and the host no longer feeds it; call before destroying the host. */
    fun release() {
        released = true
        instances.remove(host)
        try {
            MseHostNative.dacAttach(host, false)
        } catch (_: Throwable) {}
        val done = CountDownLatch(1)
        handler.post {
            handler.removeCallbacks(step)
            if (::sink.isInitialized) {
                try {
                    sink.reset()
                    sink.release()
                } catch (_: Throwable) {}
            }
            done.countDown()
        }
        try {
            done.await()
        } catch (_: InterruptedException) {}
        thread.quitSafely()
    }

    private fun step() {
        if (released) return
        try {
            val free = heldFrames == 0
            val n = try {
                MseHostNative.dacPoll(host, buffer, if (free) buffer.capacity() else 0, PAUSED_WAIT_MS, state)
            } catch (_: Throwable) {
                POLL_RELEASED
            }
            if (n == POLL_RELEASED) return
            if (n < 0) {
                if (!failing) MseLogBridge.fire(LOG_WARN, "mse audio sink: poll failed ($n), retrying")
                failing = true
                handler.postDelayed(step, RETRY_MS)
                return
            }
            if (!configured && rate > 0 && SystemClock.uptimeMillis() - configuredAtMs >= CONFIGURE_RETRY_MS) {
                configured = configure()
            }
            if (state[GEN] != gen || state[RATE] != rate || state[CHANNELS] != channels) {
                val sameFormat = configured && state[RATE] == rate && state[CHANNELS] == channels
                val hasPending = try { ::sink.isInitialized && sink.hasPendingData() } catch (_: Throwable) { false }
                if (sameFormat && (endAt >= 0 || sinkFrames == baseFrames || !hasPending)) {
                    baseFrames = sinkFrames
                } else {
                    try {
                        if (::sink.isInitialized) sink.flush()
                    } catch (_: Throwable) {}
                    sinkFrames = 0
                    baseFrames = 0
                }
                gen = state[GEN]
                heldFrames = 0
                heldSilence = false
                padAt = baseFrames
                padFrames = 0
                starvedSinceMs = 0
                reportedFrames = -1
                endAt = -1
                if (state[RATE] != rate || state[CHANNELS] != channels || !configured) {
                    rate = state[RATE]
                    channels = state[CHANNELS]
                    configured = configure()
                }
            }
            if (n > 0 && configured && channels > 0) {
                endAt = -1
                buffer.clear()
                buffer.limit(n)
                heldFrames = n / (channels * BYTES_PER_SAMPLE)
                starvedSinceMs = 0
            }
            val run = state[RUN] != 0
            if (run != running) {
                running = run
                try {
                    if (::sink.isInitialized) {
                        if (run) sink.play() else sink.pause()
                    }
                } catch (_: Throwable) {}
            }
            if (n == 0 && heldFrames == 0 && running && padFrames == 0L && reportedFrames <= 0 && sinkFrames > 0 &&
                baseFrames == 0L
            ) {
                val now = SystemClock.uptimeMillis()
                if (starvedSinceMs == 0L) starvedSinceMs = now else if (now - starvedSinceMs >= START_STARVED_MS) padToStart()
            }
            var full = false
            try {
                if (heldFrames > 0 && configured && rate > 0 && ::sink.isInitialized) {
                    val ptsUs = sinkFrames * MICROS_PER_SECOND / rate
                    if (sink.handleBuffer(buffer, ptsUs, 1)) {
                        sinkFrames += heldFrames
                        heldSilence = false
                        heldFrames = 0
                    } else {
                        full = true
                    }
                }
                if (state[DRAIN] != 0 && heldFrames == 0 && sinkFrames > 0 && endAt < 0) padToEnd()
                failing = false
            } catch (error: AudioSink.InitializationException) {
                recover(error)
                return
            } catch (error: AudioSink.WriteException) {
                recover(error)
                return
            } catch (t: Throwable) {
                recover(Exception(t))
                return
            }
            report()
            if (n > 0 && !full) handler.post(step) else handler.postDelayed(step, IDLE_MS)
        } catch (t: Throwable) {
            if (!failing) MseLogBridge.fire(LOG_WARN, "mse audio sink step encountered error: $t")
            failing = true
            if (!released) {
                handler.postDelayed(step, RETRY_MS)
            }
        }
    }

    private fun report() {
        try {
            val raw = playedFrames()
            if (raw >= 0) {
                val played = if (endAt >= 0) minOf(raw, endAt) else raw
                val media = (if (played <= padAt || padFrames == 0L) played else maxOf(padAt, played - padFrames)) - baseFrames
                if (media >= 0) reportedFrames = maxOf(reportedFrames, media)
            }
            val bufferUs = if (configured && ::sink.isInitialized) {
                try {
                    sink.audioTrackBufferSizeUs.takeIf { it != C.TIME_UNSET } ?: 0L
                } catch (_: Throwable) {
                    0L
                }
            } else {
                0L
            }
            val padWritten = if (endAt < 0 && heldSilence) 0L else padFrames
            val written = (if (endAt >= 0) endAt else sinkFrames) - baseFrames - padWritten
            val pending = if (endAt >= 0) {
                raw < endAt
            } else {
                (configured && ::sink.isInitialized && (try { sink.hasPendingData() } catch (_: Throwable) { false })) || heldFrames > 0
            }
            MseHostNative.dacReport(host, gen, reportedFrames, written, pending, bufferUs)
        } catch (_: Throwable) {}
    }

    private fun playedFrames(): Long {
        if (!configured || rate <= 0 || !::sink.isInitialized) return -1
        val positionUs = try {
            sink.getCurrentPositionUs(false)
        } catch (_: Throwable) {
            AudioSink.CURRENT_POSITION_NOT_SET
        }
        if (positionUs == AudioSink.CURRENT_POSITION_NOT_SET) return -1
        return positionUs * rate / MICROS_PER_SECOND
    }

    private fun padToEnd() {
        if (rate <= 0 || channels <= 0) return
        val frameBytes = channels * BYTES_PER_SAMPLE
        if (frameBytes <= 0) return
        val frames = (rate * END_PAD_MS / 1000).coerceAtMost((buffer.capacity() / frameBytes).toLong()).toInt()
        endAt = sinkFrames
        if (frames <= 0) return
        buffer.clear()
        buffer.put(SILENCE, 0, frames * frameBytes)
        buffer.flip()
        heldFrames = frames
        heldSilence = true
    }

    private fun padToStart() {
        if (rate <= 0 || channels <= 0 || !::sink.isInitialized) return
        val bufferUs = try { sink.audioTrackBufferSizeUs } catch (_: Throwable) { C.TIME_UNSET }
        if (bufferUs == C.TIME_UNSET) return
        val frameBytes = channels * BYTES_PER_SAMPLE
        if (frameBytes <= 0) return
        val missing = bufferUs * rate / MICROS_PER_SECOND - sinkFrames
        val frames = (missing.coerceAtLeast(0) + rate / START_PAD_MARGIN_DIVISOR)
            .coerceAtMost((buffer.capacity() / frameBytes).toLong()).toInt()
        buffer.clear()
        buffer.put(SILENCE, 0, frames * frameBytes)
        buffer.flip()
        heldFrames = frames
        heldSilence = true
        padAt = sinkFrames
        padFrames = frames.toLong()
        MseLogBridge.fire(LOG_INFO, "mse audio sink: track not started, padded ${frames * 1000L / rate}ms of silence")
    }

    private fun configure(): Boolean {
        configuredAtMs = SystemClock.uptimeMillis()
        if (rate <= 0 || channels <= 0 || !::sink.isInitialized) return false
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(rate)
            .setChannelCount(channels)
            .build()
        return try {
            sink.configure(AudioSink.AudioSinkConfig.Builder(format).build())
            true
        } catch (error: AudioSink.ConfigurationException) {
            val what = "${rate}Hz/${channels}ch"
            if (configureWarned != what) MseLogBridge.fire(LOG_WARN, "mse audio sink: cannot play $what, retrying: $error")
            configureWarned = what
            false
        } catch (t: Throwable) {
            MseLogBridge.fire(LOG_WARN, "mse audio sink configure failed: $t")
            false
        }
    }

    private fun recover(error: Exception) {
        if (!failing) MseLogBridge.fire(LOG_WARN, "mse audio sink failed, reopening: $error")
        failing = true
        try {
            if (::sink.isInitialized) sink.flush()
        } catch (_: Throwable) {}
        handler.postDelayed(step, RETRY_MS)
    }

    private inner class SinkListener : AudioSink.Listener {
        override fun onPositionDiscontinuity() = Unit

        override fun onUnderrun(bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
            val now = SystemClock.uptimeMillis()
            if (now - underrunLoggedAtMs < UNDERRUN_LOG_INTERVAL_MS) return
            underrunLoggedAtMs = now
            MseLogBridge.fire(LOG_WARN, "mse audio sink underrun buffer=${bufferSizeMs}ms sinceFeed=${elapsedSinceLastFeedMs}ms")
        }

        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = Unit

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            currentAudioSessionId = audioSessionId
            MseLogBridge.fire(LOG_INFO, "mse audio session $audioSessionId")
            try {
                MseHostNative.dacSession(host, audioSessionId)
            } catch (_: Throwable) {}
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<Long, MseAudioOutput>()

        fun getAudioSessionId(host: Long): Int = instances[host]?.audioSessionId ?: 0

        fun releaseAll() {
            instances.values.forEach { it.release() }
            instances.clear()
        }

        const val GEN = 0
        const val RATE = 1
        const val CHANNELS = 2
        const val RUN = 3
        const val DRAIN = 4
        const val STATE_SIZE = 5

        /** dacPoll: the host is released; anything else negative is a JNI buffer error. */
        const val POLL_RELEASED = -1

        const val BYTES_PER_SAMPLE = 2
        const val CHUNK_BYTES = 4096 * 2 * BYTES_PER_SAMPLE
        val SILENCE = ByteArray(CHUNK_BYTES)
        const val MICROS_PER_SECOND = 1_000_000L
        const val IDLE_MS = 10L
        const val RETRY_MS = 100L
        const val PAUSED_WAIT_MS = 200
        const val START_STARVED_MS = 100L
        const val END_PAD_MS = 50L
        const val CONFIGURE_RETRY_MS = 1000L
        const val UNDERRUN_LOG_INTERVAL_MS = 10_000L

        /** Pad margin: 1/50 s. */
        const val START_PAD_MARGIN_DIVISOR = 50

        /** The host's ring is the real buffer; outputs may still enlarge the track (see [padToStart]). */
        const val MIN_TRACK_BUFFER_US = 100_000
        const val MAX_TRACK_BUFFER_US = 200_000

        /** MSE_LOG_INFO / MSE_LOG_WARN. */
        const val LOG_INFO = 1
        const val LOG_WARN = 2
    }
}
