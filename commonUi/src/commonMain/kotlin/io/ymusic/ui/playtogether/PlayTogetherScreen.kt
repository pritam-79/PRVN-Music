package io.ymusic.ui.playtogether

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.dynamicengine.yt.YtFeedItem
import io.ymusic.dynamicengine.yt.musicSearch
import io.ymusic.imageloader.AsyncImage
import io.ymusic.settings.AppSettings
import io.ymusic.settings.flow
import io.ymusic.ui.AppGraph
import io.ymusic.ui.DesignColors
import io.ymusic.ui.DesignSpacing
import io.ymusic.ui.LocalImageLoader
import io.ymusic.ui.LocalMiniPlayerInset
import io.ymusic.player.PlayableItem
import io.ymusic.player.PlaybackState
import io.ymusic.ui.PlaybackCoordinator
import io.ymusic.ui.playtogether.backend.InviteLinkHelper
import io.ymusic.ui.playtogether.backend.PlayTogetherBackendProvider
import kotlinx.coroutines.launch
import kotlin.random.Random

private val BrandPurpleDark = Color(0xFF1E0A3C)
private val BrandPurpleMedium = Color(0xFF3B0764)
private val BrandPurpleVibrant = Color(0xFFA855F7)
private val BrandPurpleLight = Color(0xFFC084FC)
private val BrandCyan = Color(0xFF06B6D4)
private val StatusGreen = Color(0xFF10B981)
private val StatusAmber = Color(0xFFF59E0B)
private val StatusRed = Color(0xFFEF4444)

@Composable
fun PlayTogetherScreen(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    controller: PlayTogetherController = remember { PlayTogetherController.instance },
) {
    val state by controller.state.collectAsState()
    val realtimeUrl by remember(graph) { graph.settings.flow(AppSettings.playTogetherRealtimeUrl) }.collectAsState()
    val realtimeKey by remember(graph) { graph.settings.flow(AppSettings.playTogetherAnonKey) }.collectAsState()

    LaunchedEffect(realtimeUrl, realtimeKey) {
        PlayTogetherBackendProvider.configure(realtimeUrl, realtimeKey)
    }

    val syncEngine = remember(coordinator, controller) {
        PlayTogetherSyncEngine(coordinator, controller, graph.settings)
    }

    LaunchedEffect(syncEngine) {
        controller.bindSyncEngine(syncEngine)
    }

    LaunchedEffect(state is PlayTogetherState.Lobby) {
        if (state is PlayTogetherState.Lobby) {
            while (isActive) {
                controller.refreshPublicRooms()
                delay(10_000L)
            }
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var joinPresetCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignColors.Surface)
            .padding(bottom = DesignSpacing.SettingsSectionGap + LocalMiniPlayerInset.current),
    ) {
        when (val s = state) {
            is PlayTogetherState.Lobby -> {
                LobbyView(
                    activeRooms = s.activeRooms,
                    errorMessage = s.errorMessage,
                    isBusy = s.isBusy,
                    onRefresh = { controller.refreshPublicRooms() },
                    onCreateClick = { showCreateDialog = true },
                    onJoinClick = {
                        joinPresetCode = ""
                        showJoinDialog = true
                    },
                    onQuickJoin = { room ->
                        joinPresetCode = room.code
                        showJoinDialog = true
                    },
                )
            }
            is PlayTogetherState.InRoom -> {
                ActiveRoomView(
                    graph = graph,
                    state = s,
                    controller = controller,
                    onLeaveClick = { showLeaveConfirm = true },
                )
            }
        }

        // Dialogs
        if (showCreateDialog) {
            val playerQueue = coordinator.player.queue.collectAsState().value
            val playerProgress = coordinator.player.progress.collectAsState().value
            val playerStatus = coordinator.player.status.collectAsState().value

            val currentItem = playerQueue.currentItem
            val initialTrack = when (currentItem) {
                is PlayableItem.YtMedia -> PlayTogetherTrack(
                    id = currentItem.videoId,
                    title = currentItem.displayTitle,
                    artist = currentItem.displaySubtitle ?: "Unknown Artist",
                    artworkUrl = currentItem.displayThumbnailUrl,
                    durationMs = currentItem.durationMs ?: (playerProgress.durationMs.takeIf { it > 0 } ?: 210000L),
                    isYouTube = true,
                    addedBy = "Host",
                )
                else -> null
            }

            val isPlayingNow = playerStatus.state is PlaybackState.Prepared &&
                (playerStatus.state as PlaybackState.Prepared).playWhenReady &&
                !(playerStatus.state as PlaybackState.Prepared).buffering

            val initialPos = playerProgress.positionMs

            val initialQueue = if (playerQueue.items.isNotEmpty() && playerQueue.currentIndex >= 0) {
                playerQueue.items.drop(playerQueue.currentIndex + 1).mapNotNull { item ->
                    when (item) {
                        is PlayableItem.YtMedia -> PlayTogetherTrack(
                            id = item.videoId,
                            title = item.displayTitle,
                            artist = item.displaySubtitle ?: "Unknown Artist",
                            artworkUrl = item.displayThumbnailUrl,
                            durationMs = item.durationMs ?: 210000L,
                            isYouTube = true,
                            addedBy = "Host",
                        )
                        else -> null
                    }
                }
            } else emptyList()

            CreateRoomDialog(
                initialTransferTrack = initialTrack,
                initialTransferPositionMs = initialPos,
                initialQueueCount = initialQueue.size,
                onDismiss = { showCreateDialog = false },
                onCreate = { name, host, code, vis, mode, pass, transferMusic ->
                    showCreateDialog = false
                    if (transferMusic && initialTrack != null) {
                        controller.createRoom(
                            name = name,
                            hostName = host,
                            code = code,
                            visibility = vis,
                            controlMode = mode,
                            password = pass,
                            initialTrack = initialTrack.copy(addedBy = host),
                            initialPositionMs = initialPos,
                            initialIsPlaying = isPlayingNow,
                            initialQueue = initialQueue.map { it.copy(addedBy = host) },
                        )
                    } else {
                        controller.createRoom(
                            name = name,
                            hostName = host,
                            code = code,
                            visibility = vis,
                            controlMode = mode,
                            password = pass,
                        )
                    }
                },
            )
        }

        if (showJoinDialog) {
            JoinRoomDialog(
                initialCode = joinPresetCode,
                onDismiss = { showJoinDialog = false },
                onJoin = { code, user, pass ->
                    showJoinDialog = false
                    controller.joinRoom(code, user, pass)
                },
            )
        }

        if (showLeaveConfirm) {
            val isHost = (state as? PlayTogetherState.InRoom)?.isHost == true
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text(if (isHost) "End Session & Close Room?" else "Leave Room?", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        if (isHost) "As the room host, ending the session will disconnect all listeners and delete this room."
                        else "Are you sure you want to disconnect from this synchronized session?",
                        color = DesignColors.InkSecondary,
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showLeaveConfirm = false
                            if (isHost) {
                                controller.closeRoom()
                            } else {
                                controller.leaveRoom()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isHost) StatusRed else DesignColors.Accent),
                    ) {
                        Text(if (isHost) "Close Room" else "Leave", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveConfirm = false }) {
                        Text("Cancel", color = DesignColors.InkSecondary)
                    }
                },
                containerColor = DesignColors.SettingsCard,
            )
        }
    }
}

@Composable
private fun LobbyView(
    activeRooms: List<RoomInfo>,
    errorMessage: String? = null,
    isBusy: Boolean = false,
    onRefresh: () -> Unit,
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onQuickJoin: (RoomInfo) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredRooms = remember(activeRooms, searchQuery) {
        if (searchQuery.isBlank()) activeRooms
        else activeRooms.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.hostName.contains(searchQuery, ignoreCase = true) ||
                it.code == searchQuery.trim()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Header Card
        item("hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(BrandPurpleDark, BrandPurpleMedium, BrandPurpleVibrant),
                        ),
                    )
                    .border(1.dp, BrandPurpleVibrant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(20.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (PlayTogetherBackendProvider.isLiveConfigured) StatusGreen else BrandCyan),
                            )
                            Text(
                                text = "LISTEN TOGETHER",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp,
                                    color = Color(0xFFE9D5FF),
                                ),
                            )
                        }

                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Synchronized Music Rooms",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Listen with friends in real time. Shared queue, song requests, and drift correction.",
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            color = Color(0xFFE9D5FF).copy(alpha = 0.9f),
                            lineHeight = 17.sp,
                        ),
                    )
                }
            }
        }

        if (errorMessage != null) {
            item("error_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3B1824))
                        .border(1.dp, StatusRed, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = errorMessage,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFFDA4AF), fontWeight = FontWeight.Medium),
                    )
                }
            }
        }

        // Action Buttons Row
        item("actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Create Room Button
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text("Create Room", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                // Join Room Button
                OutlinedButton(
                    onClick = onJoinClick,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(BrandPurpleVibrant, BrandCyan)),
                    ),
                ) {
                    Icon(Icons.Outlined.Group, contentDescription = null, tint = BrandPurpleVibrant)
                    Spacer(Modifier.width(6.dp))
                    Text("Join by Code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                }
            }
        }

        // Search Filter Bar
        item("search_filter") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search rooms by name, host, or code...", fontSize = 13.sp, color = DesignColors.InkSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DesignColors.InkSecondary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = DesignColors.InkSecondary)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesignColors.Accent,
                    unfocusedBorderColor = DesignColors.OutlineSoft,
                    focusedTextColor = DesignColors.Ink,
                    unfocusedTextColor = DesignColors.Ink,
                ),
            )
        }

        // Section Title: Active Rooms
        item("rooms_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Public Rooms",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                )
                Text(
                    text = "${filteredRooms.size} active",
                    style = TextStyle(fontSize = 12.sp, color = DesignColors.Accent, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        // Active Rooms List
        if (filteredRooms.isEmpty()) {
            item("empty_rooms") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (searchQuery.isBlank()) "No active public rooms right now.\nTap 'Create Room' to start listening together!"
                        else "No rooms matched '$searchQuery'.",
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 13.5.sp, color = DesignColors.InkSecondary, lineHeight = 19.sp),
                    )
                }
            }
        } else {
            items(filteredRooms, key = { it.id }) { room ->
                RoomCard(room = room, onJoin = { onQuickJoin(room) })
            }
        }
    }
}

@Composable
private fun RoomCard(room: RoomInfo, onJoin: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onJoin() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = room.name.ifBlank { "PRVN Music Lounge" }
                    val displayHost = room.hostName.ifBlank { "PRVN Host" }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = displayName,
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (room.visibility == RoomVisibility.PRIVATE) {
                            Icon(Icons.Default.Lock, contentDescription = "Private", tint = BrandCyan, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Host: $displayHost · Code: ${room.code}",
                        style = TextStyle(fontSize = 12.sp, color = DesignColors.Accent, fontWeight = FontWeight.Medium),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandPurpleVibrant.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Group, contentDescription = null, tint = BrandPurpleVibrant, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${room.memberCount}",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandPurpleVibrant),
                        )
                    }
                }
            }

            if (room.currentTrack != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DesignColors.Surface.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = BrandPurpleVibrant,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = room.currentTrack.title,
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = room.currentTrack.artist,
                            style = TextStyle(fontSize = 11.sp, color = DesignColors.InkSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onJoin,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                    ) {
                        Text("Join", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRoomView(
    graph: AppGraph,
    state: PlayTogetherState.InRoom,
    controller: PlayTogetherController,
    onLeaveClick: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.room.name,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Code: ${state.room.code}",
                        style = TextStyle(fontSize = 13.sp, color = DesignColors.Accent, fontWeight = FontWeight.SemiBold),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(state.room.code))
                                copiedNotice = true
                            }
                            .padding(2.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = DesignColors.Accent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (copiedNotice) {
                        Text("Copied!", fontSize = 11.sp, color = StatusGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Sync status indicator
                val syncBadgeText = when {
                    state.isConnecting -> "Reconnecting"
                    state.syncOffsetMs <= 300L -> "Synced • ${state.syncOffsetMs}ms"
                    state.syncOffsetMs <= 800L -> "Syncing • ${state.syncOffsetMs}ms"
                    else -> "Re-aligning • ${state.syncOffsetMs}ms"
                }
                val syncBadgeColor = when {
                    state.isConnecting -> StatusAmber
                    state.syncOffsetMs <= 300L -> StatusGreen
                    state.syncOffsetMs <= 800L -> BrandCyan
                    else -> StatusAmber
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(syncBadgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = syncBadgeText,
                        style = TextStyle(fontSize = 11.sp, color = syncBadgeColor, fontWeight = FontWeight.Bold),
                    )
                }

                Button(
                    onClick = onLeaveClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (state.isHost) StatusRed.copy(alpha = 0.2f) else DesignColors.SettingsCard),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        if (state.isHost) "Close" else "Leave",
                        fontSize = 12.sp,
                        color = if (state.isHost) StatusRed else DesignColors.InkSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Tab Navigation Row
        val tabs = RoomTab.values()
        val selectedIndex = tabs.indexOf(state.activeTab).coerceAtLeast(0)

        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = DesignColors.Surface,
            contentColor = DesignColors.Accent,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = DesignColors.Accent,
                    height = 2.5.dp,
                )
            },
            divider = { HorizontalDivider(color = DesignColors.OutlineSoft.copy(alpha = 0.3f)) },
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == state.activeTab
                Tab(
                    selected = isSelected,
                    onClick = { controller.switchTab(tab) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = tab.label,
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) DesignColors.Accent else DesignColors.InkSecondary,
                                ),
                            )
                            when (tab) {
                                RoomTab.SHARED_QUEUE -> {
                                    if (state.queue.isNotEmpty()) {
                                        BadgePill("${state.queue.size}")
                                    }
                                }
                                RoomTab.SONG_REQUESTS -> {
                                    val pending = state.songRequests.count { it.status == SongRequestStatus.PENDING }
                                    if (pending > 0) {
                                        BadgePill("$pending", color = StatusAmber)
                                    }
                                }
                                RoomTab.PARTICIPANTS -> {
                                    BadgePill("${state.members.size}")
                                }
                                else -> Unit
                            }
                        }
                    },
                )
            }
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            when (state.activeTab) {
                RoomTab.NOW_PLAYING -> {
                    NowPlayingTabView(
                        state = state,
                        onTogglePlayPause = { controller.togglePlayPause() },
                        onNext = { controller.nextTrack() },
                        onPrevious = { controller.previousTrack() },
                        onSeek = { controller.seekTo(it) },
                        onGoToSearch = { controller.switchTab(RoomTab.SEARCH_MUSIC) },
                    )
                }
                RoomTab.SHARED_QUEUE -> {
                    SharedQueueTabView(
                        state = state,
                        onRemove = { controller.removeFromQueue(it) },
                        onMoveUp = { idx -> controller.reorderQueue(idx, idx - 1) },
                        onMoveDown = { idx -> controller.reorderQueue(idx, idx + 1) },
                        onClearQueue = { controller.clearQueue() },
                        onGoToSearch = { controller.switchTab(RoomTab.SEARCH_MUSIC) },
                    )
                }
                RoomTab.SEARCH_MUSIC -> {
                    InRoomSearchTabView(
                        graph = graph,
                        state = state,
                        onPlayNow = { track -> controller.playNow(track) },
                        onAddToQueue = { track -> controller.addToQueue(track) },
                        onRequestSong = { track, user -> controller.submitSongRequest(track, user) },
                    )
                }
                RoomTab.SONG_REQUESTS -> {
                    SongRequestsTabView(
                        state = state,
                        onApprove = { controller.approveSongRequest(it) },
                        onReject = { controller.rejectSongRequest(it) },
                        onCancel = { controller.cancelSongRequest(it) },
                    )
                }
                RoomTab.PARTICIPANTS -> {
                    ParticipantsTabView(state = state, controller = controller)
                }
                RoomTab.HOST_CONTROLS -> {
                    HostControlsTabView(
                        state = state,
                        controller = controller,
                        onLeaveRoom = onLeaveClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color = BrandPurpleVibrant) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 1: NOW PLAYING
// -------------------------------------------------------------------------------------------------

@Composable
private fun NowPlayingTabView(
    state: PlayTogetherState.InRoom,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onGoToSearch: () -> Unit,
) {
    val track = state.currentTrack
    val imageLoader = LocalImageLoader.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Artwork and Info
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f, fill = false)) {
            Spacer(Modifier.height(16.dp))

            val effectiveUrl = track?.artworkUrl?.takeIf { it.isNotBlank() }
                ?: track?.id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BrandPurpleDark)
                    .border(1.dp, BrandPurpleVibrant.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (effectiveUrl != null && imageLoader != null) {
                    AsyncImage(
                        loader = imageLoader,
                        url = effectiveUrl,
                        contentDescription = track?.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandPurpleVibrant, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.MusicNote,
                                    contentDescription = null,
                                    tint = BrandPurpleLight,
                                    modifier = Modifier.size(72.dp),
                                )
                            }
                        },
                        crossfadeMs = 250,
                    )
                } else {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = BrandPurpleLight,
                        modifier = Modifier.size(72.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = track?.title?.ifBlank { "PRVN Track" } ?: "No Song Playing",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = track?.artist?.ifBlank { "PRVN Artist" } ?: "Search songs below to start synchronized playback",
                style = TextStyle(fontSize = 14.sp, color = Color(0xFFCCCCCC)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            if (track != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.isAutoRadio || track.isAutoRadio) BrandCyan.copy(alpha = 0.15f) else DesignColors.SettingsCard)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = if (state.isAutoRadio || track.isAutoRadio) "📻 PRVN Auto-Radio • Continuous Play" else "Added by ${track.addedBy}",
                            style = TextStyle(fontSize = 11.5.sp, color = if (state.isAutoRadio || track.isAutoRadio) BrandCyan else DesignColors.Accent, fontWeight = FontWeight.Medium),
                        )
                    }

                    val syncText = when {
                        state.isConnecting -> "Reconnecting..."
                        state.syncOffsetMs <= 250L -> "Synced • ${state.syncOffsetMs}ms"
                        state.syncOffsetMs <= 750L -> "Syncing • ${state.syncOffsetMs}ms"
                        else -> "Re-aligning • ${state.syncOffsetMs}ms"
                    }
                    val badgeColor = when {
                        state.isConnecting -> StatusAmber
                        state.syncOffsetMs <= 250L -> StatusGreen
                        state.syncOffsetMs <= 750L -> BrandCyan
                        else -> StatusAmber
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(badgeColor))
                            Text(
                                text = syncText,
                                style = TextStyle(fontSize = 11.sp, color = badgeColor, fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }
        }

        // Playback Controls
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            val totalMs = track?.durationMs ?: 210000L
            val currentMs = state.positionMs.coerceIn(0L, totalMs)
            var draggingFraction by remember { mutableStateOf<Float?>(null) }
            val currentFraction = if (totalMs > 0) currentMs.toFloat() / totalMs.toFloat() else 0f
            val displayFraction = draggingFraction ?: currentFraction

            Slider(
                value = displayFraction.coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    draggingFraction = fraction
                },
                onValueChangeFinished = {
                    draggingFraction?.let { frac ->
                        val newPos = (frac * totalMs).toLong()
                        onSeek(newPos)
                    }
                    draggingFraction = null
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = DesignColors.Accent,
                    activeTrackColor = DesignColors.Accent,
                    inactiveTrackColor = DesignColors.OutlineSoft,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDurationMs(currentMs), fontSize = 11.sp, color = DesignColors.InkSecondary)
                Text(formatDurationMs(totalMs), fontSize = 11.sp, color = DesignColors.InkSecondary)
            }

            Spacer(Modifier.height(12.dp))

            // Transport Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = DesignColors.Ink, modifier = Modifier.size(30.dp))
                }

                Spacer(Modifier.width(16.dp))

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(DesignColors.Accent)
                        .clickable { onTogglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = DesignColors.Ink, modifier = Modifier.size(30.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onGoToSearch,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.SettingsCard),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = DesignColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search & Queue Songs", color = DesignColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 2: SHARED QUEUE
// -------------------------------------------------------------------------------------------------

@Composable
private fun SharedQueueTabView(
    state: PlayTogetherState.InRoom,
    onRemove: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onGoToSearch: () -> Unit,
) {
    val imageLoader = LocalImageLoader.current

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Up Next (${state.queue.size})",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isHost && state.queue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("Clear All", color = StatusRed, fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = onGoToSearch,
                    modifier = Modifier.height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Song", fontSize = 12.sp, color = Color.White)
                }
            }
        }

        if (state.queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.QueueMusic, contentDescription = null, tint = DesignColors.InkSecondary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Shared queue is empty.",
                        style = TextStyle(fontSize = 14.sp, color = DesignColors.InkSecondary),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onGoToSearch,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                    ) {
                        Text("Search Songs to Add", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.queue, key = { index, item -> "${item.id}_$index" }) { index, track ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DesignColors.InkSecondary,
                                modifier = Modifier.width(20.dp),
                            )

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandPurpleDark),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (track.artworkUrl != null && imageLoader != null) {
                                    AsyncImage(
                                        loader = imageLoader,
                                        url = track.artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(20.dp))
                                }
                            }

                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesignColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${track.artist} · Added by ${track.addedBy}",
                                    fontSize = 11.sp,
                                    color = DesignColors.InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (state.isHost) {
                                if (index > 0) {
                                    IconButton(onClick = { onMoveUp(index) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = DesignColors.InkSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < state.queue.size - 1) {
                                    IconButton(onClick = { onMoveDown(index) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = DesignColors.InkSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(onClick = { onRemove(track.id) }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = StatusRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 3: IN-ROOM MUSIC SEARCH
// -------------------------------------------------------------------------------------------------

@Composable
private fun InRoomSearchTabView(
    graph: AppGraph,
    state: PlayTogetherState.InRoom,
    onPlayNow: (PlayTogetherTrack) -> Unit,
    onAddToQueue: (PlayTogetherTrack) -> Unit,
    onRequestSong: (PlayTogetherTrack, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<YtFeedItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var lastActionNotice by remember { mutableStateOf<String?>(null) }
    val imageLoader = LocalImageLoader.current

    fun performSearch() {
        val q = query.trim()
        if (q.isBlank()) return
        isSearching = true
        searchError = null
        scope.launch {
            try {
                val page = graph.engines.musicSearch(query = q, logger = graph.logger?.fork("room-search"))
                val results = page.sections.flatMap { it.items }
                searchResults = results.filter { it.kind == YtFeedItem.KIND_VIDEO }
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed. Check network connection."
            } finally {
                isSearching = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search songs or artists on YouTube Music...", fontSize = 13.sp, color = DesignColors.InkSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DesignColors.InkSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = DesignColors.InkSecondary)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DesignColors.Accent,
                unfocusedBorderColor = DesignColors.OutlineSoft,
                focusedTextColor = DesignColors.Ink,
                unfocusedTextColor = DesignColors.Ink,
            ),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Playback continues while searching",
                fontSize = 11.5.sp,
                color = StatusGreen,
                fontWeight = FontWeight.Medium,
            )

            Button(
                onClick = { performSearch() },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
            ) {
                Text("Search", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (lastActionNotice != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StatusGreen.copy(alpha = 0.15f))
                    .padding(8.dp),
            ) {
                Text(lastActionNotice ?: "", fontSize = 12.sp, color = StatusGreen, fontWeight = FontWeight.SemiBold)
            }
        }

        if (searchError != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(StatusRed.copy(alpha = 0.15f))
                    .padding(8.dp),
            ) {
                Text(searchError ?: "", fontSize = 12.sp, color = StatusRed)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isSearching) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DesignColors.Accent, modifier = Modifier.size(32.dp))
            }
        } else if (searchResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Search songs to add directly to the queue\nor submit a song request to the host.",
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary, lineHeight = 18.sp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(searchResults, key = { it.id }) { item ->
                    val thumbUrl = item.thumbs.lastOrNull()?.url
                    val playTrack = PlayTogetherTrack(
                        id = item.id,
                        title = item.title,
                        artist = item.channelTitle ?: item.subtitleLine1 ?: "Unknown Artist",
                        artworkUrl = thumbUrl,
                        durationMs = item.durationMs ?: 210000L,
                        isYouTube = true,
                        addedBy = state.room.hostName,
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandPurpleDark),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (thumbUrl != null && imageLoader != null) {
                                    AsyncImage(
                                        loader = imageLoader,
                                        url = thumbUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(20.dp))
                                }
                            }

                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesignColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.channelTitle ?: item.subtitleLine1 ?: "",
                                    fontSize = 11.5.sp,
                                    color = DesignColors.InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // Action buttons: Play Now, Queue, and Request
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        onPlayNow(playTrack)
                                        lastActionNotice = "Now playing '${playTrack.title}'"
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandCyan),
                                ) {
                                    Text("Play Now", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.ExtraBold)
                                }

                                Button(
                                    onClick = {
                                        onAddToQueue(playTrack)
                                        lastActionNotice = if (state.isAutoRadio || state.currentTrack == null) {
                                            "Playing '${playTrack.title}'"
                                        } else {
                                            "Added '${playTrack.title}' to queue"
                                        }
                                    },
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                                ) {
                                    Text("Queue", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                if (!state.isHost && state.controlMode == ControlMode.HOST_ONLY) {
                                    Button(
                                        onClick = {
                                            val selfName = state.members.firstOrNull { it.id == state.currentMemberId }?.name ?: "Listener"
                                            onRequestSong(playTrack, selfName)
                                            lastActionNotice = "Requested '${playTrack.title}'"
                                        },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurpleDark),
                                        border = ButtonDefaults.outlinedButtonBorder.copy(
                                            brush = Brush.horizontalGradient(listOf(BrandPurpleVibrant, BrandCyan)),
                                        ),
                                    ) {
                                        Text("Request", fontSize = 11.sp, color = BrandPurpleLight, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 4: SONG REQUESTS
// -------------------------------------------------------------------------------------------------

@Composable
private fun SongRequestsTabView(
    state: PlayTogetherState.InRoom,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    var statusFilter by remember { mutableStateOf<SongRequestStatus?>(null) }
    val imageLoader = LocalImageLoader.current

    val filtered = remember(state.songRequests, statusFilter) {
        if (statusFilter == null) state.songRequests
        else state.songRequests.filter { it.status == statusFilter }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(label = "All (${state.songRequests.size})", selected = statusFilter == null, onClick = { statusFilter = null })
            FilterChip(
                label = "Pending (${state.songRequests.count { it.status == SongRequestStatus.PENDING }})",
                selected = statusFilter == SongRequestStatus.PENDING,
                onClick = { statusFilter = SongRequestStatus.PENDING },
            )
            FilterChip(
                label = "Approved (${state.songRequests.count { it.status == SongRequestStatus.APPROVED }})",
                selected = statusFilter == SongRequestStatus.APPROVED,
                onClick = { statusFilter = SongRequestStatus.APPROVED },
            )
            FilterChip(
                label = "Rejected (${state.songRequests.count { it.status == SongRequestStatus.REJECTED }})",
                selected = statusFilter == SongRequestStatus.REJECTED,
                onClick = { statusFilter = SongRequestStatus.REJECTED },
            )
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No song requests in this category.\nListeners can submit requests from the Search tab.",
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary, lineHeight = 18.sp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { req ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrandPurpleDark),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (req.track.artworkUrl != null && imageLoader != null) {
                                        AsyncImage(
                                            loader = imageLoader,
                                            url = req.track.artworkUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(20.dp))
                                    }
                                }

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        req.track.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DesignColors.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${req.track.artist} · By ${req.requesterName}",
                                        fontSize = 11.5.sp,
                                        color = DesignColors.InkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // Status Badge
                                val (badgeBg, badgeText, badgeColor) = when (req.status) {
                                    SongRequestStatus.PENDING -> Triple(StatusAmber.copy(alpha = 0.15f), "Pending", StatusAmber)
                                    SongRequestStatus.APPROVED -> Triple(StatusGreen.copy(alpha = 0.15f), "Approved", StatusGreen)
                                    SongRequestStatus.REJECTED -> Triple(StatusRed.copy(alpha = 0.15f), "Rejected", StatusRed)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(badgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                                }
                            }

                            // Host action buttons for PENDING requests
                            if (state.isHost && req.status == SongRequestStatus.PENDING) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = { onReject(req.id) },
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text("Reject", fontSize = 11.sp, color = StatusRed)
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Button(
                                        onClick = { onApprove(req.id) },
                                        modifier = Modifier.height(30.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                    ) {
                                        Text("Approve & Queue", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (!state.isHost && req.requesterId == state.currentMemberId && req.status == SongRequestStatus.PENDING) {
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { onCancel(req.id) }) {
                                        Text("Cancel Request", fontSize = 11.sp, color = StatusRed)
                                    }
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
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DesignColors.Accent else DesignColors.SettingsCard)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else DesignColors.InkSecondary,
        )
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 5: PARTICIPANTS
// -------------------------------------------------------------------------------------------------

@Composable
private fun ParticipantsTabView(state: PlayTogetherState.InRoom, controller: PlayTogetherController) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Connected Listeners (${state.members.size})",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.members, key = { it.id }) { member ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (member.isHost) BrandPurpleVibrant else BrandCyan),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                member.name.take(1).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }

                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    member.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DesignColors.Ink,
                                )
                                if (member.isHost) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(BrandPurpleVibrant.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text("HOST", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = BrandPurpleVibrant)
                                    }
                                }
                                if (member.id == state.currentMemberId) {
                                    Text("(You)", fontSize = 11.sp, color = DesignColors.InkSecondary)
                                }
                            }

                            Text(
                                "Drift: ${member.driftMs}ms",
                                fontSize = 11.5.sp,
                                color = DesignColors.InkSecondary,
                            )
                        }

                        val (syncBg, syncLabel, syncColor) = when (member.syncState) {
                            MemberSyncState.SYNCED -> Triple(StatusGreen.copy(alpha = 0.15f), "Synced", StatusGreen)
                            MemberSyncState.BUFFERING -> Triple(StatusAmber.copy(alpha = 0.15f), "Buffering", StatusAmber)
                            MemberSyncState.SYNCING -> Triple(BrandCyan.copy(alpha = 0.15f), "Syncing", BrandCyan)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(syncBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(syncLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = syncColor)
                        }

                        if (state.isHost && member.id != state.currentMemberId) {
                            IconButton(
                                onClick = { controller.kickMember(member.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove Listener",
                                    tint = StatusRed,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 6: HOST CONTROLS
// -------------------------------------------------------------------------------------------------

@Composable
private fun HostControlsTabView(
    state: PlayTogetherState.InRoom,
    controller: PlayTogetherController,
    onLeaveRoom: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var linkCopied by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Room & Session Settings",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
        )

        // Share Invitation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Invite Listeners", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Share the 4-digit code or deep link with friends to listen together.",
                    fontSize = 12.sp,
                    color = DesignColors.InkSecondary,
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val link = InviteLinkHelper.createDeepLink(state.room.code, state.room.name)
                            clipboard.setText(AnnotatedString(link))
                            linkCopied = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (linkCopied) "Link Copied!" else "Copy Invite Link", fontSize = 12.sp, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(state.room.code))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text("Code: ${state.room.code}", fontSize = 12.sp, color = DesignColors.Ink)
                    }
                }
            }
        }

        // Host Control Permissions (Only active for Host)
        if (state.isHost) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Host Authority", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Collaborative Control", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                            Text("Allow all listeners to play, pause, and skip songs", fontSize = 11.5.sp, color = DesignColors.InkSecondary)
                        }
                        Switch(
                            checked = state.controlMode == ControlMode.COLLABORATIVE,
                            onCheckedChange = { isCollab ->
                                controller.setControlMode(if (isCollab) ControlMode.COLLABORATIVE else ControlMode.HOST_ONLY)
                            },
                        )
                    }

                    HorizontalDivider(color = DesignColors.OutlineSoft.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Allow Song Requests", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                            Text("Listeners can search and submit songs for approval", fontSize = 11.5.sp, color = DesignColors.InkSecondary)
                        }
                        Switch(
                            checked = state.allowListenerRequests,
                            onCheckedChange = { controller.toggleAllowRequests(it) },
                        )
                    }
                }
            }

            // Destructive actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Session Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink)

                    Button(
                        onClick = { controller.clearQueue() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAmber.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Clear Shared Queue", fontSize = 12.sp, color = StatusAmber, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onLeaveRoom,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("End Session & Close Room", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { controller.closeRoom() },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Delete Room", fontSize = 12.sp, color = StatusRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Listener Status", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You are connected as a listener. The host controls playback permissions and approves requests.",
                        fontSize = 12.sp,
                        color = DesignColors.InkSecondary,
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = onLeaveRoom,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Leave Room", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// DIALOGS
// -------------------------------------------------------------------------------------------------

@Composable
private fun CreateRoomDialog(
    initialTransferTrack: PlayTogetherTrack? = null,
    initialTransferPositionMs: Long = 0L,
    initialQueueCount: Int = 0,
    onDismiss: () -> Unit,
    onCreate: (name: String, hostName: String, code: String, visibility: RoomVisibility, mode: ControlMode, password: String?, transferMusic: Boolean) -> Unit,
) {
    var roomName by remember { mutableStateOf("PRVN Music Lounge") }
    var hostName by remember { mutableStateOf("PRVN Host") }
    var roomCode by remember { mutableStateOf(Random.nextInt(1000, 9999).toString()) }
    var isPrivate by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var isCollaborative by remember { mutableStateOf(false) }
    var transferMusic by remember { mutableStateOf(initialTransferTrack != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Music Room", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (initialTransferTrack != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandPurpleDark.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandPurpleVibrant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Transfer Current Music", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandPurpleLight)
                                Switch(checked = transferMusic, onCheckedChange = { transferMusic = it })
                            }
                            if (transferMusic) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "🎵 ${initialTransferTrack.title} • ${initialTransferTrack.artist}",
                                    fontSize = 11.5.sp,
                                    color = DesignColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Starting at ${formatDurationMs(initialTransferPositionMs)}" +
                                        if (initialQueueCount > 0) " + $initialQueueCount upcoming in queue" else "",
                                    fontSize = 10.5.sp,
                                    color = DesignColors.InkSecondary,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = hostName,
                    onValueChange = { hostName = it },
                    label = { Text("Your Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { input -> roomCode = input.filter { it.isDigit() }.take(4) },
                    label = { Text("4-Digit Room Code") },
                    placeholder = { Text("e.g. 5512") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Private Room (Password Protected)", fontSize = 12.sp, color = DesignColors.Ink)
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }

                if (isPrivate) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Room Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Collaborative (All Can Control)", fontSize = 12.sp, color = DesignColors.Ink)
                    Switch(checked = isCollaborative, onCheckedChange = { isCollaborative = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        roomName,
                        hostName,
                        roomCode,
                        if (isPrivate) RoomVisibility.PRIVATE else RoomVisibility.PUBLIC,
                        if (isCollaborative) ControlMode.COLLABORATIVE else ControlMode.HOST_ONLY,
                        if (isPrivate) password else null,
                        transferMusic,
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
            ) {
                Text("Create Room", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DesignColors.InkSecondary)
            }
        },
        containerColor = DesignColors.SettingsCard,
    )
}

@Composable
private fun JoinRoomDialog(
    initialCode: String = "",
    onDismiss: () -> Unit,
    onJoin: (code: String, userName: String, password: String?) -> Unit,
) {
    var roomCode by remember { mutableStateOf(initialCode) }
    var userName by remember { mutableStateOf("Listener") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Room", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { input -> roomCode = input.filter { it.isDigit() }.take(4) },
                    label = { Text("4-Digit Room Code") },
                    placeholder = { Text("e.g. 5512") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Your Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (If private)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onJoin(roomCode, userName, password.ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                enabled = roomCode.length == 4,
            ) {
                Text("Join Session", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DesignColors.InkSecondary)
            }
        },
        containerColor = DesignColors.SettingsCard,
    )
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
