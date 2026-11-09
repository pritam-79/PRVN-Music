package io.ymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.dialog_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Dedicated About PRVN Music dialog surfacing application identity, versioning, developer attribution,
 * open-source and third-party licences, privacy policy, and verified repository links.
 */
@Composable
fun AboutPrvnDialog(graph: AppGraph, onDismiss: () -> Unit) {
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    var showLicenses by remember { mutableStateOf(false) }

    ElevatedBarDialog(
        title = "About PRVN Music",
        onDismiss = onDismiss,
        canScrollBackward = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
        buttons = {
            DialogSecondaryButton(stringResource(Res.string.dialog_cancel)) { onDismiss() }
        },
    ) {
        if (showLicenses) {
            val licenseState = rememberLazyListState()
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Third-Party Components",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Text(
                    "PRVN Music is open-source under GNU GPL v3.0. The following third-party components and open-source foundations are included:",
                    style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                HorizontalDivider(color = DesignColors.Divider, modifier = Modifier.padding(vertical = 4.dp))
                LazyColumn(state = licenseState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(OssComponents) { comp ->
                        Column(
                            Modifier.fillMaxWidth().clickable { uriHandler.openUri(comp.url) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(comp.name, style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink))
                                Text(comp.license, style = TextStyle(fontSize = 11.5.sp, color = DesignColors.Accent, fontWeight = FontWeight.Medium))
                            }
                            comp.scope?.let {
                                Text(it, style = TextStyle(fontSize = 11.sp, color = DesignColors.InkFaint), modifier = Modifier.padding(top = 2.dp))
                            }
                            Text(comp.url, style = TextStyle(fontSize = 11.sp, color = DesignColors.InkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    DialogSecondaryButton("Back to About") { showLicenses = false }
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(
                            "PRVN Music",
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DesignColors.InkStrong),
                        )
                        Text(
                            "Premium Dark Audio Streaming & Player",
                            style = TextStyle(fontSize = 12.sp, color = DesignColors.Accent, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    HorizontalDivider(color = DesignColors.Divider)
                }

                item {
                    AboutInfoRow("Version", "${graph.appVersionName ?: "0.06"} (Build ${graph.appVersionCode ?: 9})")
                    AboutInfoRow("Application ID", "com.prvn.music")
                    AboutInfoRow("Developer", "PRVN")
                    AboutInfoRow("Target Architecture", "Android ARM64 (arm64-v8a)")
                    AboutInfoRow("License", "GNU General Public License v3.0")
                    HorizontalDivider(color = DesignColors.Divider)
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(
                            "Privacy Policy",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                        )
                        Text(
                            "PRVN Music respects your privacy. No user accounts or personal profiles are collected. Media streaming connects directly to content providers. Local library indexing stays strictly on your device. Anonymous crash reporting can be toggled in Privacy settings.",
                            style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary, lineHeight = 17.sp),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    HorizontalDivider(color = DesignColors.Divider)
                }

                item {
                    AboutClickableRow("Third-Party Licenses", "View open-source licenses and attribution") {
                        showLicenses = true
                    }
                    AboutClickableRow("Source Code Repository", "github.com/pritam-79/PRVN-Music") {
                        uriHandler.openUri("https://github.com/pritam-79/PRVN-Music")
                    }
                    AboutClickableRow("Bug Reports & Feedback", "github.com/pritam-79/PRVN-Music/issues") {
                        uriHandler.openUri("https://github.com/pritam-79/PRVN-Music/issues")
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary))
        Text(value, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink))
    }
}

@Composable
private fun AboutClickableRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(title, style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink))
        Text(subtitle, style = TextStyle(fontSize = 11.5.sp, color = DesignColors.Accent), modifier = Modifier.padding(top = 2.dp))
    }
}
