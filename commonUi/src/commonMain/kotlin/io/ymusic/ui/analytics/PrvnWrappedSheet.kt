package io.ymusic.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.dynamicengine.yt.YtFeedItem
import io.ymusic.mediastore.model.MediaStoreItem
import io.ymusic.ui.AppGraph
import io.ymusic.ui.DesignColors
import io.ymusic.ui.ElevatedBarDialog
import io.ymusic.ui.playhistory.ListeningStats
import io.ymusic.ui.playhistory.mediaItemsByPlayHistoryCodes

@Composable
fun PrvnWrappedDialog(
    graph: AppGraph,
    onDismiss: () -> Unit,
) {
    var stats by remember { mutableStateOf<ListeningStats?>(null) }
    var topLocalSongs by remember { mutableStateOf<List<MediaStoreItem>>(emptyList()) }
    var topYtSongs by remember { mutableStateOf<List<YtFeedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        isLoading = true
        stats = graph.playHistory?.listeningStats()
        val allLocal = graph.mediaStore?.allItems().orEmpty()
        val topCodes = graph.playHistory?.localTopChart(limit = 10).orEmpty()
        topLocalSongs = mediaItemsByPlayHistoryCodes(allLocal, topCodes)
        topYtSongs = graph.playHistory?.youtubeTopChart(limit = 10).orEmpty()
        isLoading = false
    }

    ElevatedBarDialog(
        title = "PRVN Wrapped",
        onDismiss = onDismiss,
        canScrollBackward = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
        buttons = {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(540.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1B0B2E), Color(0xFF0C071C), Color(0xFF06030F))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesignColors.Accent)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header Banner Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF2A85), Color(0xFF8A2BE2), Color(0xFF00E5FF))
                                    )
                                )
                                .padding(1.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(Color(0xEE160C2C))
                                    .padding(18.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color(0xFFFF4081),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "YOUR PRVN RECAP",
                                            style = TextStyle(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 1.5.sp,
                                                color = Color(0xFFFF4081)
                                            )
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "On-Device Audio Analytics",
                                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Calculated 100% privately on your device from your local and streaming play history.",
                                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFB0A8D0), lineHeight = 16.sp)
                                    )
                                }
                            }
                        }
                    }

                    // Key Stats Grid
                    item {
                        val totalMs = stats?.totalWatchTimeMs ?: 0L
                        val totalMinutes = (totalMs / 60000L).coerceAtLeast(1L)
                        val totalHours = totalMinutes / 60L
                        val timeStr = if (totalHours > 0) "${totalHours}h ${totalMinutes % 60}m" else "${totalMinutes}m"
                        val tracksCount = stats?.totalTracks ?: (topLocalSongs.size + topYtSongs.size)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Timer,
                                label = "Total Listened",
                                value = timeStr,
                                tint = Color(0xFF00E5FF)
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Headphones,
                                label = "Tracks Explored",
                                value = "$tracksCount Tracks",
                                tint = Color(0xFFFF4081)
                            )
                        }
                    }

                    // Top Tracks Section
                    item {
                        Text(
                            "Top Played Tracks",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }

                    if (topLocalSongs.isEmpty() && topYtSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E1834))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Keep listening to songs to unlock your top tracks and personalized stats!",
                                    style = TextStyle(fontSize = 13.sp, color = Color(0xFF9E96BC), textAlign = TextAlign.Center)
                                )
                            }
                        }
                    } else {
                        // Render top local tracks
                        itemsIndexed(topLocalSongs.take(5)) { index, song ->
                            RankedTrackRow(
                                rank = index + 1,
                                title = song.title,
                                artist = song.artist ?: "Unknown Artist",
                                isLocal = true,
                            )
                        }
                        // Render top YouTube tracks
                        itemsIndexed(topYtSongs.take(5)) { index, item ->
                            RankedTrackRow(
                                rank = topLocalSongs.size.coerceAtMost(5) + index + 1,
                                title = item.title,
                                artist = item.subtitleLine1 ?: item.channelTitle ?: "YouTube",
                                isLocal = false,
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF171228))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White))
            Text(label, style = TextStyle(fontSize = 11.5.sp, color = Color(0xFF8E88AB)))
        }
    }
}

@Composable
private fun RankedTrackRow(
    rank: Int,
    title: String,
    artist: String,
    isLocal: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF140E24))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rankBadgeColor = when (rank) {
            1 -> Color(0xFFFFD700)
            2 -> Color(0xFFC0C0C0)
            3 -> Color(0xFFCD7F32)
            else -> Color(0xFF6B6584)
        }
        Text(
            text = "#$rank",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = rankBadgeColor),
            modifier = Modifier.width(32.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = title,
                style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$artist • ${if (isLocal) "Local Audio" else "Online Stream"}",
                style = TextStyle(fontSize = 11.sp, color = Color(0xFF8882A2)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
