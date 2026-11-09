package io.ymusic.mse

import android.view.Surface

actual class MseVideoSurface(val surface: Surface? = null) {
    /** C `MseHost*` as a Long, set by bindNativeMse. 0 = none. */
    @Volatile
    var host: Long = 0L

    actual fun setBlitSize(width: Int, height: Int) = Unit

    actual fun presentedFrames(): Int = if (host == 0L) 0 else try {
        MseHostNative.frames(host)
    } catch (_: Throwable) {
        0
    }

    actual fun droppedFrames(): Int = if (host == 0L) 0 else try {
        MseHostNative.droppedFrames(host)
    } catch (_: Throwable) {
        0
    }

    actual fun videoDecoder(): String = if (host == 0L) "" else try {
        MseHostNative.videoDecoder(host)
    } catch (_: Throwable) {
        ""
    }

    actual fun setVolume(volume: Float) {
        if (host == 0L) return
        try {
            MseHostNative.setVolume(host, volume)
        } catch (_: Throwable) {}
    }

    actual fun setAudioEffect(
        bands: FloatArray,
        bass: Float,
        virtualizer: Float,
        preamp: Float,
    ) {
        if (host == 0L) return
        try {
            MseHostNative.setEq(host, bands)
            MseHostNative.setBass(host, bass)
            MseHostNative.setVirtualizer(host, virtualizer)
            MseHostNative.setPreamp(host, preamp)
        } catch (_: Throwable) {}
    }

    actual fun pcmLevels(out: FloatArray): Int =
        if (host == 0L || out.isEmpty()) 0 else try {
            MseHostNative.pcmLevels(host, out)
        } catch (_: Throwable) {
            0
        }

    actual fun audioSessionId(): Int = if (host == 0L) 0 else try {
        MseHostNative.audioSessionId(host)
    } catch (_: Throwable) {
        0
    }

    actual fun videoFailure(): String? = if (host == 0L) null else try {
        MseHostNative.videoFailure(host)
    } catch (_: Throwable) {
        null
    }
}
