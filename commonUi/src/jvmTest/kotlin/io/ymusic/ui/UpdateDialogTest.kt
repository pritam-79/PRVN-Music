package io.ymusic.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import io.ymusic.dynamicengine.DynamicEngineManager
import io.ymusic.dynamicengine.embeddedEngineJsSource
import io.ymusic.settings.InMemorySettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import java.util.Locale
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class UpdateDialogTest {

    private class FakeUpdates(initial: AppUpdateStatus) : AppUpdates {
        override val status = MutableStateFlow(initial)
        override val blockedReason: StringResource? = null
        var downloadsRequested = 0

        override fun download() {
            downloadsRequested++
        }

        override fun installAndRestart() {}
        override suspend fun checkNow(): UpdateCheck = UpdateCheck.Found
    }

    private fun testOffer(download: UpdateDownload) = AppUpdateStatus.Offered(
        versionCode = 16,
        versionName = "0.13",
        notes = "Bug fixes and notifications",
        changelogUrl = null,
        installInApp = true,
        downloadPage = "https://github.com/pritam-79/PRVN-Music/releases",
        download = download,
        forceUpdate = false,
    )

    @Test
    fun update_dialog_is_visible_during_background_autodownload_and_shows_retry_on_failure_and_reopens_on_request() {
        Locale.setDefault(Locale.ENGLISH)
        val client = CannedHttp { req, _ -> textResponse(req, "{}", "application/json") }
        val engines = DynamicEngineManager(client, appVersion = 1, jsSource = embeddedEngineJsSource())
        runBlocking { engines.start() }
        val fakeUpdates = FakeUpdates(testOffer(UpdateDownload.Running(done = 512_000L, total = 1_024_000L)))
        try {
            runComposeUiTest {
                val graph = AppGraph(
                    settings = InMemorySettingsStore(),
                    engines = engines,
                    player = StubPlayer(),
                    updates = fakeUpdates,
                    playerSurface = {},
                )
                setContent {
                    val externalPrompt by graph.updatePromptRequests.collectAsState()
                    UpdateDialog(graph, recheck = externalPrompt)
                }

                // Auto-download on startup (recheck = 0) must still show the update dialog and progress.
                waitUntilAtLeastOneExists(hasText("PRVN Music Update"), 5_000)
                onNodeWithText("Downloading...").assertExists()

                // Auto/background download failure must also be surfaced with Retry, not hidden.
                fakeUpdates.status.value = testOffer(UpdateDownload.Failed("E91012"))
                waitUntilAtLeastOneExists(hasText("Retry"), 5_000)

                // Dismissing hides it, and notification/settings prompt re-opens it.
                onNodeWithText("Later").performClick()
                waitForIdle()
                onNodeWithText("PRVN Music Update").assertDoesNotExist()

                graph.requestUpdatePrompt()
                waitUntilAtLeastOneExists(hasText("PRVN Music Update"), 5_000)
            }
        } finally {
            runBlocking { engines.close() }
        }
    }
}
