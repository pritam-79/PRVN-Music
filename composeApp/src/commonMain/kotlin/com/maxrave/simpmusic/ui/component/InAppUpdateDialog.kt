package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.Update
import com.maxrave.simpmusic.ui.theme.fontFamily
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel.InAppUpdateState
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.download_and_install
import simpmusic.composeapp.generated.resources.download_failed
import simpmusic.composeapp.generated.resources.install_now
import simpmusic.composeapp.generated.resources.ok
import simpmusic.composeapp.generated.resources.open_in_browser
import simpmusic.composeapp.generated.resources.up_to_date_message
import simpmusic.composeapp.generated.resources.up_to_date_title
import simpmusic.composeapp.generated.resources.update_available

private val BrandPurple = Color(0xFFA855F7)
private val BrandCyan = Color(0xFF06B6D4)
private val DarkDialogBg = Color(0xFF131722)
private val CardBg = Color(0xFF1E2433)
private val HighContrastTextPrimary = Color.White
private val HighContrastTextSecondary = Color(0xFFE2E8F0)
private val HighContrastTextMuted = Color(0xFF94A3B8)
private val WarningRed = Color(0xFFEF4444)

@Composable
fun InAppUpdateDialog(
    state: InAppUpdateState,
    onDownload: (UpdateData) -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    onInstall: (String) -> Unit,
    onRequestPermission: () -> Unit = {},
    onOpenBrowser: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    when (state) {
        is InAppUpdateState.Idle -> Unit

        is InAppUpdateState.Checking -> {
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                icon = {
                    CircularProgressIndicator(
                        color = BrandPurple,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                },
                title = {
                    Text(
                        text = "Checking for Updates...",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = "Querying release servers for PRVN Music updates.",
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = HighContrastTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        is InAppUpdateState.UpdateAvailable -> {
            val update = state.updateData
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                icon = {
                    Icon(
                        imageVector = SimpIcons.Update,
                        contentDescription = null,
                        tint = BrandPurple,
                        modifier = Modifier.size(32.dp),
                    )
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.update_available),
                            style = typo().titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HighContrastTextPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "PRVN Music ${update.tagName}",
                            style = typo().bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandCyan,
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardBg)
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (update.body.isNotBlank()) {
                            Markdown(
                                update.body,
                                typography = markdownTypography(
                                    h1 = typo().titleMedium.copy(color = HighContrastTextPrimary),
                                    h2 = typo().titleSmall.copy(color = HighContrastTextPrimary),
                                    h3 = typo().labelMedium.copy(color = HighContrastTextPrimary),
                                    text = typo().bodySmall.copy(color = HighContrastTextSecondary),
                                    bullet = typo().bodySmall.copy(color = HighContrastTextSecondary),
                                    paragraph = typo().bodySmall.copy(color = HighContrastTextSecondary),
                                    textLink = TextLinkStyles(
                                        SpanStyle(
                                            color = BrandCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = fontFamily(),
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onDownload(update) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandPurple,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            stringResource(Res.string.download_and_install),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                        )
                    }
                },
                dismissButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpenBrowser(update.tagName) }) {
                            Text(
                                stringResource(Res.string.open_in_browser),
                                color = BrandCyan,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = onDismiss) {
                            Text(
                                stringResource(Res.string.cancel),
                                color = HighContrastTextSecondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
            )
        }

        is InAppUpdateState.Downloading -> {
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                onDismissRequest = {},
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Downloading PRVN Music Update",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { if (state.progress in 0f..1f) state.progress else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = BrandCyan,
                            trackColor = Color(0xFF2D3748),
                        )
                        Spacer(Modifier.height(14.dp))
                        val downloadedMb = (state.bytesDownloaded.toDouble() / (1024.0 * 1024.0))
                        val totalMb = (state.totalBytes.toDouble() / (1024.0 * 1024.0))
                        val percent = if (state.totalBytes > 0) (state.progress * 100).toInt() else 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val progressText = if (state.totalBytes > 0) {
                                "${(downloadedMb * 10).toInt() / 10.0} MB / ${(totalMb * 10).toInt() / 10.0} MB"
                            } else {
                                "${(downloadedMb * 10).toInt() / 10.0} MB downloaded"
                            }
                            Text(
                                text = progressText,
                                style = typo().bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = HighContrastTextSecondary,
                            )
                            Text(
                                text = "$percent%",
                                style = typo().bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = BrandCyan,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val speedText = if (state.speedBps > 1024 * 1024) {
                                "${(state.speedBps / (1024.0 * 1024.0) * 10).toInt() / 10.0} MB/s"
                            } else if (state.speedBps > 0) {
                                "${state.speedBps / 1024} KB/s"
                            } else {
                                "Connecting..."
                            }
                            Text(
                                text = speedText,
                                style = typo().bodySmall,
                                color = HighContrastTextMuted,
                            )

                            val etaText = if (state.etaSeconds > 0) {
                                "${state.etaSeconds}s remaining"
                            } else {
                                ""
                            }
                            if (etaText.isNotEmpty()) {
                                Text(
                                    text = etaText,
                                    style = typo().bodySmall,
                                    color = HighContrastTextMuted,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPurple,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Pause", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(HighContrastTextSecondary)),
                        ) {
                            Text(stringResource(Res.string.cancel), color = HighContrastTextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                dismissButton = {},
            )
        }

        is InAppUpdateState.Paused -> {
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                onDismissRequest = {},
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Download Paused",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    val downloadedMb = (state.bytesDownloaded.toDouble() / (1024.0 * 1024.0))
                    val totalMb = (state.totalBytes.toDouble() / (1024.0 * 1024.0))
                    val percent = if (state.totalBytes > 0) (state.progress * 100).toInt() else 0
                    Column {
                        Text(
                            text = "Download paused at $percent% (${(downloadedMb * 10).toInt() / 10.0} MB / ${(totalMb * 10).toInt() / 10.0} MB).",
                            style = typo().bodyMedium,
                            color = HighContrastTextSecondary,
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandCyan,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Resume", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Cancel", color = HighContrastTextSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        }

        is InAppUpdateState.Verifying -> {
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                onDismissRequest = {},
                containerColor = DarkDialogBg,
                icon = {
                    CircularProgressIndicator(
                        color = BrandCyan,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                },
                title = {
                    Text(
                        text = "Verifying APK Security",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = state.statusText,
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                        textAlign = TextAlign.Center,
                    )
                },
                confirmButton = {},
            )
        }

        is InAppUpdateState.InstallPermissionRequired -> {
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Permission Required",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = "Android requires permission to install updates directly from PRVN Music. Tap 'Grant Permission' to enable 'Install unknown apps' for PRVN Music, then return to complete the update.",
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandPurple,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later", color = HighContrastTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        is InAppUpdateState.DownloadComplete, is InAppUpdateState.InstallReady -> {
            val filePath = if (state is InAppUpdateState.DownloadComplete) state.filePath else (state as InAppUpdateState.InstallReady).filePath
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Download Verified & Ready",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = "The latest PRVN Music update has been downloaded and verified. Tap Install Now to update immediately.",
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onInstall(filePath) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandCyan,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            stringResource(Res.string.install_now),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.Black,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel), color = HighContrastTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        is InAppUpdateState.Installing -> {
            AlertDialog(
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Installing Update...",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = "The system package installer has been launched. Please follow the system prompts to complete installation.",
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(Res.string.ok), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
            )
        }

        is InAppUpdateState.InstallSuccess -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Installation Completed",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = "PRVN Music has been successfully updated.",
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(Res.string.ok), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
            )
        }

        is InAppUpdateState.InstallFailed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Installation Failed",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WarningRed,
                    )
                },
                text = {
                    Text(
                        text = "Could not complete installation: ${state.reason}",
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onInstall(state.filePath) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Retry Install", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Dismiss", color = HighContrastTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        is InAppUpdateState.SignatureMismatch -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Signature Mismatch",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WarningRed,
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Security Alert: The downloaded APK is signed with a different key than your installed version.\n\nAndroid prevents updates between differently-signed builds to protect your data. If you are upgrading from a debug build to a production build, please uninstall the previous version first.",
                            style = typo().bodyMedium,
                            color = HighContrastTextSecondary,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(Res.string.ok), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
            )
        }

        is InAppUpdateState.InvalidApk -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "APK Verification Failed",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WarningRed,
                    )
                },
                text = {
                    Text(
                        text = "The downloaded update cannot be installed: ${state.reason}",
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onRetry?.invoke() ?: onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Retry Download", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel), color = HighContrastTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        is InAppUpdateState.NetworkError -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Update Network Error",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WarningRed,
                    )
                },
                text = {
                    Text(
                        text = state.message,
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onRetry?.invoke() ?: onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel), color = HighContrastTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        is InAppUpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = stringResource(Res.string.up_to_date_title),
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = stringResource(Res.string.up_to_date_message, state.versionName),
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(Res.string.ok), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
            )
        }

        is InAppUpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkDialogBg,
                title = {
                    Text(
                        text = "Update Check",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = HighContrastTextPrimary,
                    )
                },
                text = {
                    Text(
                        text = state.message.ifBlank { stringResource(Res.string.download_failed) },
                        style = typo().bodyMedium,
                        color = HighContrastTextSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(stringResource(Res.string.ok), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
            )
        }
    }
}
