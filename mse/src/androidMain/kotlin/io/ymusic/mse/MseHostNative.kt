package io.ymusic.mse

import android.view.Surface

interface MseHostSink {
    fun onPcm(rate: Int, channels: Int, s16: ByteArray)

    fun onProgress(positionSec: Double, durationSec: Double, width: Int, height: Int)
}

actual object MseHostNative {
    private var frameScratch = ByteArray(0)
    private val frameMeta = IntArray(3)

    init {
        try { System.loadLibrary("avutil") } catch (_: Throwable) {}
        try { System.loadLibrary("swresample") } catch (_: Throwable) {}
        try { System.loadLibrary("swscale") } catch (_: Throwable) {}
        try { System.loadLibrary("avcodec") } catch (_: Throwable) {}
        try { System.loadLibrary("avformat") } catch (_: Throwable) {}
        try { System.loadLibrary("mseffmpegjni") } catch (_: Throwable) {}
    }

    @JvmStatic
    actual external fun vtable(): Long

    @JvmStatic
    actual external fun create(): Long

    @JvmStatic
    actual external fun destroy(handle: Long)

    @JvmStatic
    fun createHost(): Long = try { create() } catch (_: Throwable) { 0L }

    @JvmStatic
    fun destroyHost(handle: Long) { try { destroy(handle) } catch (_: Throwable) {} }

    @JvmStatic
    external fun setSink(handle: Long, sink: MseHostSink?)

    @JvmStatic
    external fun dacAttach(handle: Long, attached: Boolean)

    @JvmStatic
    external fun dacPoll(handle: Long, dst: java.nio.ByteBuffer?, cap: Int, waitMs: Int, state: IntArray): Int

    @JvmStatic
    external fun dacReport(handle: Long, gen: Int, position: Long, written: Long, pending: Boolean, bufferUs: Long)

    @JvmStatic
    external fun dacSession(handle: Long, session: Int)

    @JvmStatic
    actual external fun frames(handle: Long): Int

    @JvmStatic
    actual external fun droppedFrames(handle: Long): Int

    @JvmStatic
    actual external fun videoDecoder(handle: Long): String

    @JvmStatic
    actual external fun samplesPlayed(handle: Long): Long

    actual fun copyFrame(handle: Long, afterGen: Int): NativeMseFrame? {
        if (handle == 0L) return null
        return try {
            var n = copyFrameBytes(handle, afterGen, frameMeta, frameScratch)
            if (n <= 0) return null
            if (n > frameScratch.size) {
                frameScratch = ByteArray(n)
                n = copyFrameBytes(handle, afterGen, frameMeta, frameScratch)
                if (n <= 0 || n > frameScratch.size) return null
            }
            NativeMseFrame(frameMeta[0], frameMeta[1], frameMeta[2], frameScratch)
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    external fun copyFrameBytes(handle: Long, afterGen: Int, meta: IntArray, dest: ByteArray): Int

    @JvmStatic
    external fun setVideoSurface(handle: Long, surface: Surface?)

    @JvmStatic
    external fun videoSize(handle: Long, outWh: IntArray): Boolean

    @JvmStatic
    actual external fun setVolume(handle: Long, vol: Float)

    @JvmStatic
    actual external fun setSpeed(handle: Long, speed: Float, tempo: Float)

    @JvmStatic
    actual external fun setPitch(handle: Long, pitch: Float)

    @JvmStatic
    actual external fun setEq(handle: Long, bands: FloatArray)

    @JvmStatic
    actual external fun setBass(handle: Long, db: Float)

    @JvmStatic
    actual external fun setVirtualizer(handle: Long, width: Float)

    @JvmStatic
    actual external fun setPreamp(handle: Long, db: Float)

    @JvmStatic
    actual external fun pcmLevels(handle: Long, out: FloatArray): Int

    @JvmStatic
    fun audioSessionId(handle: Long): Int = MseAudioOutput.getAudioSessionId(handle)

    @JvmStatic
    external fun videoFailure(handle: Long): String?

    @JvmStatic
    external fun setDecoderCaps(rows: IntArray)
}
