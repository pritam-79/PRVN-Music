package io.ymusic.mse

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays one MSE host's decoded PCM stream through an Android [AudioTrack].
 * Implements [MseHostSink] to receive decoded PCM chunks directly from FFmpeg in native C.
 */
class MseAudioOutput(private val host: Long) : MseHostSink {
    @Volatile
    private var released = false

    @Volatile
    private var track: AudioTrack? = null
    private var currentRate = 0
    private var currentChannels = 0

    init {
        instances[host] = this
    }

    override fun onPcm(rate: Int, channels: Int, s16: ByteArray) {
        if (released || s16.isEmpty() || rate <= 0 || channels <= 0) return
        try {
            var current = track
            if (current == null || currentRate != rate || currentChannels != channels) {
                current?.runCatching {
                    pause()
                    flush()
                    release()
                }
                val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                val minBuf = AudioTrack.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
                val bufSize = maxOf(minBuf * 4, 32768)
                current = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(channelMask)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                current.play()
                track = current
                currentRate = rate
                currentChannels = channels
            }
            track?.write(s16, 0, s16.size)
        } catch (_: Throwable) {
            // Suppress non-fatal audio pipeline write exceptions
        }
    }

    override fun onProgress(positionSec: Double, durationSec: Double, width: Int, height: Int) {
        // Native progress notification hook
    }

    fun release() {
        released = true
        instances.remove(host)
        val current = track
        track = null
        try {
            current?.stop()
            current?.release()
        } catch (_: Throwable) {}
    }

    val audioSessionId: Int
        get() = track?.audioSessionId ?: 0

    companion object {
        private val instances = ConcurrentHashMap<Long, MseAudioOutput>()

        fun getAudioSessionId(host: Long): Int = instances[host]?.audioSessionId ?: 0

        fun releaseAll() {
            instances.values.forEach { it.release() }
            instances.clear()
        }
    }
}
