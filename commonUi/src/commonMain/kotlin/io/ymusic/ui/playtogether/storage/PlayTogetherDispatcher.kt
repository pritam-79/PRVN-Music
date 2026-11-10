package io.ymusic.ui.playtogether.storage

import kotlinx.coroutines.CoroutineDispatcher

/** Single thread for Play Together SQLite persistence (JdbcSqliteDriver is thread-local). */
internal expect fun playTogetherDispatcher(): CoroutineDispatcher
