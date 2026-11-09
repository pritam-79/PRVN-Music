package io.ymusic.dynamicengine.player

import io.ymusic.mse.MseAudioOutput
import io.ymusic.mse.MseDecoderCaps
import io.ymusic.mse.MseHostNative
import io.ymusic.mse.MseVideoSurface
import io.ymusic.quickjs.QuickJsRuntime
import kmpcommon.log.Logger

private const val DECODER_TABLE_WAIT_MS = 3_000L

internal actual fun bindNativeMse(runtime: QuickJsRuntime, surface: MseVideoSurface): NativeMseHost {
    val host = try {
        MseHostNative.create()
    } catch (t: Throwable) {
        0L
    }
    if (host == 0L) error("mse_host_create failed")
    surface.host = host
    val audioOutput = MseAudioOutput(host)
    try {
        MseHostNative.setVideoSurface(host, surface.surface)
    } catch (_: Throwable) {}
    try {
        runtime.attachMseHost(host, MseHostNative.vtable())
    } catch (_: Throwable) {}
    return object : NativeMseHost {
        override fun setVideoSurface(surface: MseVideoSurface?) {
            try {
                MseHostNative.setVideoSurface(host, surface?.surface)
            } catch (_: Throwable) {}
        }

        override fun setSpeed(speed: Float, tempo: Float) {
            try {
                MseHostNative.setSpeed(host, speed, tempo)
            } catch (_: Throwable) {}
        }

        override suspend fun awaitDecoderTable(logger: Logger?) {
            try {
                MseDecoderCaps.install(DECODER_TABLE_WAIT_MS, logger)
            } catch (_: Throwable) {}
        }

        override fun close() {
            try {
                // A successor session may already own the shared surface.
                if (surface.host == host) surface.host = 0L
                audioOutput.release()
                MseHostNative.setVideoSurface(host, null)
                MseHostNative.destroy(host)
            } catch (_: Throwable) {}
        }
    }
}
