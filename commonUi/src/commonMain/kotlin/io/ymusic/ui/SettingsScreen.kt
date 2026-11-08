package io.ymusic.ui
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import io.ymusic.ui.resources.ic_app_icon_dj
import io.ymusic.ui.resources.ic_app_icon_minimal
import io.ymusic.ui.resources.ic_app_icon_neon
import io.ymusic.ui.resources.ic_app_icon_original
import io.ymusic.ui.resources.ic_app_icon_purple
import io.ymusic.ui.resources.settings_app_icon
import io.ymusic.ui.resources.settings_app_icon_dj
import io.ymusic.ui.resources.settings_app_icon_hint
import io.ymusic.ui.resources.settings_app_icon_minimal
import io.ymusic.ui.resources.settings_app_icon_neon
import io.ymusic.ui.resources.settings_app_icon_original
import io.ymusic.ui.resources.settings_app_icon_purple
import io.ymusic.ui.resources.settings_audio_quality
import io.ymusic.ui.resources.settings_audio_quality_high
import io.ymusic.ui.resources.settings_audio_quality_hint
import io.ymusic.ui.resources.settings_audio_quality_low
import io.ymusic.ui.resources.settings_audio_quality_medium
import io.ymusic.ui.resources.settings_notifications_playback
import io.ymusic.ui.resources.settings_notifications_playback_hint
import io.ymusic.ui.resources.settings_notifications_room
import io.ymusic.ui.resources.settings_notifications_room_hint
import io.ymusic.ui.resources.settings_play_together_drift
import io.ymusic.ui.resources.settings_play_together_drift_hint
import io.ymusic.ui.resources.settings_play_together_invites
import io.ymusic.ui.resources.settings_play_together_invites_hint
import io.ymusic.ui.resources.settings_play_together_vis_anyone
import io.ymusic.ui.resources.settings_play_together_vis_invite
import io.ymusic.ui.resources.settings_play_together_vis_private
import io.ymusic.ui.resources.settings_play_together_visibility
import io.ymusic.ui.resources.settings_play_together_visibility_hint
import io.ymusic.ui.resources.settings_section_notifications
import io.ymusic.ui.resources.settings_section_play_together
import io.ymusic.ui.resources.settings_section_updates
import io.ymusic.ui.resources.settings_updates_check
import io.ymusic.ui.resources.settings_updates_check_hint
import io.ymusic.ui.resources.settings_updates_github_releases
import io.ymusic.ui.resources.settings_play_together_backend
import io.ymusic.ui.resources.settings_play_together_backend_hint
import io.ymusic.ui.resources.settings_play_together_backend_local
import io.ymusic.ui.resources.settings_play_together_backend_supabase
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.dynamicengine.yt.YtLocaleOption
import io.ymusic.dynamicengine.yt.YtLocales
import io.ymusic.dynamicengine.yt.applyYtLocale
import io.ymusic.dynamicengine.yt.ytLocaleOptions
import io.ymusic.settings.AppSettings
import io.ymusic.settings.BoolSetting
import io.ymusic.settings.flow
import io.ymusic.settings.put
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.dialog_cancel
import io.ymusic.ui.resources.settings_audio_focus
import io.ymusic.ui.resources.settings_audio_focus_apps
import io.ymusic.ui.resources.settings_audio_focus_calls
import io.ymusic.ui.resources.settings_auto_update
import io.ymusic.ui.resources.settings_auto_update_hint
import io.ymusic.ui.resources.settings_changelog
import io.ymusic.ui.resources.settings_content_locale_hint
import io.ymusic.ui.resources.settings_content_region
import io.ymusic.ui.resources.settings_crash_reports
import io.ymusic.ui.resources.settings_crash_reports_hint
import io.ymusic.ui.resources.settings_debug
import io.ymusic.ui.resources.settings_enable_pip
import io.ymusic.ui.resources.settings_enable_pip_hint
import io.ymusic.ui.resources.settings_eq_hint
import io.ymusic.ui.resources.settings_language
import io.ymusic.ui.resources.settings_licenses
import io.ymusic.ui.resources.settings_licenses_intro
import io.ymusic.ui.resources.settings_section_about
import io.ymusic.ui.resources.settings_section_appearance
import io.ymusic.ui.resources.settings_section_content
import io.ymusic.ui.resources.settings_section_devices
import io.ymusic.ui.resources.settings_section_downloads
import io.ymusic.ui.resources.settings_section_playback
import io.ymusic.ui.resources.settings_section_privacy
import io.ymusic.ui.resources.settings_show_fps
import io.ymusic.ui.resources.settings_show_fps_hint
import io.ymusic.ui.resources.settings_source_code
import io.ymusic.ui.resources.settings_speed_hint
import io.ymusic.ui.resources.settings_sponsor_block
import io.ymusic.ui.resources.settings_sponsor_block_hint
import io.ymusic.ui.resources.settings_stats
import io.ymusic.ui.resources.settings_stats_hint
import io.ymusic.ui.resources.settings_terms
import io.ymusic.ui.resources.settings_theme
import io.ymusic.ui.resources.settings_theme_amoled
import io.ymusic.ui.resources.settings_theme_dark
import io.ymusic.ui.resources.settings_theme_light
import io.ymusic.ui.resources.settings_theme_system
import io.ymusic.ui.resources.settings_versions
import io.ymusic.ui.resources.settings_versions_app
import io.ymusic.ui.resources.settings_versions_engine
import io.ymusic.ui.resources.settings_versions_player
import io.ymusic.ui.resources.sound_eq
import io.ymusic.ui.resources.sound_speed
import kmpcommon.log.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import projectcommon.error.runSuspendCatching
import projectcommon.log.captureUnlessNetwork

@Composable
fun SettingsScreen(graph: AppGraph, signedIn: Boolean = false, onLogin: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val uiLanguage by remember(graph) { graph.settings.flow(AppSettings.uiLanguage) }.collectAsState()
    val theme by remember(graph) { graph.settings.flow(AppSettings.theme) }.collectAsState()
    val accent by remember(graph) { graph.settings.flow(AppSettings.themeAccent) }.collectAsState()
    // The content locale LIVES in the engine; this screen has exactly two jobs: ask the engine for
    // the region menu + the current pick (one call, when the screen opens and again after a pick),
    // and report picks with applyYtLocale. There is ONE language: the app language doubles as the
    // content language (hl, resolved from the picker's supported list), so only the region has its
    // own YouTube-served picker. Until the reply lands (or if it fails) the region row still shows
    // Automatic; it just can't offer alternatives yet.
    var options by remember { mutableStateOf(YtLocales()) }
    var loadingOptions by remember { mutableStateOf(true) }
    var localeRevision by remember { mutableStateOf(0) }
    LaunchedEffect(graph.engines, localeRevision) {
        loadingOptions = true
        runSuspendCatching { graph.engines.ytLocaleOptions() }
            .onSuccess { options = it }
            .onFailure { graph.logger?.fork("settings")?.log(Logger.Level.WARN, "locale options fetch failed", it) }
        loadingOptions = false
    }
    suspend fun applyLocale(gl: String?) {
        runSuspendCatching { graph.engines.applyYtLocale(gl) }
            .onFailure { graph.logger?.fork("settings")?.captureUnlessNetwork(it, "applyYtLocale failed — UI reloads as if it changed") }
        localeRevision++ // re-ask: the labels and the automatic answer follow the new locale
        graph.localeEpoch.value++ // locale-shaped caches (Discover feeds) refetch
    }
    fun pickLocale(gl: String) {
        scope.launch { applyLocale(gl) }
    }

    // Report scroll to the shell so the app bar lifts once the content scrolls under it (every other
    // scrolling screen does this via TrackScroll; without it the bar never elevates).
    val scrollState = rememberScrollState()
    TrackScroll { scrollState.canScrollBackward }
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = DesignSpacing.SettingsSectionGap + LocalMiniPlayerInset.current)) {
        // 1. APPEARANCE
        SettingsSection(Res.string.settings_section_appearance) {
            ThemeRow(
                current = theme,
                onSelect = { value -> scope.launch { graph.settings.put(AppSettings.theme, value) } },
            )
            AppIconPickerRow(graph)
            AccentRow(
                current = accentColorOf(accent),
                onPick = { color ->
                    val hex = accentHexOf(color).takeIf { it != accentHexOf(DefaultAccent) }.orEmpty()
                    scope.launch { graph.settings.put(AppSettings.themeAccent, hex) }
                },
            )
            LanguageRow(
                current = uiLanguage.ifBlank { defaultUiLanguageTag() },
                onSelect = { tag ->
                    scope.launch {
                        withContext(NonCancellable) {
                            graph.settings.put(AppSettings.uiLanguage, tag)
                            applyLocale(gl = null)
                        }
                    }
                },
            )
        }

        // 2. PLAYBACK
        SettingsSection(Res.string.settings_section_playback) {
            AudioQualityRow(graph)
            SoundRow(graph, speed = true)
            SoundRow(graph, speed = false)
            CaptionStyleRow(graph)
            ToggleRow(graph, AppSettings.enablePip, Res.string.settings_enable_pip, Res.string.settings_enable_pip_hint)
            if (graph.audioFocusSetting) AudioFocusRow(graph)
            ToggleRow(
                graph,
                AppSettings.sponsorBlockEnabled,
                Res.string.settings_sponsor_block,
                Res.string.settings_sponsor_block_hint,
            )
            ContentLocaleRow(
                title = stringResource(Res.string.settings_content_region),
                subtitle = stringResource(Res.string.settings_content_locale_hint),
                current = options.gl,
                options = options.regions,
                loading = loadingOptions,
                onSelect = { code -> pickLocale(gl = code) },
            )
            PlayerCacheRow(graph)
        }

        // 3. PLAY TOGETHER
        SettingsSection(Res.string.settings_section_play_together) {
            PlayTogetherVisibilityRow(graph)
            ToggleRow(
                graph,
                AppSettings.playTogetherAllowInvites,
                Res.string.settings_play_together_invites,
                Res.string.settings_play_together_invites_hint,
            )
            PlayTogetherDriftRow(graph)
            PlayTogetherBackendRow(graph)
        }

        // 4. DOWNLOADS
        if (graph.downloads != null) {
            SettingsSection(Res.string.settings_section_downloads) { DownloadSettingsRows(graph) }
        }

        // 5. NOTIFICATIONS
        SettingsSection(Res.string.settings_section_notifications) {
            PlaybackNotificationStatusRow()
            ToggleRow(
                graph,
                AppSettings.roomNotifications,
                Res.string.settings_notifications_room,
                Res.string.settings_notifications_room_hint,
            )
        }

        // 6. PRIVACY
        SettingsSection(Res.string.settings_section_privacy) {
            ToggleRow(graph, AppSettings.crashReports, Res.string.settings_crash_reports, Res.string.settings_crash_reports_hint)
            ToggleRow(graph, AppSettings.stats, Res.string.settings_stats, Res.string.settings_stats_hint)
        }

        // 7. ABOUT PRVN
        SettingsSection(Res.string.settings_section_about) {
            AboutPrvnRow(graph)
            VersionsRow(graph)
            val config by remember(graph) { graph.remoteConfig ?: emptyFlow() }.collectAsState(initial = null)
            val links = AppLinks(config)
            LinkRow(Res.string.settings_source_code, links.sourceCode)
            LinkRow(Res.string.settings_terms, links.terms)
            LicensesRow()
        }

        // 8. UPDATES
        SettingsSection(Res.string.settings_section_updates) {
            UpdatesCheckRow()
            if (graph.updates != null) {
                ToggleRow(graph, AppSettings.autoDownloadUpdates, Res.string.settings_auto_update, Res.string.settings_auto_update_hint)
            }
            val config by remember(graph) { graph.remoteConfig ?: emptyFlow() }.collectAsState(initial = null)
            config?.features?.changelogUrl?.let { LinkRow(Res.string.settings_changelog, it) }
        }

        // DEVICES (if available)
        if (graph.exportSessionCookies != null && graph.scanQrCode != null &&
            graph.confirmDeviceOwner != null && graph.api != null
        ) {
            SettingsSection(Res.string.settings_section_devices) {
                LinkDevicesRow(graph, signedIn = signedIn, onLogin = onLogin)
            }
        }

        // DEBUG
        SettingsSection(Res.string.settings_debug) {
            ToggleRow(graph, AppSettings.showFps, Res.string.settings_show_fps, Res.string.settings_show_fps_hint)
        }
    }
}

@Composable
private fun AppIconPickerRow(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val currentIcon by remember(graph) { graph.settings.flow(AppSettings.appIcon) }.collectAsState()
    val icons = remember {
        listOf(
            Triple("original", Res.string.settings_app_icon_original, Res.drawable.ic_app_icon_original),
            Triple("purple", Res.string.settings_app_icon_purple, Res.drawable.ic_app_icon_purple),
            Triple("neon", Res.string.settings_app_icon_neon, Res.drawable.ic_app_icon_neon),
            Triple("minimal", Res.string.settings_app_icon_minimal, Res.drawable.ic_app_icon_minimal),
            Triple("dj", Res.string.settings_app_icon_dj, Res.drawable.ic_app_icon_dj),
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
    ) {
        Text(
            stringResource(Res.string.settings_app_icon),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
        )
        Text(
            stringResource(Res.string.settings_app_icon_hint),
            style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
            modifier = Modifier.padding(top = DesignSpacing.Space2, bottom = DesignSpacing.Space8),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icons.forEach { (key, labelRes, iconRes) ->
                val selected = currentIcon == key
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { scope.launch { graph.settings.put(AppSettings.appIcon, key) } }
                        .padding(2.dp),
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) DesignColors.Accent else DesignColors.Outline,
                                shape = RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = stringResource(labelRes),
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(11.dp)),
                        )
                    }
                    Text(
                        stringResource(labelRes),
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) DesignColors.Accent else DesignColors.InkSecondary,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val AudioQualityOptions = listOf(
    "high" to Res.string.settings_audio_quality_high,
    "medium" to Res.string.settings_audio_quality_medium,
    "low" to Res.string.settings_audio_quality_low,
)

@Composable
private fun AudioQualityRow(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val current by remember(graph) { graph.settings.flow(AppSettings.audioQuality) }.collectAsState()
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.settings_audio_quality),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(Res.string.settings_audio_quality_hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        Box {
            Text(
                stringResource((AudioQualityOptions.firstOrNull { it.first == current } ?: AudioQualityOptions.first()).second),
                style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                AudioQualityOptions.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            open = false
                            scope.launch { graph.settings.put(AppSettings.audioQuality, value) }
                        },
                    )
                }
            }
        }
    }
}

private val PlayTogetherVisibilityOptions = listOf(
    "ANYONE" to Res.string.settings_play_together_vis_anyone,
    "INVITE" to Res.string.settings_play_together_vis_invite,
    "PRIVATE" to Res.string.settings_play_together_vis_private,
)

@Composable
private fun PlayTogetherVisibilityRow(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val current by remember(graph) { graph.settings.flow(AppSettings.playTogetherDefaultVisibility) }.collectAsState()
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.settings_play_together_visibility),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(Res.string.settings_play_together_visibility_hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        Box {
            Text(
                stringResource((PlayTogetherVisibilityOptions.firstOrNull { it.first == current } ?: PlayTogetherVisibilityOptions.first()).second),
                style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PlayTogetherVisibilityOptions.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            open = false
                            scope.launch { graph.settings.put(AppSettings.playTogetherDefaultVisibility, value) }
                        },
                    )
                }
            }
        }
    }
}

private val PlayTogetherDriftOptions = listOf(
    250 to "250 ms (Low latency)",
    500 to "500 ms (Recommended)",
    750 to "750 ms (Relaxed)",
    1000 to "1000 ms (High latency)",
)

@Composable
private fun PlayTogetherDriftRow(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val current by remember(graph) { graph.settings.flow(AppSettings.playTogetherSyncDriftMs) }.collectAsState()
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.settings_play_together_drift),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(Res.string.settings_play_together_drift_hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        Box {
            Text(
                (PlayTogetherDriftOptions.firstOrNull { it.first == current } ?: PlayTogetherDriftOptions[1]).second,
                style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PlayTogetherDriftOptions.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            open = false
                            scope.launch { graph.settings.put(AppSettings.playTogetherSyncDriftMs, value) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayTogetherBackendRow(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val realtimeUrl by remember(graph) { graph.settings.flow(AppSettings.playTogetherRealtimeUrl) }.collectAsState()
    val realtimeKey by remember(graph) { graph.settings.flow(AppSettings.playTogetherAnonKey) }.collectAsState()
    var openDialog by remember { mutableStateOf(false) }

    val isConfigured = realtimeUrl.isNotBlank() && realtimeKey.isNotBlank()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { openDialog = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.settings_play_together_backend),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(Res.string.settings_play_together_backend_hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        Text(
            if (isConfigured) stringResource(Res.string.settings_play_together_backend_supabase)
            else stringResource(Res.string.settings_play_together_backend_local),
            style = TextStyle(
                fontSize = 13.sp,
                color = if (isConfigured) DesignColors.Accent else DesignColors.InkSecondary,
                fontWeight = if (isConfigured) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }

    if (openDialog) {
        var draftUrl by remember { mutableStateOf(realtimeUrl) }
        var draftKey by remember { mutableStateOf(realtimeKey) }

        AlertDialog(
            onDismissRequest = { openDialog = false },
            title = {
                Text(
                    stringResource(Res.string.settings_play_together_backend),
                    fontWeight = FontWeight.Bold,
                    color = DesignColors.Ink,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your Supabase Realtime endpoint and anon public API key to enable live cross-device group playback sync. Leave blank to use local on-device relay.",
                        style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                    )
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = { draftUrl = it },
                        label = { Text("Supabase URL") },
                        placeholder = { Text("https://your-project.supabase.co") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesignColors.Accent,
                            focusedLabelColor = DesignColors.Accent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftKey,
                        onValueChange = { draftKey = it },
                        label = { Text("Supabase Anon Key") },
                        placeholder = { Text("eyJhbGciOiJIUzI1NiIsIn...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesignColors.Accent,
                            focusedLabelColor = DesignColors.Accent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        openDialog = false
                        scope.launch {
                            graph.settings.put(AppSettings.playTogetherRealtimeUrl, draftUrl.trim())
                            graph.settings.put(AppSettings.playTogetherAnonKey, draftKey.trim())
                            io.ymusic.ui.playtogether.backend.PlayTogetherBackendProvider.configure(
                                draftUrl.trim(),
                                draftKey.trim(),
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draftUrl.isNotBlank() || draftKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                draftUrl = ""
                                draftKey = ""
                                openDialog = false
                                scope.launch {
                                    graph.settings.put(AppSettings.playTogetherRealtimeUrl, "")
                                    graph.settings.put(AppSettings.playTogetherAnonKey, "")
                                    io.ymusic.ui.playtogether.backend.PlayTogetherBackendProvider.configure("", "")
                                }
                            },
                        ) {
                            Text("Reset to Local", color = Color(0xFFF43F5E))
                        }
                    }
                    TextButton(onClick = { openDialog = false }) {
                        Text(stringResource(Res.string.dialog_cancel), color = DesignColors.InkSecondary)
                    }
                }
            },
            containerColor = DesignColors.SettingsCard,
        )
    }
}

@Composable
private fun PlaybackNotificationStatusRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.settings_notifications_playback),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(Res.string.settings_notifications_playback_hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        Text(
            "Active",
            style = TextStyle(fontSize = 13.sp, color = DesignColors.Accent, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun UpdatesCheckRow() {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri("https://github.com/pritam-79/PRVN-Music/releases") }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.settings_updates_check),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(Res.string.settings_updates_check_hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        Text(
            stringResource(Res.string.settings_updates_github_releases),
            style = TextStyle(fontSize = 13.sp, color = DesignColors.Accent),
        )
    }
}

@Composable
private fun AboutPrvnRow(graph: AppGraph) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
    ) {
        Text(
            "About PRVN Music",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
        )
        Text(
            "App information, developer, licences & privacy",
            style = TextStyle(fontSize = 12.sp, color = DesignColors.Accent),
            modifier = Modifier.padding(top = DesignSpacing.Space2),
        )
    }
    if (open) {
        AboutPrvnDialog(graph, onDismiss = { open = false })
    }
}

@Composable
private fun SoundRow(graph: AppGraph, speed: Boolean) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (speed) Res.string.sound_speed else Res.string.sound_eq),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(if (speed) Res.string.settings_speed_hint else Res.string.settings_eq_hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
    }
    if (open) {
        if (speed) PlaybackFxSheet(graph.player, onDismiss = { open = false })
        else EqualizerFxSheet(graph, onDismiss = { open = false })
    }
}

/** A boolean setting as a switch row: tap anywhere to flip, persisted immediately. */
@Composable
internal fun ToggleRow(graph: AppGraph, setting: BoolSetting, title: StringResource, hint: StringResource) {
    val scope = rememberCoroutineScope()
    val enabled by remember(graph, setting) { graph.settings.flow(setting) }.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch) { scope.launch { graph.settings.put(setting, it) } }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(title),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource(hint),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        CompactSwitch(enabled)
    }
}

private val AudioFocusOptions: List<Pair<String, StringResource>> = listOf(
    AppSettings.AUDIO_FOCUS_APPS to Res.string.settings_audio_focus_apps,
    AppSettings.AUDIO_FOCUS_CALLS to Res.string.settings_audio_focus_calls,
)

@Composable
private fun AudioFocusRow(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val current by remember(graph) { graph.settings.flow(AppSettings.audioFocus) }.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { open = true }
                .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        ) {
            Text(
                stringResource(Res.string.settings_audio_focus),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                stringResource((AudioFocusOptions.firstOrNull { it.first == current } ?: AudioFocusOptions.first()).second),
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AudioFocusOptions.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        scope.launch { graph.settings.put(AppSettings.audioFocus, value) }
                    },
                )
            }
        }
    }
}

@Composable
private fun LinkRow(label: StringResource, url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(label),
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(url) }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
    )
}

/** Informational only: the app build and the JS bundle revisions in effect ("—" until a bundle has run). */
@Composable
private fun VersionsRow(graph: AppGraph) {
    val engine by remember(graph) { graph.runningBundles?.engineFlow ?: flowOf(null) }.collectAsState(initial = null)
    val player by remember(graph) { graph.runningBundles?.playerFlow ?: flowOf(null) }.collectAsState(initial = null)
    val parts = listOfNotNull(
        graph.appVersionName?.let { name ->
            stringResource(Res.string.settings_versions_app, name, graph.appVersionCode?.toString() ?: "—")
        },
        stringResource(Res.string.settings_versions_engine, engine?.toString() ?: "—"),
        stringResource(Res.string.settings_versions_player, player?.toString() ?: "—"),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
    ) {
        Text(
            stringResource(Res.string.settings_versions),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
        )
        Text(
            parts.joinToString(" · "),
            style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = DesignSpacing.Space2),
        )
    }
}

/** Settings → Open source licenses: the shipped third-party components ([OssComponents]) in the
 *  ElevatedBarDialog idiom (see [LocalePickerDialog]). */
@Composable
private fun LicensesRow() {
    var open by remember { mutableStateOf(false) }
    Text(
        stringResource(Res.string.settings_licenses),
        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
    )
    if (open) {
        val listState = rememberLazyListState()
        val uriHandler = LocalUriHandler.current
        ElevatedBarDialog(
            title = stringResource(Res.string.settings_licenses),
            onDismiss = { open = false },
            canScrollBackward = listState.canScrollBackward,
            canScrollForward = listState.canScrollForward,
            buttons = { DialogSecondaryButton(stringResource(Res.string.dialog_cancel)) { open = false } },
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                item("intro") {
                    Text(
                        stringResource(Res.string.settings_licenses_intro),
                        style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
                items(OssComponents, key = { it.name }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(c.url) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.name,
                                style = TextStyle(fontSize = 13.sp, color = DesignColors.Ink),
                            )
                            c.scope?.let {
                                Text(
                                    it,
                                    style = TextStyle(fontSize = 11.sp, color = DesignColors.InkMuted),
                                    modifier = Modifier.padding(top = DesignSpacing.Space2),
                                )
                            }
                        }
                        Text(
                            c.license,
                            style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The content-region picker. [current] is the WORKING region — YouTube's own answer, whether
 * IP-derived or following a pick, so there is no separate "automatic" entry: the row always shows
 * a concrete region and selecting one is a plain write. The menu runs to ~100 entries, so picking
 * happens in a dialog, not a dropdown.
 */
@Composable
private fun ContentLocaleRow(
    title: String,
    subtitle: String,
    current: String,
    options: List<YtLocaleOption>,
    loading: Boolean,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    fun labelOf(code: String) = options.firstOrNull { it.code.equals(code, ignoreCase = true) }?.label ?: code
    // Title + caption are one row: the whole two-line block is the hover/click target, and the value
    // sits vertically centred against it (Row-level CenterVertically).
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            )
            Text(
                subtitle,
                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                modifier = Modifier.padding(top = DesignSpacing.Space2),
            )
        }
        Text(
            labelOf(current),
            style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
        )
    }
    if (open) {
        LocalePickerDialog(
            title = title,
            entries = options,
            current = current,
            loading = loading,
            onSelect = { code -> onSelect(code); open = false },
            onDismiss = { open = false },
        )
    }
}

/**
 * A scrollable single-choice dialog, opened on the row that shows the current choice. While the
 * menus are still being fetched and there is nothing to offer beyond Automatic, a spinner says so
 * (a stale-but-usable list from the previous locale is shown instead of the spinner). Custom
 * layout, not [AlertDialog]'s slots (those inset the content into a floating panel): the list is
 * full-bleed on [DesignPalette.Surface], and the fixed title/button bars sit on
 * [DesignPalette.SurfaceElevated] behind a divider, casting a shadow onto the list while content
 * continues past their edge — the ElevatedBarColumn idiom.
 */
@Composable
private fun LocalePickerDialog(
    title: String,
    entries: List<YtLocaleOption>,
    current: String,
    loading: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    fun isCurrent(code: String) = code.equals(current, ignoreCase = true)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = entries.indexOfFirst { isCurrent(it.code) }.coerceAtLeast(0),
    )
    ElevatedBarDialog(
        title = title,
        onDismiss = onDismiss,
        canScrollBackward = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
        buttons = { DialogSecondaryButton(stringResource(Res.string.dialog_cancel), onClick = onDismiss) },
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(entries, key = { it.code }) { option ->
                val selected = isCurrent(option.code)
                Text(
                    option.label,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) DesignColors.Accent else DesignColors.Ink,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option.code) }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            if (loading && entries.size <= 1) {
                item("loading") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = DesignColors.Accent)
                    }
                }
            }
        }
    }
}

/** M3's stock 52×32 Switch reads oversized against the 14sp row text — scaled to row weight.
 *  scale() shrinks the visual only (the layout slot stays M3's), which is fine in a text-tall row. */
@Composable
internal fun CompactSwitch(checked: Boolean) {
    Switch(checked = checked, onCheckedChange = null, modifier = Modifier.scale(0.8f))
}

/** "" = follow system, per [AppSettings.theme]. */
private val ThemeOptions: List<Pair<String, StringResource>> = listOf(
    "" to Res.string.settings_theme_system,
    "light" to Res.string.settings_theme_light,
    "dark" to Res.string.settings_theme_dark,
    "amoled" to Res.string.settings_theme_amoled,
)

@Composable
private fun ThemeRow(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.settings_theme),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            modifier = Modifier.weight(1f),
        )
        // The menu anchors to the value text, so it drops on the right under the current choice —
        // anchored to the row it would open at the far-away left edge of the screen.
        Box {
            Text(
                stringResource((ThemeOptions.firstOrNull { it.first == current } ?: ThemeOptions.first()).second),
                style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                ThemeOptions.forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { onSelect(value); open = false })
                }
            }
        }
    }
}

/** ONE language, always a concrete pick (the first launch seeded it) — no "system" entry. */
@Composable
private fun LanguageRow(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = DesignSpacing.SettingsRowHorizontal, vertical = DesignSpacing.SettingsRowVertical),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.settings_language),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
            modifier = Modifier.weight(1f),
        )
        Text(
            UiLanguages.firstOrNull { it.tag == current }?.nativeName ?: current,
            style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
        )
    }
    if (open) {
        val entries = remember { UiLanguages.map { YtLocaleOption(it.tag, it.nativeName) } }
        LocalePickerDialog(
            title = stringResource(Res.string.settings_language),
            entries = entries,
            current = current,
            loading = false,
            onSelect = { tag -> onSelect(tag); open = false },
            onDismiss = { open = false },
        )
    }
}
