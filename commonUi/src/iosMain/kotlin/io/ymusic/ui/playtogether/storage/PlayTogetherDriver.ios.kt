package io.ymusic.ui.playtogether.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** Native (sqliter) [SqlDriver] for Play Together persistence on iOS. */
fun playTogetherNativeDriver(name: String = "play-together.db"): SqlDriver =
    NativeSqliteDriver(schema = PlayTogetherSchema, name = name)
