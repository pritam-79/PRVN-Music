package io.ymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.update_allow_install
import io.ymusic.ui.resources.update_ask_later
import io.ymusic.ui.resources.update_available_title
import io.ymusic.ui.resources.update_check
import io.ymusic.ui.resources.update_check_failed
import io.ymusic.ui.resources.update_checking
import io.ymusic.ui.resources.update_close
import io.ymusic.ui.resources.update_download
import io.ymusic.ui.resources.update_download_in_browser
import io.ymusic.ui.resources.update_downloading
import io.ymusic.ui.resources.update_failed
import io.ymusic.ui.resources.update_full_changelog
import io.ymusic.ui.resources.update_install_now
import io.ymusic.ui.resources.update_install_permission
import io.ymusic.ui.resources.update_install_permission_allow
import io.ymusic.ui.resources.update_installing
import io.ymusic.ui.resources.update_manual_hint
import io.ymusic.ui.resources.update_official_site
import io.ymusic.ui.resources.update_ready
import io.ymusic.ui.resources.update_up_to_date
import io.ymusic.ui.resources.update_version
import io.ymusic.ui.resources.ymusic_logo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import projectcommon.error.DISK_FULL_CODE
import projectcommon.error.NETWORK_CODE

/**
 * Custom PRVN Music in-app update dialog displaying the brand mark, version details,
 * what's new changelog, download progress, and mandatory update enforcement.
 */
@Composable
fun UpdateDialog(graph: AppGraph, recheck: Int = 0) {
    val updates = graph.updates ?: return
    val status by updates.status.collectAsState()
    val uriHandler = LocalUriHandler.current
    var dismissedVersion by remember(recheck) { mutableStateOf<Int?>(null) }
    var requestedVersion by remember { mutableStateOf<Int?>(null) }
    val offer = status as? AppUpdateStatus.Offered ?: return
    val requested = requestedVersion == offer.versionCode
    val download = offer.download
    LaunchedEffect(requested && download is UpdateDownload.Ready) {
        if (requested && download is UpdateDownload.Ready) dismissedVersion = null
    }
    if (!offer.forceUpdate && offer.versionCode == dismissedVersion) return
    if (!requested && download is UpdateDownload.Running && recheck == 0) return

    val blocked = updates.blockedReason
    val inPlace = blocked == null && offer.installInApp
    val askLater: () -> Unit = {
        if (!offer.forceUpdate) {
            dismissedVersion = offer.versionCode
        }
    }
    val openDownloadPage: (() -> Unit)? = offer.downloadPage?.let { url -> { uriHandler.openUri(url) } }

    ElevatedBarDialog(
        title = "PRVN Music Update",
        onDismiss = askLater,
        dismissOnClickOutside = false,
        canScrollBackward = false,
        canScrollForward = false,
        buttons = {
            if (!offer.forceUpdate) {
                DialogSecondaryButton("Later", onClick = askLater)
            }
            when {
                !inPlace -> TextButton(onClick = { openDownloadPage?.invoke() }, enabled = openDownloadPage != null) {
                    Text("Update Now")
                }
                download is UpdateDownload.NeedsInstallPermission -> TextButton(onClick = { updates.allowInstall() }) {
                    Text(stringResource(Res.string.update_allow_install))
                }
                download is UpdateDownload.Ready || download is UpdateDownload.InstallFailed -> TextButton(onClick = { updates.installAndRestart() }) {
                    Text("Install Now")
                }
                download is UpdateDownload.Installing || (requested && download is UpdateDownload.Running) -> TextButton(onClick = {}, enabled = false) {
                    Text("Installing...")
                }
                else -> TextButton(
                    onClick = {
                        requestedVersion = offer.versionCode
                        updates.download()
                    },
                ) { Text("Update Now") }
            }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // PRVN Music Logo & Version Heading
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ymusic_logo),
                    contentDescription = "PRVN Music",
                    modifier = Modifier.size(40.dp),
                    tint = Color.Unspecified,
                )
                Column {
                    Text(
                        "PRVN Music v${offer.versionName}",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                    )
                    Text(
                        "Build ${offer.versionCode}" + if (offer.forceUpdate) " · Mandatory Update" else " · New Version Available",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (offer.forceUpdate) Color(0xFFEF4444) else DesignColors.Accent,
                        ),
                    )
                }
            }

            // What's New Section
            if (offer.notes.isNotBlank()) {
                Text(
                    "What's New:",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
                )
                ChangeNotes(offer.notes)
            }

            offer.changelogUrl?.let { url ->
                LinkText(stringResource(Res.string.update_full_changelog)) { uriHandler.openUri(url) }
            }

            // Download progress indicator
            if (requested && download is UpdateDownload.Running) {
                Text(
                    if (download.total > 0) {
                        stringResource(Res.string.update_downloading, formatBytes(download.done), formatBytes(download.total))
                    } else {
                        "Downloading update: ${formatBytes(download.done)}"
                    },
                    style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
                )
                LinearProgressIndicator(
                    progress = { if (download.total > 0) (download.done.toFloat() / download.total) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (download is UpdateDownload.Installing) {
                Text(
                    stringResource(Res.string.update_installing),
                    style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (download is UpdateDownload.NeedsInstallPermission) {
                val browserLink = openDownloadPage?.takeIf { download.offerBrowser }
                Text(
                    stringResource(
                        if (browserLink != null) Res.string.update_install_permission else Res.string.update_install_permission_allow,
                    ),
                    style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
                )
                browserLink?.let { LinkText(stringResource(Res.string.update_download_in_browser), it) }
            }

            if (requested && download is UpdateDownload.Failed) {
                Text(updateFailureMessage(download.code), style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary))
                openDownloadPage?.let { LinkText(stringResource(Res.string.update_official_site), it) }
            }

            if (download is UpdateDownload.InstallFailed) {
                Text(updateFailureMessage(download.code), style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary))
                openDownloadPage?.let { LinkText(stringResource(Res.string.update_official_site), it) }
            }

            if (blocked != null) {
                Text(
                    stringResource(Res.string.update_manual_hint, stringResource(blocked)),
                    style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                )
            }
        }
    }
}

/** A failure the user can act on as its own message, any other as the code to report. */
@Composable
private fun updateFailureMessage(code: String): String =
    if (code == DISK_FULL_CODE || code == NETWORK_CODE) errorMessage(code) else stringResource(Res.string.update_failed, code)

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Accent),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = DesignSpacing.Space4),
    )
}

/**
 * "Check for update" from the drawer: checking, then up to date or failed. A found update closes
 * this ([onDone] true) and the update prompt takes over.
 */
@Composable
fun CheckUpdateDialog(graph: AppGraph, onDone: (found: Boolean) -> Unit) {
    val updates = graph.updates ?: return
    var result by remember { mutableStateOf<UpdateCheck?>(null) }
    LaunchedEffect(Unit) {
        val check = updates.checkNow()
        if (check == UpdateCheck.Found) onDone(true) else result = check
    }
    ElevatedBarDialog(
        title = "Check for Updates",
        onDismiss = { onDone(false) },
        canScrollBackward = false,
        canScrollForward = false,
        buttons = {
            DialogSecondaryButton(stringResource(Res.string.update_close)) { onDone(false) }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(DesignSpacing.Space16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (result) {
                null -> {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.update_checking), style = TextStyle(fontSize = 14.sp, color = DesignColors.Ink))
                }
                UpdateCheck.UpToDate -> {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = DesignColors.BrandGreen)
                    Text(stringResource(Res.string.update_up_to_date), style = TextStyle(fontSize = 14.sp, color = DesignColors.Ink))
                }
                else -> Text(
                    stringResource(Res.string.update_check_failed),
                    style = TextStyle(fontSize = 14.sp, color = DesignColors.InkSecondary),
                )
            }
        }
    }
}

@Composable
private fun ChangeNotes(notes: String) {
    if (notes.isBlank()) return
    Text(
        notes,
        style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
        maxLines = 7,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
