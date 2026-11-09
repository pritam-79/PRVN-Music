package io.ymusic.ui.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.player.PlayableItem
import io.ymusic.player.Player
import io.ymusic.ui.DesignColors
import io.ymusic.ui.ElevatedBarDialog
import io.ymusic.ui.percentEncode

@Composable
fun SyncedLyricsDialog(
    player: Player,
    onDismiss: () -> Unit,
) {
    val queue by player.queue.collectAsState()
    val currentItem = queue.currentItem
    val progress by player.progress.collectAsState()
    val uriHandler = LocalUriHandler.current

    val title = currentItem?.displayTitle ?: "Unknown Track"
    val artist = when (currentItem) {
        is PlayableItem.LocalMedia -> currentItem.artist ?: "Unknown Artist"
        is PlayableItem.YtMedia -> currentItem.displaySubtitle ?: "Unknown Artist"
        else -> "Unknown Artist"
    }
    val durationSec = (progress.durationMs / 1000L).toInt().takeIf { it > 0 }

    var lyricsState by remember(title, artist) { mutableStateOf<LrcSongLyrics?>(null) }
    var isLoading by remember(title, artist) { mutableStateOf(true) }
    var timeOffsetMs by remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()

    // Fetch lyrics on song change
    LaunchedEffect(title, artist) {
        isLoading = true
        lyricsState = LrcLibClient.fetchLyrics(title, artist, durationSec)
        isLoading = false
    }

    // Determine current active lyric line index
    val effectivePositionMs = progress.positionMs + timeOffsetMs
    val syncedLines = lyricsState?.syncedLines.orEmpty()
    val activeIndex = remember(effectivePositionMs, syncedLines) {
        if (syncedLines.isEmpty()) -1
        else {
            val idx = syncedLines.indexOfLast { it.timeMs <= effectivePositionMs }
            if (idx >= 0) idx else 0
        }
    }

    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < syncedLines.size) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    ElevatedBarDialog(
        title = "Synchronized Lyrics",
        onDismiss = onDismiss,
        canScrollBackward = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
        buttons = {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF141224), Color(0xFF0C0A15))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header: Track title, artist & sync indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = title,
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artist,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFBBBBBB)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Sync badge
                val hasSynced = syncedLines.isNotEmpty()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (hasSynced) Color(0xFF1B4D3E) else Color(0xFF2C2C3E))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (isLoading) "Searching..." else if (hasSynced) "Synced • LRCLIB" else if (lyricsState?.plainLyrics != null) "Plain Text" else "No Lyrics",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = if (hasSynced) Color(0xFF4EFAAA) else Color(0xFFB0B0C0),
                            fontWeight = FontWeight.SemiBold
                        ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Timing offset controls when synced
            if (syncedLines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Offset: ${if (timeOffsetMs >= 0) "+$timeOffsetMs" else "$timeOffsetMs"}ms",
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF888899)),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    OffsetButton("-0.5s") { timeOffsetMs -= 500L }
                    Spacer(Modifier.width(6.dp))
                    OffsetButton("Reset") { timeOffsetMs = 0L }
                    Spacer(Modifier.width(6.dp))
                    OffsetButton("+0.5s") { timeOffsetMs += 500L }
                }
            }

            HorizontalDivider(
                color = Color(0xFF2E2A48),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Content Area
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = DesignColors.Accent, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Fetching synchronized lyrics...", style = TextStyle(fontSize = 13.sp, color = Color(0xFFAAAAAA)))
                        }
                    }
                    syncedLines.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = 40.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(syncedLines) { index, line ->
                                val isActive = index == activeIndex
                                val textColor by animateColorAsState(
                                    targetValue = if (isActive) Color.White else Color(0xFF6E6988),
                                    animationSpec = tween(durationMillis = 200),
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isActive) 1.05f else 1.0f,
                                    animationSpec = tween(durationMillis = 200),
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { player.seekTo((line.timeMs - timeOffsetMs).coerceAtLeast(0L)) }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = line.text,
                                        style = TextStyle(
                                            fontSize = if (isActive) 18.sp else 15.sp,
                                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = textColor,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 24.sp,
                                        ),
                                        modifier = Modifier.scale(scale),
                                    )
                                }
                            }
                        }
                    }
                    !lyricsState?.plainLyrics.isNullOrBlank() -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item {
                                Text(
                                    text = lyricsState!!.plainLyrics!!,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = Color(0xFFDDDDDD),
                                        lineHeight = 24.sp,
                                        textAlign = TextAlign.Center,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Lyrics,
                                contentDescription = null,
                                tint = Color(0xFF555070),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No lyrics found for this song",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "We checked LRCLIB database but couldn't locate synchronized lyrics for \"$title\".",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF888899), textAlign = TextAlign.Center),
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DesignColors.Accent.copy(alpha = 0.2f))
                                    .border(1.dp, DesignColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        val query = "$title $artist lyrics"
                                        uriHandler.openUri("https://www.google.com/search?q=${percentEncode(query)}")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Search, contentDescription = null, tint = DesignColors.Accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Search on Google", style = TextStyle(fontSize = 12.5.sp, color = DesignColors.Accent, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OffsetButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF262238))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 10.5.sp, color = Color(0xFFCCC8E0), fontWeight = FontWeight.Medium),
        )
    }
}
