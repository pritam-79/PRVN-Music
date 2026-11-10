package io.ymusic.ui.playtogether.storage

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** Android [SqlDriver] for Play Together room/queue/request persistence. Null [name] = in-memory. */
fun playTogetherAndroidDriver(context: Context, name: String? = "play-together.db"): SqlDriver =
    AndroidSqliteDriver(schema = PlayTogetherSchema, context = context, name = name)
