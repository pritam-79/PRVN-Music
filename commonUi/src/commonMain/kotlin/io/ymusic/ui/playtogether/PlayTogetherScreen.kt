package io.ymusic.ui.playtogether

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.imageloader.AsyncImage
import io.ymusic.player.PlayableItem
import io.ymusic.settings.AppSettings
import io.ymusic.settings.flow
import io.ymusic.ui.AppGraph
import io.ymusic.ui.DesignColors
import io.ymusic.ui.LocalImageLoader
import io.ymusic.ui.LocalMiniPlayerInset
import io.ymusic.ui.PlaybackCoordinator
import io.ymusic.ui.playtogether.backend.InviteLinkHelper
import io.ymusic.ui.playtogether.backend.PlayTogetherBackendProvider
import io.ymusic.ui.playtogether.backend.RoomSecurityPolicy
import io.ymusic.ui.youtubeThumbUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock

private val BrandPurpleDark = Color(0xFF19082E)
private val BrandPurpleMedium = Color(0xFF3B0764)
private val BrandPurpleVibrant = Color(0xFFA855F7)
private val BrandPurpleLight = Color(0xFFC084FC)
private val BrandCyan = Color(0xFF06B6D4)
private val GlassBg = Color(0xCC12101A)
private val StatusGreen = Color(0xFF10B981)
private val StatusAmber = Color(0xFFF59E0B)
private val StatusRed = Color(0xFFEF4444)

private enum class RequestFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    APPROVED("Accepted/Queue"),
    HISTORY("History"),
}

@Composable
fun PlayTogetherScreen(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    controller: PlayTogetherController = graph.playTogether,
) {
    val state by controller.state.collectAsState()
    val realtimeUrl by remember(graph) { graph.settings.flow(AppSettings.playTogetherRealtimeUrl) }.collectAsState()
    val realtimeKey by remember(graph) { graph.settings.flow(AppSettings.playTogetherAnonKey) }.collectAsState()
    val savedUserName by remember(graph) { graph.settings.flow(AppSettings.playTogetherUserName) }.collectAsState()

    LaunchedEffect(realtimeUrl, realtimeKey) {
        PlayTogetherBackendProvider.configure(realtimeUrl, realtimeKey)
    }

    LaunchedEffect(state is PlayTogetherState.Lobby) {
        if (state is PlayTogetherState.Lobby) {
            while (isActive) {
                controller.refreshLobby()
                delay(15_000L)
            }
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinDialogPrefillCode by remember { mutableStateOf("") }
    var showRoomSettingsDialog by remember { mutableStateOf(false) }
    var showEndRoomDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignColors.Surface),
    ) {
        when (val s = state) {
            is PlayTogetherState.Lobby -> {
                PlayTogetherLobbyView(
                    state = s,
                    onCreateRoomClick = { showCreateDialog = true },
                    onJoinRoomClick = {
                        joinDialogPrefillCode = ""
                        showJoinDialog = true
                    },
                    onJoinPublicRoom = { room ->
                        if (room.visibility == RoomVisibility.PRIVATE) {
                            joinDialogPrefillCode = room.code
                            showJoinDialog = true
                        } else {
                            controller.joinRoom(
                                code = room.code,
                                userName = savedUserName.ifBlank { "Listener" },
                            )
                        }
                    },
                    onRefresh = { controller.refreshLobby() },
                    onDismissError = { controller.dismissError() },
                )
            }

            is PlayTogetherState.InRoom -> {
                PlayTogetherRoomView(
                    graph = graph,
                    coordinator = coordinator,
                    controller = controller,
                    state = s,
                    onOpenSettings = { showRoomSettingsDialog = true },
                    onLeaveOrEnd = {
                        if (s.isHost) {
                            showEndRoomDialog = true
                        } else {
                            controller.leaveRoom()
                        }
                    },
                )
            }
        }
    }

    if (showCreateDialog) {
        val currentQueue = coordinator.player.queue.value
        val curItem = currentQueue.currentItem
        val curPos = coordinator.player.progress.value.positionMs
        val previewTrack = remember(curItem) {
            when (curItem) {
                is PlayableItem.YtMedia -> TrackResolver.fromPlayableItem(curItem, "You")
                is PlayableItem.LocalMedia -> PlayTogetherTrack(
                    id = curItem.uri,
                    title = curItem.title ?: "Local Track",
                    artist = curItem.artist ?: "Local Track",
                    artworkUrl = curItem.thumbnailUrl,
                    durationMs = curItem.durationMs ?: 0L,
                    isYouTube = false,
                )
                null -> null
            }
        }
        val extraQueueCount = remember(currentQueue) {
            if (currentQueue.currentIndex >= 0 && currentQueue.currentIndex + 1 < currentQueue.items.size) {
                currentQueue.items.drop(currentQueue.currentIndex + 1).count { it is PlayableItem.YtMedia }
            } else {
                0
            }
        }

        CreateRoomDialog(
            initialHostName = savedUserName.takeIf { it.isNotBlank() && it != "Listener" } ?: "Host",
            initialTrack = previewTrack,
            initialPositionMs = curPos,
            initialQueueCount = extraQueueCount,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, hostName, vis, ctrl, pw, transfer ->
                showCreateDialog = false
                controller.createRoom(
                    roomName = name,
                    hostName = hostName,
                    visibility = vis,
                    controlMode = ctrl,
                    password = pw,
                    transferCurrentMusic = transfer,
                )
            },
        )
    }

    if (showJoinDialog) {
        JoinRoomDialog(
            initialCode = joinDialogPrefillCode,
            initialUserName = savedUserName.ifBlank { "Listener" },
            onDismiss = { showJoinDialog = false },
            onJoin = { code, userName, pw ->
                showJoinDialog = false
                controller.joinRoom(code, userName, pw)
            },
        )
    }

    val currentRoom = state as? PlayTogetherState.InRoom
    if (showRoomSettingsDialog && currentRoom != null) {
        RoomSettingsDialog(
            graph = graph,
            state = currentRoom,
            controller = controller,
            onDismiss = { showRoomSettingsDialog = false },
            onRequestCloseRoom = {
                showRoomSettingsDialog = false
                controller.closeRoom()
            },
            onRequestDeleteRoom = {
                showRoomSettingsDialog = false
                showDeleteConfirmDialog = true
            },
        )
    }

    if (showEndRoomDialog && currentRoom != null) {
        AlertDialog(
            onDismissRequest = { showEndRoomDialog = false },
            title = { Text("End Play Together Room?", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Closing stops synchronization for all ${currentRoom.members.size} participants. Deleting also erases saved room history.",
                    color = DesignColors.InkSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showEndRoomDialog = false
                            showDeleteConfirmDialog = true
                        },
                    ) {
                        Text("Delete Room", color = StatusRed)
                    }
                    Button(
                        onClick = {
                            showEndRoomDialog = false
                            controller.closeRoom()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                    ) {
                        Text("Close Room", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndRoomDialog = false }) {
                    Text("Cancel", color = DesignColors.InkSecondary)
                }
            },
            containerColor = DesignColors.SettingsCard,
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Room Permanently?", color = StatusRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This removes the room from public discovery, clears its shared queue & requests, and disconnects all listeners.",
                    color = DesignColors.InkSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        controller.deleteRoom()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = DesignColors.InkSecondary)
                }
            },
            containerColor = DesignColors.SettingsCard,
        )
    }
}

@Composable
private fun PlayTogetherLobbyView(
    state: PlayTogetherState.Lobby,
    onCreateRoomClick: () -> Unit,
    onJoinRoomClick: () -> Unit,
    onJoinPublicRoom: (RoomInfo) -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val bottomInset = LocalMiniPlayerInset.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))

        // Liquid Glass Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            BrandPurpleDark,
                            BrandPurpleMedium.copy(alpha = 0.85f),
                            DesignColors.Accent.copy(alpha = 0.28f),
                        ),
                    ),
                )
                .border(1.dp, BrandPurpleVibrant.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(BrandCyan))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PLAY TOGETHER",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandCyan, letterSpacing = 1.2.sp),
                        )
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh rooms", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Listen in Real-Time Sync",
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Share your Discover music, host synchronized rooms across networks, manage queues, and accept song requests.",
                    style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 16.sp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onCreateRoomClick,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Create Room", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onJoinRoomClick,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BrandPurpleVibrant.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Group, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Join by Code", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        AnimatedVisibility(visible = state.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.errorMessage.orEmpty(), color = StatusRed, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("Retry", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(onClick = onDismissError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = StatusRed, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search active rooms by name, host, or 4-digit code...", fontSize = 12.5.sp, color = DesignColors.InkFaint) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DesignColors.InkFaint, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = DesignColors.InkFaint, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandPurpleVibrant,
                unfocusedBorderColor = DesignColors.OutlineSoft,
                focusedContainerColor = GlassBg,
                unfocusedContainerColor = GlassBg,
                focusedTextColor = DesignColors.Ink,
                unfocusedTextColor = DesignColors.Ink,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        val filteredPublic = remember(state.activeRooms, searchQuery) {
            val q = searchQuery.trim()
            if (q.isEmpty()) state.activeRooms
            else state.activeRooms.filter {
                it.name.contains(q, ignoreCase = true) || it.hostName.contains(q, ignoreCase = true) || it.code.contains(q)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Public Rooms", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
            if (state.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BrandCyan)
            } else {
                Text("${filteredPublic.size} live", style = TextStyle(fontSize = 12.sp, color = BrandCyan, fontWeight = FontWeight.SemiBold))
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filteredPublic.isEmpty() && state.recentRooms.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlassBg)
                            .border(1.dp, DesignColors.OutlineSoft.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "No matching rooms" else "No public rooms right now",
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create a room to invite friends with a 4-digit code or prvn:// link.",
                                style = TextStyle(fontSize = 12.sp, color = DesignColors.InkSecondary),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else {
                items(filteredPublic, key = { it.id }) { room ->
                    LobbyRoomCard(room = room, isRecent = false, onJoin = { onJoinPublicRoom(room) })
                }

                if (state.recentRooms.isNotEmpty() && searchQuery.isBlank()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Recent Sessions", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.InkSecondary))
                    }
                    items(state.recentRooms, key = { "recent_${it.id}" }) { room ->
                        LobbyRoomCard(room = room, isRecent = true, onJoin = { onJoinPublicRoom(room) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyRoomCard(
    room: RoomInfo,
    isRecent: Boolean,
    onJoin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onJoin() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBg),
        border = BorderStroke(1.dp, if (isRecent) DesignColors.OutlineSoft.copy(alpha = 0.5f) else BrandPurpleVibrant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            room.name,
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (room.visibility == RoomVisibility.PRIVATE) {
                            Icon(Icons.Default.Lock, contentDescription = "Private", tint = StatusAmber, modifier = Modifier.size(13.dp))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Host: ${room.hostName} • Code: ${room.code} • ${if (room.controlMode == ControlMode.COLLABORATIVE) "Collab" else "Host Controls"}",
                        style = TextStyle(fontSize = 11.5.sp, color = BrandPurpleLight),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Group, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(13.dp))
                        Text("${room.memberCount}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BrandCyan)
                    }
                }
            }

            room.currentTrack?.let { track ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BrandPurpleDark.copy(alpha = 0.55f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackThumb(track = track, size = 34.dp, cornerRadius = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, fontSize = 11.sp, color = DesignColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("JOIN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = DesignColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun PlayTogetherRoomView(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    controller: PlayTogetherController,
    state: PlayTogetherState.InRoom,
    onOpenSettings: () -> Unit,
    onLeaveOrEnd: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copiedBanner by remember { mutableStateOf(false) }
    val canControl = RoomSecurityPolicy.canControlPlayback(state.isHost, state.controlMode)

    LaunchedEffect(copiedBanner) {
        if (copiedBanner) {
            delay(2000L)
            copiedBanner = false
        }
    }

    LaunchedEffect(state.statusBanner) {
        if (state.statusBanner != null) {
            delay(3500L)
            controller.clearStatusBanner()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Room Header Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DesignColors.SurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            state.room.name,
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (state.isHost) DesignColors.Accent.copy(alpha = 0.2f) else BrandPurpleVibrant.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                if (state.isHost) "HOST" else "LISTENER",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (state.isHost) DesignColors.Accent else BrandPurpleLight,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable {
                                    clipboard.setText(AnnotatedString(state.room.code))
                                    copiedBanner = true
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text("Code: ${state.room.code}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = BrandCyan)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (copiedBanner) Icons.Default.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy room code",
                                tint = if (copiedBanner) StatusGreen else BrandCyan,
                                modifier = Modifier.size(12.dp),
                            )
                        }

                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share room invite",
                            tint = BrandPurpleLight,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    val deepLink = InviteLinkHelper.createDeepLink(state.room.code, state.room.name)
                                    val msg = "Join my PRVN Play Together room \"${state.room.name}\"!\nCode: ${state.room.code}\n$deepLink"
                                    val shareFn = graph.shareText
                                    if (shareFn != null) shareFn(msg) else clipboard.setText(AnnotatedString(deepLink))
                                    copiedBanner = true
                                },
                        )

                        RoomSyncStatusPill(state = state, onClickResync = { controller.resyncPlayback() })
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Room Settings", tint = DesignColors.InkSecondary, modifier = Modifier.size(19.dp))
                    }
                    Button(
                        onClick = onLeaveOrEnd,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isHost) StatusRed.copy(alpha = 0.18f) else DesignColors.SettingsCard,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(
                            if (state.isHost) "End" else "Leave",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isHost) StatusRed else DesignColors.InkSecondary,
                        )
                    }
                }
            }
        }

        // Live Host Song Request Alert Banner
        AnimatedVisibility(visible = state.isHost && state.incomingRequestAlert != null) {
            state.incomingRequestAlert?.let { alertReq ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BrandPurpleDark),
                    border = BorderStroke(1.dp, DesignColors.Accent.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = DesignColors.Accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "New Song Request • ${alertReq.requesterName}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DesignColors.Accent,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { controller.dismissIncomingRequestAlert() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = DesignColors.InkSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrackThumb(track = alertReq.track, size = 38.dp, cornerRadius = 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(alertReq.track.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(alertReq.track.artist, fontSize = 11.5.sp, color = DesignColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { controller.acceptSongRequest(alertReq.id) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.6f)),
                            ) {
                                Text("Accept", fontSize = 11.sp, color = StatusGreen, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { controller.rejectSongRequest(alertReq.id) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f)),
                            ) {
                                Text("Reject", fontSize = 11.sp, color = StatusRed, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { controller.approveSongRequest(alertReq.id, playNow = false) },
                                modifier = Modifier.weight(1.25f).height(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                            ) {
                                Text("Add to Queue", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Transient Error or Status Feedback
        AnimatedVisibility(visible = state.errorMessage != null || state.statusBanner != null) {
            val isErr = state.errorMessage != null
            val msg = state.errorMessage ?: state.statusBanner.orEmpty()
            val tint = if (isErr) StatusRed else BrandCyan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (isErr) {
                    TextButton(
                        onClick = {
                            if (state.isPlaybackFailed) controller.retryCurrentStream() else controller.resyncPlayback()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp),
                    ) {
                        Text("Retry", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                IconButton(onClick = { controller.dismissError() }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = tint, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Exact 5-Tab Navigation Bar
        val tabs = RoomTab.entries
        val selectedIndex = tabs.indexOf(state.activeTab).coerceAtLeast(0)
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            containerColor = DesignColors.Surface,
            contentColor = DesignColors.Accent,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = DesignColors.Accent,
                    height = 2.5.dp,
                )
            },
            divider = { HorizontalDivider(color = DesignColors.OutlineSoft.copy(alpha = 0.3f)) },
        ) {
            tabs.forEach { tab ->
                val selected = tab == state.activeTab
                val badgeCount = when (tab) {
                    RoomTab.SONG_REQUESTS -> state.pendingRequestCount
                    RoomTab.SHARED_QUEUE -> state.queue.size
                    RoomTab.PARTICIPANTS -> state.members.size
                    else -> 0
                }
                Tab(
                    selected = selected,
                    onClick = { controller.selectTab(tab) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                tab.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) DesignColors.Accent else DesignColors.InkSecondary,
                            )
                            if (badgeCount > 0) {
                                val badgeBg = if (tab == RoomTab.SONG_REQUESTS) DesignColors.Accent else BrandPurpleVibrant.copy(alpha = 0.35f)
                                val badgeFg = if (tab == RoomTab.SONG_REQUESTS) Color.White else BrandPurpleLight
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(badgeBg)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text("$badgeCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeFg)
                                }
                            }
                        }
                    },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.activeTab) {
                RoomTab.NOW_PLAYING -> NowPlayingRoomTab(graph, coordinator, controller, state, canControl)
                RoomTab.SHARED_QUEUE -> SharedQueueRoomTab(controller, state, canControl)
                RoomTab.SEARCH_MUSIC -> InRoomSearchTab(controller, state, canControl)
                RoomTab.SONG_REQUESTS -> SongRequestsRoomTab(controller, state, canControl)
                RoomTab.PARTICIPANTS -> ParticipantsRoomTab(controller, state, onOpenSettings)
            }
        }
    }
}

@Composable
private fun RoomSyncStatusPill(
    state: PlayTogetherState.InRoom,
    onClickResync: () -> Unit,
) {
    val (label, color) = when {
        state.connectionState == PlaybackConnectionState.OFFLINE -> "Offline" to StatusRed
        state.connectionState == PlaybackConnectionState.RECONNECTING -> "Reconnecting..." to StatusAmber
        state.isPlaybackFailed -> "Stream Error" to StatusRed
        state.isBuffering -> "Buffering..." to BrandCyan
        state.currentTrack == null -> "Connected" to StatusGreen
        state.isHost && state.isPlaying -> "Host Live" to StatusGreen
        state.isHost -> "Host Paused" to BrandPurpleLight
        !state.isPlaying -> "Paused" to BrandPurpleLight
        state.syncQuality == SyncQuality.EXCELLENT || state.syncQuality == SyncQuality.ACCEPTABLE ->
            "Synced • ${state.syncOffsetMs}ms" to StatusGreen
        state.syncQuality == SyncQuality.CORRECTING ->
            "Syncing • ${state.syncOffsetMs}ms" to BrandCyan
        state.syncQuality == SyncQuality.OUT_OF_SYNC ->
            "Drift • ${state.syncOffsetMs}ms" to StatusAmber
        else -> "Syncing..." to BrandPurpleLight
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable { onClickResync() }
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun NowPlayingRoomTab(
    graph: AppGraph,
    coordinator: PlaybackCoordinator,
    controller: PlayTogetherController,
    state: PlayTogetherState.InRoom,
    canControl: Boolean,
) {
    val track = state.currentTrack
    val progressState by coordinator.player.progress.collectAsState()
    val bottomInset = LocalMiniPlayerInset.current
    val totalMs = maxOf(track?.durationMs ?: 0L, progressState.durationMs).coerceAtLeast(0L)
    val currentMs = if (totalMs > 0L) state.positionMs.coerceIn(0L, totalMs) else state.positionMs.coerceAtLeast(0L)
    var draggingFraction by remember { mutableStateOf<Float?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomInset + 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            // Centered Glowing Liquid Glass Artwork (clickable to expand full NowPlaying Video/Lyrics!)
            Box(
                modifier = Modifier
                    .size(196.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(BrandPurpleMedium, BrandPurpleDark),
                        ),
                    )
                    .border(1.2.dp, BrandPurpleVibrant.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .clickable(enabled = track != null) { graph.pip.nowPlayingOpen.value = true },
                contentAlignment = Alignment.Center,
            ) {
                TrackThumb(track = track, size = 196.dp, cornerRadius = 22.dp, iconSize = 60.dp)

                if (state.isBuffering && track != null) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandCyan, strokeWidth = 3.dp)
                    }
                }

                if (track != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.OpenInFull,
                            contentDescription = "Open full Now Playing / Video / Lyrics",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = track?.title ?: "Ready to Listen Together",
                style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = track?.let {
                    listOfNotNull(it.artist, it.album).joinToString(" • ")
                } ?: "Search a song or start playback from Discover",
                style = TextStyle(fontSize = 13.5.sp, color = BrandPurpleLight),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            if (track != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            if (track.isAutoRadio) "Auto-Radio" else "Added by ${track.addedBy}",
                            fontSize = 10.5.sp,
                            color = DesignColors.InkSecondary,
                        )
                    }
                    if (state.networkRttMs > 0L) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BrandCyan.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("Net RTT ${state.networkRttMs}ms", fontSize = 10.5.sp, color = BrandCyan)
                        }
                    }
                    if (!state.isHost && !state.isPlaybackFailed && state.syncOffsetMs > 0L) {
                        val driftColor = when (state.syncQuality) {
                            SyncQuality.EXCELLENT, SyncQuality.ACCEPTABLE -> StatusGreen
                            SyncQuality.CORRECTING -> BrandCyan
                            SyncQuality.OUT_OF_SYNC -> StatusAmber
                            else -> DesignColors.InkSecondary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(driftColor.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("Drift ${state.syncOffsetMs}ms", fontSize = 10.5.sp, color = driftColor)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Seek Slider
            val fraction = if (totalMs > 0L) (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
            Slider(
                value = (draggingFraction ?: fraction).coerceIn(0f, 1f),
                enabled = track != null && totalMs > 0L && canControl,
                onValueChange = { draggingFraction = it },
                onValueChangeFinished = {
                    draggingFraction?.let { frac ->
                        if (totalMs > 0L) controller.seekTo((frac * totalMs).toLong())
                    }
                    draggingFraction = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = DesignColors.Accent,
                    activeTrackColor = DesignColors.Accent,
                    inactiveTrackColor = DesignColors.OutlineSoft,
                    disabledThumbColor = BrandPurpleLight.copy(alpha = 0.6f),
                    disabledActiveTrackColor = BrandPurpleVibrant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDurationMs(currentMs), fontSize = 11.sp, color = DesignColors.InkSecondary)
                Text(if (totalMs > 0L) formatDurationMs(totalMs) else "--:--", fontSize = 11.sp, color = DesignColors.InkSecondary)
            }

            Spacer(Modifier.height(8.dp))

            // Transport Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { controller.previousTrack() },
                    enabled = track != null && canControl,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (track != null && canControl) Color.White else DesignColors.InkFaint,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (canControl) DesignColors.Accent else BrandPurpleDark,
                        )
                        .border(1.dp, if (canControl) Color.White.copy(alpha = 0.25f) else BrandPurpleVibrant.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            if (canControl) controller.playPause() else controller.resyncPlayback()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!canControl) {
                        Icon(Icons.Default.Refresh, contentDescription = "Resync with host", tint = BrandCyan, modifier = Modifier.size(28.dp))
                    } else if (state.isPlaying) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }

                IconButton(
                    onClick = { controller.skipTrack() },
                    enabled = track != null && canControl,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = if (track != null && canControl) Color.White else DesignColors.InkFaint,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            if (!canControl) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Synced to ${state.room.hostName} • Tap Resync anytime if playback drifts",
                    fontSize = 11.5.sp,
                    color = BrandPurpleLight,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { controller.selectTab(RoomTab.SEARCH_MUSIC) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BrandPurpleVibrant.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = DesignColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (canControl) "Search & Queue" else "Request Song", fontSize = 12.sp, color = Color.White)
                }

                if (canControl) {
                    OutlinedButton(
                        onClick = { controller.transferCurrentMusicToRoom() },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BrandCyan.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Outlined.PlaylistPlay, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pull App Track", fontSize = 12.sp, color = BrandCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedQueueRoomTab(
    controller: PlayTogetherController,
    state: PlayTogetherState.InRoom,
    canControl: Boolean,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val bottomInset = LocalMiniPlayerInset.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Shared Queue (${state.queue.size})",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            if (canControl && state.queue.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("Clear Queue", fontSize = 12.sp, color = StatusRed)
                }
            }
        }

        if (state.queue.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.QueueMusic, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Shared queue is empty", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (canControl) "Add songs from Search or Discover, or let Discover Auto-Radio play next."
                        else "Send a song request from Search or Discover to get added next!",
                        fontSize = 12.sp,
                        color = DesignColors.InkSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { controller.selectTab(RoomTab.SEARCH_MUSIC) },
                        colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                    ) {
                        Text(if (canControl) "Add Songs" else "Request a Song", color = Color.White)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.queue, key = { idx, t -> "${t.id}_$idx" }) { index, track ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GlassBg),
                        border = BorderStroke(1.dp, DesignColors.OutlineSoft.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable(enabled = canControl) { controller.playNow(track) },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPurpleLight,
                                modifier = Modifier.width(22.dp),
                            )
                            TrackThumb(track = track, size = 44.dp, cornerRadius = 8.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(track.artist, track.album, formatOptionalDuration(track.durationMs)).joinToString(" • "),
                                    fontSize = 11.5.sp,
                                    color = DesignColors.InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("Added by ${track.addedBy}", fontSize = 10.5.sp, color = BrandCyan)
                            }
                            if (canControl) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { controller.playNow(track) }, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play now", tint = DesignColors.Accent, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { controller.moveQueueUp(index) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = if (index > 0) Color.White else DesignColors.InkFaint, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { controller.moveQueueDown(index) }, enabled = index < state.queue.lastIndex, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = if (index < state.queue.lastIndex) Color.White else DesignColors.InkFaint, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { controller.removeFromQueue(index) }, modifier = Modifier.size(28.dp)) {
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Shared Queue?", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
            text = { Text("Remove all ${state.queue.size} queued songs for everyone in the room?", color = DesignColors.InkSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        controller.clearQueue()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                ) { Text("Clear", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = DesignColors.InkSecondary) }
            },
            containerColor = DesignColors.SettingsCard,
        )
    }
}

@Composable
private fun InRoomSearchTab(
    controller: PlayTogetherController,
    state: PlayTogetherState.InRoom,
    canControl: Boolean,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(PlayTogetherSearchSource.MUSIC_SONGS) }
    var searchResult by remember { mutableStateOf(PlayTogetherSearchResult(emptyList())) }
    var isSearching by remember { mutableStateOf(true) }
    val bottomInset = LocalMiniPlayerInset.current

    fun runSearch(q: String = query, src: PlayTogetherSearchSource = selectedSource) {
        scope.launch {
            isSearching = true
            searchResult = controller.searchSongs(q, src)
            isSearching = false
        }
    }

    LaunchedEffect(selectedSource) {
        if (query.isBlank() || searchResult.tracks.isEmpty()) {
            runSearch(query, selectedSource)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search songs, artists, or YouTube link...", fontSize = 12.5.sp, color = DesignColors.InkFaint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DesignColors.InkFaint, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            runSearch("", selectedSource)
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = DesignColors.InkFaint, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DesignColors.Accent,
                    unfocusedBorderColor = DesignColors.OutlineSoft,
                    focusedContainerColor = GlassBg,
                    unfocusedContainerColor = GlassBg,
                    focusedTextColor = DesignColors.Ink,
                    unfocusedTextColor = DesignColors.Ink,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { runSearch(query, selectedSource) },
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(50.dp),
            ) {
                Text("Go", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PlayTogetherSearchSource.entries) { src ->
                val selected = src == selectedSource
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedSource = src
                        runSearch(query, src)
                    },
                    label = { Text(src.label, fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DesignColors.Accent.copy(alpha = 0.25f),
                        selectedLabelColor = DesignColors.Accent,
                    ),
                )
            }
        }

        searchResult.errorMessage?.let { err ->
            Spacer(Modifier.height(6.dp))
            Text(err, color = StatusAmber, fontSize = 12.sp)
        }

        Spacer(Modifier.height(6.dp))

        if (isSearching && searchResult.tracks.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = DesignColors.Accent)
                    Spacer(Modifier.height(8.dp))
                    Text("Loading PRVN Discover catalog...", fontSize = 12.sp, color = DesignColors.InkSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(searchResult.tracks, key = { it.id }) { track ->
                    val alreadyPlaying = state.currentTrack?.id == track.id
                    val alreadyQueued = state.queue.any { it.id == track.id }
                    val alreadyRequested = state.songRequests.any { it.track.id == track.id && !it.status.isTerminal }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = GlassBg),
                        border = BorderStroke(1.dp, DesignColors.OutlineSoft.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TrackThumb(track = track, size = 48.dp, cornerRadius = 8.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(track.artist, track.album, formatOptionalDuration(track.durationMs)).joinToString(" • "),
                                        fontSize = 11.5.sp,
                                        color = DesignColors.InkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (canControl) {
                                    Button(
                                        onClick = { controller.playNow(track) },
                                        enabled = !alreadyPlaying,
                                        colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp).weight(1f),
                                    ) {
                                        Text(if (alreadyPlaying) "Playing" else "Play Now", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { controller.playNextInRoom(track) },
                                        enabled = !alreadyPlaying,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        border = BorderStroke(1.dp, BrandPurpleVibrant.copy(alpha = 0.6f)),
                                        modifier = Modifier.height(32.dp).weight(1f),
                                    ) {
                                        Text("Play Next", fontSize = 11.sp, color = BrandPurpleLight)
                                    }
                                    OutlinedButton(
                                        onClick = { controller.addToQueue(track) },
                                        enabled = !alreadyPlaying && !alreadyQueued,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        border = BorderStroke(1.dp, BrandCyan.copy(alpha = 0.6f)),
                                        modifier = Modifier.height(32.dp).weight(1f),
                                    ) {
                                        Text(if (alreadyQueued) "Queued" else "+ Queue", fontSize = 11.sp, color = BrandCyan)
                                    }
                                } else {
                                    Button(
                                        onClick = { controller.requestSong(track) },
                                        enabled = state.allowListenerRequests && !alreadyPlaying && !alreadyQueued && !alreadyRequested,
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurpleVibrant),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp).fillMaxWidth(),
                                    ) {
                                        val btnLabel = when {
                                            alreadyPlaying -> "Currently Playing"
                                            alreadyQueued -> "In Queue"
                                            alreadyRequested -> "Already Requested"
                                            !state.allowListenerRequests -> "Requests Paused"
                                            else -> "Request Song"
                                        }
                                        Text(btnLabel, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
private fun SongRequestsRoomTab(
    controller: PlayTogetherController,
    state: PlayTogetherState.InRoom,
    canControl: Boolean,
) {
    var filter by remember { mutableStateOf(RequestFilter.ALL) }
    val bottomInset = LocalMiniPlayerInset.current

    val filtered = remember(state.songRequests, filter) {
        when (filter) {
            RequestFilter.ALL -> state.songRequests
            RequestFilter.PENDING -> state.songRequests.filter { it.status == SongRequestStatus.PENDING }
            RequestFilter.APPROVED -> state.songRequests.filter {
                it.status == SongRequestStatus.ACCEPTED || it.status == SongRequestStatus.ADDED_TO_QUEUE
            }
            RequestFilter.HISTORY -> state.songRequests.filter { it.status.isTerminal }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RequestFilter.entries) { f ->
                val count = when (f) {
                    RequestFilter.ALL -> state.songRequests.size
                    RequestFilter.PENDING -> state.songRequests.count { it.status == SongRequestStatus.PENDING }
                    RequestFilter.APPROVED -> state.songRequests.count { it.status == SongRequestStatus.ACCEPTED || it.status == SongRequestStatus.ADDED_TO_QUEUE }
                    RequestFilter.HISTORY -> state.songRequests.count { it.status.isTerminal }
                }
                FilterChip(
                    selected = f == filter,
                    onClick = { filter = f },
                    label = { Text("${f.label} ($count)", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DesignColors.Accent.copy(alpha = 0.25f),
                        selectedLabelColor = DesignColors.Accent,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No song requests here", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Participants can search in Play Together or request directly from Discover & Search!",
                        fontSize = 12.sp,
                        color = DesignColors.InkSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { controller.selectTab(RoomTab.SEARCH_MUSIC) }) {
                        Text("Search & Request a Song", color = DesignColors.Accent)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { req ->
                    val statusColor = when (req.status) {
                        SongRequestStatus.PENDING -> StatusAmber
                        SongRequestStatus.ACCEPTED -> BrandCyan
                        SongRequestStatus.ADDED_TO_QUEUE -> StatusGreen
                        SongRequestStatus.PLAYED -> BrandPurpleLight
                        SongRequestStatus.REJECTED -> StatusRed
                        SongRequestStatus.CANCELLED -> DesignColors.InkFaint
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = GlassBg),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TrackThumb(track = req.track, size = 46.dp, cornerRadius = 8.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(req.track.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(req.track.artist, req.track.album, formatOptionalDuration(req.track.durationMs)).joinToString(" • "),
                                        fontSize = 11.5.sp,
                                        color = DesignColors.InkSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Requested by ${req.requesterName} • ${formatRelativeTime(req.requestTimeMs)}",
                                        fontSize = 10.5.sp,
                                        color = BrandPurpleLight,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusColor.copy(alpha = 0.16f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(req.status.displayLabel, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                            }

                            val isMyPending = req.requesterId == state.currentMemberId && req.status == SongRequestStatus.PENDING
                            if ((canControl && !req.status.isTerminal && req.status != SongRequestStatus.ADDED_TO_QUEUE) || isMyPending) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    if (canControl && (req.status == SongRequestStatus.PENDING || req.status == SongRequestStatus.ACCEPTED)) {
                                        Button(
                                            onClick = { controller.approveSongRequest(req.id, playNow = true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(32.dp).weight(1f),
                                        ) { Text("Play Now", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }

                                        OutlinedButton(
                                            onClick = { controller.approveSongRequest(req.id, playNow = false) },
                                            border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.6f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(32.dp).weight(1f),
                                        ) { Text("+ Queue", fontSize = 11.sp, color = StatusGreen, fontWeight = FontWeight.Bold) }

                                        if (req.status == SongRequestStatus.PENDING) {
                                            OutlinedButton(
                                                onClick = { controller.acceptSongRequest(req.id) },
                                                border = BorderStroke(1.dp, BrandCyan.copy(alpha = 0.6f)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(32.dp).weight(0.9f),
                                            ) { Text("Accept", fontSize = 11.sp, color = BrandCyan, fontWeight = FontWeight.Bold) }
                                        }

                                        OutlinedButton(
                                            onClick = { controller.rejectSongRequest(req.id) },
                                            border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(32.dp).weight(0.85f),
                                        ) { Text("Reject", fontSize = 11.sp, color = StatusRed, fontWeight = FontWeight.Bold) }
                                    } else if (isMyPending) {
                                        OutlinedButton(
                                            onClick = { controller.cancelSongRequest(req.id) },
                                            border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(32.dp),
                                        ) { Text("Cancel Request", fontSize = 11.sp, color = StatusRed) }
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
private fun ParticipantsRoomTab(
    controller: PlayTogetherController,
    state: PlayTogetherState.InRoom,
    onOpenSettings: () -> Unit,
) {
    val bottomInset = LocalMiniPlayerInset.current

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = GlassBg),
            border = BorderStroke(1.dp, BrandPurpleVibrant.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${state.members.size} Participant${if (state.members.size == 1) "" else "s"} Connected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "Visibility: ${state.room.visibility.name.replace('_', ' ')} • Control: ${if (state.controlMode == ControlMode.COLLABORATIVE) "Collaborative" else "Host Only"}",
                        fontSize = 11.5.sp,
                        color = BrandPurpleLight,
                    )
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Room Info", fontSize = 11.5.sp, color = BrandCyan)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.members, key = { it.id }) { member ->
                val isSelf = member.id == state.currentMemberId
                val (syncText, syncColor) = when (member.syncState) {
                    MemberSyncState.SYNCED, MemberSyncState.PLAYING -> member.syncState.displayLabel to StatusGreen
                    MemberSyncState.RESOLVING, MemberSyncState.BUFFERING -> member.syncState.displayLabel to BrandCyan
                    MemberSyncState.WAITING, MemberSyncState.PREPARING, MemberSyncState.PAUSED, MemberSyncState.SYNCING ->
                        member.syncState.displayLabel to BrandPurpleLight
                    MemberSyncState.RECONNECTING -> member.syncState.displayLabel to StatusAmber
                    MemberSyncState.STREAM_UNAVAILABLE, MemberSyncState.FAILED -> member.syncState.displayLabel to StatusRed
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = GlassBg),
                    border = BorderStroke(1.dp, if (member.isHost) DesignColors.Accent.copy(alpha = 0.45f) else DesignColors.OutlineSoft.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (member.isHost) DesignColors.Accent.copy(alpha = 0.25f) else BrandPurpleDark),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                member.name.take(1).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (member.isHost) DesignColors.Accent else BrandPurpleLight,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    member.name + if (isSelf) " (You)" else "",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                if (member.isHost) {
                                    Text("HOST", fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = DesignColors.Accent)
                                }
                            }
                            Text(
                                if (member.driftMs > 0L) "$syncText • ${member.driftMs}ms drift" else syncText,
                                fontSize = 11.5.sp,
                                color = syncColor,
                            )
                        }

                        if (state.isHost && !member.isHost && !isSelf) {
                            IconButton(onClick = { controller.kickMember(member.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove participant", tint = StatusRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomSettingsDialog(
    graph: AppGraph,
    state: PlayTogetherState.InRoom,
    controller: PlayTogetherController,
    onDismiss: () -> Unit,
    onRequestCloseRoom: () -> Unit,
    onRequestDeleteRoom: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val deepLink = remember(state.room.code) { InviteLinkHelper.createDeepLink(state.room.code, state.room.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Room Management", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Room: ${state.room.name} (Code ${state.room.code})", fontSize = 12.5.sp, color = BrandCyan, fontWeight = FontWeight.SemiBold)

                if (state.isHost) {
                    Text("Room Visibility", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            RoomVisibility.PUBLIC to "Public",
                            RoomVisibility.INVITE_ONLY to "Invite",
                            RoomVisibility.PRIVATE to "Private",
                        ).forEach { (vis, label) ->
                            FilterChip(
                                selected = state.room.visibility == vis,
                                onClick = { controller.setRoomVisibility(vis) },
                                label = { Text(label, fontSize = 11.sp) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Collaborative Playback", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                            Text("Allow all listeners to control queue & play/pause", fontSize = 11.sp, color = DesignColors.InkSecondary)
                        }
                        Switch(checked = state.controlMode == ControlMode.COLLABORATIVE, onCheckedChange = { controller.toggleControlMode() })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Song Requests", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                            Text("Participants can suggest tracks from Search & Discover", fontSize = 11.sp, color = DesignColors.InkSecondary)
                        }
                        Switch(checked = state.allowListenerRequests, onCheckedChange = { controller.toggleAllowRequests() })
                    }
                }

                OutlinedButton(
                    onClick = {
                        val text = "Join PRVN Play Together \"${state.room.name}\"! Code: ${state.room.code}\n$deepLink"
                        val shareFn = graph.shareText
                        if (shareFn != null) shareFn(text) else clipboard.setText(AnnotatedString(deepLink))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Invite Link")
                }

                if (state.isHost) {
                    HorizontalDivider(color = DesignColors.OutlineSoft.copy(alpha = 0.5f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onRequestCloseRoom,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.6f)),
                        ) { Text("Close Room", fontSize = 11.5.sp, color = StatusAmber) }
                        OutlinedButton(
                            onClick = onRequestDeleteRoom,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.7f)),
                        ) { Text("Delete Room", fontSize = 11.5.sp, color = StatusRed) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = DesignColors.Accent) }
        },
        containerColor = DesignColors.SettingsCard,
    )
}

@Composable
private fun TrackThumb(
    track: PlayTogetherTrack?,
    size: Dp,
    cornerRadius: Dp,
    iconSize: Dp = size * 0.48f,
) {
    val imageLoader = LocalImageLoader.current
    val effectiveUrl = remember(track?.id, track?.artworkUrl) {
        when {
            track == null -> null
            !track.artworkUrl.isNullOrBlank() && track.artworkUrl != "null" -> track.artworkUrl
            TrackResolver.isValidYouTubeId(track.id) -> youtubeThumbUrl(track.id)
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(BrandPurpleDark)
            .border(1.dp, BrandPurpleVibrant.copy(alpha = 0.25f), RoundedCornerShape(cornerRadius)),
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
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(iconSize))
                },
                error = {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(iconSize))
                },
                crossfadeMs = 200,
            )
        } else {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun CreateRoomDialog(
    initialHostName: String,
    initialTrack: PlayTogetherTrack?,
    initialPositionMs: Long,
    initialQueueCount: Int,
    onDismiss: () -> Unit,
    onCreate: (
        roomName: String,
        hostName: String,
        visibility: RoomVisibility,
        controlMode: ControlMode,
        password: String?,
        transferCurrentMusic: Boolean,
    ) -> Unit,
) {
    var roomName by remember { mutableStateOf("PRVN Music Lounge") }
    var hostName by remember { mutableStateOf(initialHostName) }
    var visibility by remember { mutableStateOf(RoomVisibility.PUBLIC) }
    var isCollaborative by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var transferMusic by remember { mutableStateOf(initialTrack != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Play Together Room", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (initialTrack != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandPurpleDark.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, BrandPurpleVibrant.copy(alpha = 0.45f)),
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
                                Text("${initialTrack.title} • ${initialTrack.artist}", fontSize = 11.5.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "Resumes at ${formatDurationMs(initialPositionMs)}" +
                                        if (initialQueueCount > 0) " + $initialQueueCount queued" else "",
                                    fontSize = 10.5.sp,
                                    color = BrandCyan,
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
                    label = { Text("Host Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Visibility", fontSize = 11.5.sp, color = DesignColors.InkSecondary, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RoomVisibility.PUBLIC to "Public",
                        RoomVisibility.INVITE_ONLY to "Invite Only",
                        RoomVisibility.PRIVATE to "Private",
                    ).forEach { (vis, label) ->
                        FilterChip(
                            selected = visibility == vis,
                            onClick = { visibility = vis },
                            label = { Text(label, fontSize = 11.sp) },
                        )
                    }
                }

                if (visibility == RoomVisibility.PRIVATE) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passcode (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Collaborative (Friends Can Control)", fontSize = 12.sp, color = DesignColors.Ink)
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
                        visibility,
                        if (isCollaborative) ControlMode.COLLABORATIVE else ControlMode.HOST_ONLY,
                        if (visibility == RoomVisibility.PRIVATE) password.ifBlank { null } else null,
                        transferMusic,
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
            ) { Text("Create Room", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DesignColors.InkSecondary) }
        },
        containerColor = DesignColors.SettingsCard,
    )
}

@Composable
private fun JoinRoomDialog(
    initialCode: String = "",
    initialUserName: String = "Listener",
    onDismiss: () -> Unit,
    onJoin: (code: String, userName: String, password: String?) -> Unit,
) {
    var roomCode by remember { mutableStateOf(initialCode) }
    var userName by remember { mutableStateOf(initialUserName) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Play Together Room", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
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
                    label = { Text("Your Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Passcode (If private)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(roomCode, userName, password.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                enabled = roomCode.length == 4,
            ) { Text("Join Room", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DesignColors.InkSecondary) }
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

private fun formatOptionalDuration(ms: Long): String? =
    if (ms > 0L) formatDurationMs(ms) else null

private fun formatRelativeTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Just now"
    val diffSec = ((Clock.System.now().toEpochMilliseconds() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3600L}h ago"
        else -> "${diffSec / 86_400L}d ago"
    }
}
