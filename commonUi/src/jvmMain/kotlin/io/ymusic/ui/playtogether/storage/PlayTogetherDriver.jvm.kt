package io.ymusic.ui.playtogether.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/** JVM (JDBC) [SqlDriver] for Play Together persistence. Pass filesystem path or ":memory:". */
fun playTogetherJdbcDriver(path: String): SqlDriver {
    val inMemory = path == ":memory:"
    val url = if (inMemory) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:$path"
    val properties = Properties().apply {
        if (!inMemory) {
            setProperty("journal_mode", "WAL")
            setProperty("synchronous", "NORMAL")
            setProperty("busy_timeout", "5000")
        }
    }
    return JdbcSqliteDriver(url, properties, PlayTogetherSchema)
}
