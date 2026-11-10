package io.ymusic.ui.playtogether.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

private val ptDbDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { r -> Thread(r, "play-together-db").apply { isDaemon = true } }
        .asCoroutineDispatcher()

internal actual fun playTogetherDispatcher(): CoroutineDispatcher = ptDbDispatcher
