package com.maxrave.simpmusic.ui.component

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
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
import simpmusic.composeapp.generated.resources.downloading_update
import simpmusic.composeapp.generated.resources.install_now
import simpmusic.composeapp.generated.resources.ok
import simpmusic.composeapp.generated.resources.open_in_browser
import simpmusic.composeapp.generated.resources.up_to_date_message
import simpmusic.composeapp.generated.resources.up_to_date_title
import simpmusic.composeapp.generated.resources.update_available

@Composable
fun InAppUpdateDialog(
    state: InAppUpdateState,
    onDownload: (UpdateData) -> Unit,
    onInstall: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    when (state) {
        is InAppUpdateState.Idle -> Unit

        is InAppUpdateState.UpdateAvailable -> {
            val update = state.updateData
            AlertDialog(
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = SimpIcons.Update,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.update_available),
                            style = typo().titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "PRVN Music ${update.tagName}",
                            style = typo().bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (update.body.isNotBlank()) {
                            Markdown(
                                update.body,
                                typography = markdownTypography(
                                    h1 = typo().titleMedium,
                                    h2 = typo().titleSmall,
                                    h3 = typo().labelMedium,
                                    text = typo().bodySmall,
                                    bullet = typo().bodySmall,
                                    paragraph = typo().bodySmall,
                                    textLink = TextLinkStyles(
                                        SpanStyle(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
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
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(Res.string.download_and_install))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { onOpenBrowser(update.tagName) }) {
                            Text(stringResource(Res.string.open_in_browser))
                        }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.cancel))
                        }
                    }
                },
            )
        }

        is InAppUpdateState.Downloading -> {
            AlertDialog(
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
                onDismissRequest = {},
                title = {
                    Text(
                        text = stringResource(Res.string.downloading_update),
                        style = typo().titleMedium,
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
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        val downloadedMb = (state.bytesDownloaded.toDouble() / (1024.0 * 1024.0))
                        val totalMb = (state.totalBytes.toDouble() / (1024.0 * 1024.0))
                        val percent = if (state.totalBytes > 0) (state.progress * 100).toInt() else 0

                        val progressText = if (state.totalBytes > 0) {
                            "${(downloadedMb * 10).toInt() / 10.0} MB / ${(totalMb * 10).toInt() / 10.0} MB ($percent%)"
                        } else {
                            "${(downloadedMb * 10).toInt() / 10.0} MB downloaded"
                        }
                        Text(
                            text = progressText,
                            style = typo().bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }

        is InAppUpdateState.ReadyToInstall -> {
            AlertDialog(
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Download Complete",
                        style = typo().titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Text(
                        text = "The latest PRVN Music update has been downloaded. Tap Install Now to proceed with the update.",
                        style = typo().bodyMedium,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onInstall(state.filePath) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(stringResource(Res.string.install_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }

        is InAppUpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = stringResource(Res.string.up_to_date_title),
                        style = typo().titleMedium,
                    )
                },
                text = {
                    Text(
                        text = stringResource(Res.string.up_to_date_message, state.versionName),
                        style = typo().bodyMedium,
                    )
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text(stringResource(Res.string.ok))
                    }
                },
            )
        }

        is InAppUpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Update Check",
                        style = typo().titleMedium,
                    )
                },
                text = {
                    Text(
                        text = state.message.ifBlank { stringResource(Res.string.download_failed) },
                        style = typo().bodyMedium,
                    )
                },
                confirmButton = {
                    Button(onClick = onDismiss) {
                        Text(stringResource(Res.string.ok))
                    }
                },
            )
        }
    }
}
