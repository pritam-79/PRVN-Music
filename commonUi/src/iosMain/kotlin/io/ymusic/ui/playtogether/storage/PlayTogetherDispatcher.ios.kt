package io.ymusic.ui.playtogether.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
private val ptDbDispatcher: CoroutineDispatcher = newSingleThreadContext("play-together-db")

internal actual fun playTogetherDispatcher(): CoroutineDispatcher = ptDbDispatcher
