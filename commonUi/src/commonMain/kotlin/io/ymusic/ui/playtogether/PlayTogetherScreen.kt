package io.ymusic.ui.playtogether

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ymusic.settings.AppSettings
import io.ymusic.settings.flow
import io.ymusic.ui.AppGraph
import io.ymusic.ui.DesignColors
import io.ymusic.ui.DesignSpacing
import io.ymusic.ui.LocalMiniPlayerInset
import io.ymusic.ui.PlaybackCoordinator
import io.ymusic.ui.playtogether.backend.PlayTogetherBackendProvider
import io.ymusic.ui.resources.Res
import io.ymusic.ui.resources.ymusic_logo
import org.jetbrains.compose.resources.painterResource

private val BrandPurpleDark = Color(0xFF1E0A3C)
private val BrandPurpleMedium = Color(0xFF3B0764)
private val BrandPurpleVibrant = Color(0xFFA855F7)
private val BrandPurpleLight = Color(0xFFC084FC)
private val StatusGreen = Color(0xFF10B981)
private val StatusAmber = Color(0xFFF59E0B)

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

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showAddSongDialog by remember { mutableStateOf(false) }
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
                    state = s,
                    onTogglePlayPause = { controller.togglePlayPause() },
                    onNext = { controller.nextTrack() },
                    onPrevious = { controller.previousTrack() },
                    onSeek = { pos -> controller.seekTo(pos) },
                    onLeaveClick = { showLeaveConfirm = true },
                    onAddSongClick = { showAddSongDialog = true },
                    onRemoveFromQueue = { id -> controller.removeFromQueue(id) },
                )
            }
        }

        // Dialogs
        if (showCreateDialog) {
            CreateRoomDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, host, code, vis, mode ->
                    showCreateDialog = false
                    controller.createRoom(name, host, code, vis, mode)
                },
            )
        }

        if (showJoinDialog) {
            JoinRoomDialog(
                initialCode = joinPresetCode,
                onDismiss = { showJoinDialog = false },
                onJoin = { code, user ->
                    showJoinDialog = false
                    controller.joinRoom(code, user)
                },
            )
        }

        if (showAddSongDialog) {
            AddSongToQueueDialog(
                onDismiss = { showAddSongDialog = false },
                onAdd = { track ->
                    showAddSongDialog = false
                    controller.addToQueue(track)
                },
            )
        }

        if (showLeaveConfirm) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text("Leave Room?", color = DesignColors.Ink, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to disconnect from this synchronized listening session?", color = DesignColors.InkSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            showLeaveConfirm = false
                            controller.leaveRoom()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                    ) {
                        Text("Leave", color = Color.White)
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
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    onQuickJoin: (RoomInfo) -> Unit,
) {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusGreen),
                        )
                        Text(
                            text = "REALTIME SYNCHRONIZED PLAYBACK",
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPurpleLight,
                                letterSpacing = 1.sp,
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Listen Together Anywhere",
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Synchronize audio playback with friends in real time. Ultra-low drift (<50ms) across Android and desktop.",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 18.sp,
                        ),
                    )
                }
            }
        }

        if (!PlayTogetherBackendProvider.isLiveConfigured) {
            item("transparency_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1035))
                        .border(1.dp, Color(0xFF7C3AED).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFA855F7)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Local Relay Mode (Offline / Preview)",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE9D5FF)),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Operating on-device via local simulated relay. To sync across separate devices in real time, configure your Supabase Realtime endpoint in Settings → Play Together.",
                                style = TextStyle(fontSize = 11.5.sp, color = Color(0xFFC084FC).copy(alpha = 0.9f), lineHeight = 16.sp),
                            )
                        }
                    }
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
                        .border(1.dp, Color(0xFFF43F5E), RoundedCornerShape(12.dp))
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
                        brush = Brush.horizontalGradient(listOf(BrandPurpleVibrant, BrandPurpleLight)),
                    ),
                ) {
                    Icon(Icons.Outlined.Group, contentDescription = null, tint = BrandPurpleVibrant)
                    Spacer(Modifier.width(6.dp))
                    Text("Join by Code", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                }
            }
        }

        // Section Title: Active Rooms
        item("rooms_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active Public Rooms",
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                )
                Text(
                    text = "${activeRooms.size} live",
                    style = TextStyle(fontSize = 12.sp, color = DesignColors.Accent, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        // Active Rooms List
        if (activeRooms.isEmpty()) {
            item("empty_rooms") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No public rooms active right now.\nCreate one and invite your peers!",
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
                    )
                }
            }
        } else {
            items(activeRooms, key = { it.id }) { room ->
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
                    Text(
                        text = room.name,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Host: ${room.hostName} · Code: ${room.code}",
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
    state: PlayTogetherState.InRoom,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLeaveClick: () -> Unit,
    onAddSongClick: () -> Unit,
    onRemoveFromQueue: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copiedNotice by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Room Top Info Banner
        item("room_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.room.name,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
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
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Code", tint = DesignColors.Accent, modifier = Modifier.size(14.dp))
                        }
                        if (copiedNotice) {
                            Text("Copied!", fontSize = 11.sp, color = StatusGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Button(
                    onClick = onLeaveClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignColors.SettingsCard),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text("Leave", fontSize = 12.sp, color = DesignColors.InkSecondary)
                }
            }
        }

        if (state.errorMessage != null) {
            item("room_error_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3B1824))
                        .border(1.dp, Color(0xFFF43F5E), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = state.errorMessage,
                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFFDA4AF), fontWeight = FontWeight.Medium),
                    )
                }
            }
        }

        // Synchronized Player Hero Card
        item("player_hero") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DesignColors.SettingsCard),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Artwork / Logo Disc
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(BrandPurpleVibrant, BrandPurpleMedium, BrandPurpleDark),
                                ),
                            )
                            .border(1.5.dp, BrandPurpleVibrant.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.ymusic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Track Title & Artist
                    Text(
                        text = state.currentTrack?.title ?: "No Track Playing",
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.currentTrack?.artist ?: "Add a song to room queue",
                        style = TextStyle(fontSize = 13.sp, color = DesignColors.InkSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(10.dp))

                    // Realtime Synced Progress Slider
                    val trackDuration = state.currentTrack?.durationMs ?: 1L
                    val currentPos = state.positionMs.coerceIn(0L, trackDuration)
                    val sliderValue = (currentPos.toFloat() / trackDuration.toFloat()).coerceIn(0f, 1f)

                    Slider(
                        value = sliderValue,
                        onValueChange = { frac ->
                            val targetMs = (frac * trackDuration).toLong()
                            onSeek(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = DesignColors.Accent,
                            activeTrackColor = DesignColors.Accent,
                            inactiveTrackColor = DesignColors.Outline,
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatTime(currentPos), fontSize = 11.sp, color = DesignColors.InkSecondary)
                        Text(formatTime(trackDuration), fontSize = 11.sp, color = DesignColors.InkSecondary)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Playback Controls
                    val canControl = state.isHost || state.controlMode == ControlMode.COLLABORATIVE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = canControl,
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = if (canControl) DesignColors.Ink else DesignColors.Outline, modifier = Modifier.size(28.dp))
                        }

                        // Big Play/Pause Button
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(if (canControl) DesignColors.Accent else DesignColors.Outline)
                                .clickable(enabled = canControl) { onTogglePlayPause() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }

                        IconButton(
                            onClick = onNext,
                            enabled = canControl,
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = if (canControl) DesignColors.Ink else DesignColors.Outline, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Sync Status Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(StatusGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(StatusGreen))
                        Text(
                            text = "Synchronized (${state.syncOffsetMs} ms drift)",
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = StatusGreen),
                        )
                    }

                    if (!canControl) {
                        Text(
                            text = "Host controls playback in this room",
                            style = TextStyle(fontSize = 11.sp, color = DesignColors.InkSecondary),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        // Room Members List Section
        item("members_section") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Room Listeners (${state.members.size})",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.members, key = { it.id }) { member ->
                        MemberChip(member = member)
                    }
                }
            }
        }

        // Shared Queue Section
        item("queue_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Room Queue (${state.queue.size})",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DesignColors.Ink),
                )
                Button(
                    onClick = onAddSongClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Song", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (state.queue.isEmpty()) {
            item("empty_queue") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No songs in queue. Tap '+ Add Song' to queue tracks!",
                        fontSize = 12.sp,
                        color = DesignColors.InkSecondary,
                    )
                }
            }
        } else {
            items(state.queue, key = { it.id }) { track ->
                QueueTrackRow(
                    track = track,
                    canDelete = state.isHost || state.controlMode == ControlMode.COLLABORATIVE,
                    onDelete = { onRemoveFromQueue(track.id) },
                )
            }
        }
    }
}

@Composable
private fun MemberChip(member: RoomMember) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DesignColors.SettingsCard)
            .border(1.dp, if (member.isHost) BrandPurpleVibrant.copy(alpha = 0.5f) else DesignColors.Outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (member.isHost) BrandPurpleVibrant else DesignColors.Outline),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.name.take(1).uppercase(),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
        Text(
            text = member.name,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
        )
        if (member.isHost) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BrandPurpleVibrant.copy(alpha = 0.2f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text("HOST", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandPurpleVibrant)
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    track: PlayTogetherTrack,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DesignColors.SettingsCard)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BrandPurpleMedium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = BrandPurpleLight, modifier = Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} · Added by ${track.addedBy}",
                style = TextStyle(fontSize = 11.sp, color = DesignColors.InkSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = formatTime(track.durationMs),
            style = TextStyle(fontSize = 11.sp, color = DesignColors.InkSecondary),
        )

        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = DesignColors.InkSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, host: String, code: String, vis: RoomVisibility, mode: ControlMode) -> Unit,
) {
    var roomName by remember { mutableStateOf("PRVN Lounge") }
    var hostName by remember { mutableStateOf("DJ PRVN") }
    var roomCode by remember { mutableStateOf(kotlin.random.Random.nextInt(1000, 9999).toString()) }
    var visibility by remember { mutableStateOf(RoomVisibility.PUBLIC) }
    var controlMode by remember { mutableStateOf(ControlMode.COLLABORATIVE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Play Together Room", fontWeight = FontWeight.Bold, color = DesignColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignColors.Accent,
                        focusedLabelColor = DesignColors.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = hostName,
                    onValueChange = { hostName = it },
                    label = { Text("Your Nickname") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignColors.Accent,
                        focusedLabelColor = DesignColors.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) roomCode = it },
                    label = { Text("Custom 4-Digit Room Code") },
                    placeholder = { Text("e.g. 4821") },
                    singleLine = true,
                    supportingText = {
                        if (roomCode.length != 4) {
                            Text("Must be exactly 4 digits", color = Color(0xFFF43F5E), fontSize = 11.sp)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignColors.Accent,
                        focusedLabelColor = DesignColors.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Visibility selector
                Text("Room Visibility", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoomVisibility.entries.forEach { vis ->
                        val selected = vis == visibility
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DesignColors.Accent else DesignColors.SettingsCard)
                                .clickable { visibility = vis }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = when (vis) {
                                    RoomVisibility.PUBLIC -> "Public"
                                    RoomVisibility.INVITE_ONLY -> "Invite"
                                    RoomVisibility.PRIVATE -> "Private"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else DesignColors.InkSecondary,
                            )
                        }
                    }
                }

                // Control Mode selector
                Text("Playback Control", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlMode.entries.forEach { mode ->
                        val selected = mode == controlMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) DesignColors.Accent else DesignColors.SettingsCard)
                                .clickable { controlMode = mode }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = when (mode) {
                                    ControlMode.HOST_ONLY -> "Host Only"
                                    ControlMode.COLLABORATIVE -> "Anyone Controls"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else DesignColors.InkSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(roomName, hostName, roomCode, visibility, controlMode) },
                enabled = roomCode.length == 4 && roomName.isNotBlank() && hostName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
            ) {
                Text("Create Session", color = Color.White)
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
    onJoin: (code: String, userName: String) -> Unit,
) {
    var code by remember { mutableStateOf(initialCode) }
    var userName by remember { mutableStateOf("Listener") }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Play Together Room", fontWeight = FontWeight.Bold, color = DesignColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the 4-digit code provided by your host:",
                    fontSize = 13.sp,
                    color = DesignColors.InkSecondary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 4) code = it },
                        label = { Text("4-Digit Code") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DesignColors.Accent,
                            focusedLabelColor = DesignColors.Accent,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    OutlinedButton(
                        onClick = {
                            val clip = clipboard.getText()?.text?.trim().orEmpty()
                            if (clip.length == 4) code = clip
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Text("Paste", fontSize = 12.sp, color = DesignColors.Accent)
                    }
                }

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Your Nickname") },
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
                onClick = { if (code.isNotBlank()) onJoin(code, userName) },
                enabled = code.length == 4,
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
            ) {
                Text("Join Room", color = Color.White)
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
private fun AddSongToQueueDialog(
    onDismiss: () -> Unit,
    onAdd: (PlayTogetherTrack) -> Unit,
) {
    val presets = remember {
        listOf(
            PlayTogetherTrack("p1", "Starfall Melody", "Starlight Synthetics", durationMs = 210000L),
            PlayTogetherTrack("p2", "Cybernetic Rain", "PRVN Soundwave", durationMs = 185000L),
            PlayTogetherTrack("p3", "Neon Dreams 2026", "Aether Wave", durationMs = 240000L),
            PlayTogetherTrack("p4", "Midnight Drive", "Lofi Highways", durationMs = 195000L),
        )
    }

    var customTitle by remember { mutableStateOf("") }
    var customArtist by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Song to Room Queue", fontWeight = FontWeight.Bold, color = DesignColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select from recommended session tracks:", fontSize = 12.sp, color = DesignColors.InkSecondary)

                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DesignColors.Surface)
                            .clickable { onAdd(preset) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)
                            Text(preset.artist, fontSize = 10.sp, color = DesignColors.InkSecondary)
                        }
                        Icon(Icons.Default.Add, contentDescription = null, tint = DesignColors.Accent, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Or enter a custom track:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesignColors.Ink)

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Track Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DesignColors.Accent,
                        focusedLabelColor = DesignColors.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = customArtist,
                    onValueChange = { customArtist = it },
                    label = { Text("Artist") },
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
                    if (customTitle.isNotBlank()) {
                        onAdd(
                            PlayTogetherTrack(
                                id = "custom_${customTitle.hashCode()}",
                                title = customTitle,
                                artist = customArtist.ifBlank { "Unknown Artist" },
                            ),
                        )
                    }
                },
                enabled = customTitle.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DesignColors.Accent),
            ) {
                Text("Add", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = DesignColors.InkSecondary)
            }
        },
        containerColor = DesignColors.SettingsCard,
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
