package com.abhijit.shutupNstudy.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import com.abhijit.shutupNstudy.ActiveSession
import com.abhijit.shutupNstudy.CreateSetup
import com.abhijit.shutupNstudy.Home
import com.abhijit.shutupNstudy.JoinGate
import com.abhijit.shutupNstudy.SoloActiveSession
import com.abhijit.shutupNstudy.audio.AudioSynth
import com.abhijit.shutupNstudy.data.FirebaseSync
import com.abhijit.shutupNstudy.service.TimerService
import com.abhijit.shutupNstudy.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = NeuBg,
    cornerRadius: Dp = 12.dp,
    shadowOffset: Dp = 3.dp,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .neuFlat(
                cornerRadius = cornerRadius,
                shadowOffset = shadowOffset,
                blurRadius = shadowOffset * 2,
                backgroundColor = backgroundColor
            )
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

@Composable
fun HomeScreen(
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputRoomId by remember { mutableStateOf("") }
    var cachedName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        cachedName = FirebaseSync.getCachedUsername(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeuBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo Icon and Brand
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .neuFlat(cornerRadius = 24.dp)
        ) {
            Text("📚", fontSize = 32.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Shutup & Study",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = NeuTextPrimary
        )
        Text(
            text = "SYNCHRONIZED STUDY ROOMS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NeuTextSecondary,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 1. Solo Study Timer Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neuFlat(cornerRadius = 20.dp)
                .padding(20.dp)
        ) {
            Text(
                text = "⏱️ Solo Study Session",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = NeuTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Study alone with a simple, distraction-free offline Pomodoro timer. No internet or database connection required.",
                fontSize = 12.sp,
                color = NeuTextSecondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            NeuButton(
                onClick = { onNavigate(SoloActiveSession) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Solo Timer", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Group Study Session Card (Merged)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neuFlat(cornerRadius = 20.dp)
                .padding(20.dp)
        ) {
            Text(
                text = "👥 Group Study Session",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = NeuTextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Study together with friends in a synchronized room. Enter a room code to join, or create a new room session.",
                fontSize = 12.sp,
                color = NeuTextSecondary,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            NeuTextField(
                value = inputRoomId,
                onValueChange = { inputRoomId = it },
                placeholder = "Enter Room Code",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeuButton(
                    onClick = {
                        if (inputRoomId.trim().isNotEmpty()) {
                            val cleanCode = extractRoomCode(inputRoomId).uppercase(Locale.getDefault())
                            FirebaseSync.checkRoomExists(
                                cleanCode,
                                onSuccess = { exists, room ->
                                    if (exists && room != null) {
                                        if (cachedName.isNotEmpty()) {
                                            onNavigate(ActiveSession(cleanCode, cachedName, room.leaderId == FirebaseSync.getOrGenerateUserId(context)))
                                        } else {
                                            onNavigate(JoinGate(cleanCode))
                                        }
                                    } else {
                                        Toast.makeText(context, "Study room not found. Check the code.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onFailure = {
                                    Toast.makeText(context, "Failed to connect to database.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Please enter a room code first.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Join Room", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                }

                NeuButton(
                    onClick = { onNavigate(CreateSetup) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create New Room", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun JoinGateScreen(
    roomId: String,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeuBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neuFlat(cornerRadius = 24.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("👤 Identify Yourself", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NeuTextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "You are joining Room: $roomId. Please set a display name.",
                fontSize = 12.sp,
                color = NeuTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            NeuTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = "e.g. Abhijit",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            NeuButton(
                onClick = {
                    if (nameInput.trim().isNotEmpty()) {
                        FirebaseSync.cacheUsername(context, nameInput)
                        scope.launch {
                            FirebaseSync.checkRoomExists(
                                roomId,
                                onSuccess = { exists, room ->
                                    if (exists && room != null) {
                                        onNavigate(ActiveSession(roomId, nameInput.trim(), room.leaderId == FirebaseSync.getOrGenerateUserId(context)))
                                    } else {
                                        Toast.makeText(context, "Room has been closed.", Toast.LENGTH_SHORT).show()
                                        onNavigate(Home)
                                    }
                                },
                                onFailure = {
                                    Toast.makeText(context, "Failed to connect to database.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = PlayBtnBg
            ) {
                Text("Join Study Room ✨", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Back to Dashboard",
                fontSize = 13.sp,
                color = NeuTextSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable { onNavigate(Home) }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun CreateSetupScreen(
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var leaderName by remember { mutableStateOf("") }
    var focusMin by remember { mutableStateOf("25") }
    var breakMin by remember { mutableStateOf("5") }
    var longBreakMin by remember { mutableStateOf("15") }
    var intervalCount by remember { mutableStateOf("4") }

    LaunchedEffect(Unit) {
        leaderName = FirebaseSync.getCachedUsername(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeuBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neuFlat(cornerRadius = 24.dp)
                .padding(28.dp)
        ) {
            Text(
                text = "🛡️ Study Setup",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = NeuTextPrimary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Configure your group's pomodoro.",
                fontSize = 12.sp,
                color = NeuTextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text("YOUR DISPLAY NAME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            NeuTextField(
                value = leaderName,
                onValueChange = { leaderName = it },
                placeholder = "Leader Name",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("FOCUS (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    NeuTextField(
                        value = focusMin,
                        onValueChange = { focusMin = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("BREAK (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    NeuTextField(
                        value = breakMin,
                        onValueChange = { breakMin = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("LONG BREAK (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    NeuTextField(
                        value = longBreakMin,
                        onValueChange = { longBreakMin = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("INTERVAL COUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    NeuTextField(
                        value = intervalCount,
                        onValueChange = { intervalCount = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            NeuButton(
                onClick = {
                    if (leaderName.trim().isNotEmpty() &&
                        focusMin.toLongOrNull() != null &&
                        breakMin.toLongOrNull() != null &&
                        longBreakMin.toLongOrNull() != null &&
                        intervalCount.toLongOrNull() != null
                    ) {
                        FirebaseSync.cacheUsername(context, leaderName)
                        FirebaseSync.createRoom(
                            context = context,
                            username = leaderName,
                            focusMin = focusMin.toLong(),
                            shortBreakMin = breakMin.toLong(),
                            longBreakMin = longBreakMin.toLong(),
                            intervals = intervalCount.toLong(),
                            onSuccess = { newRoomId ->
                                onNavigate(ActiveSession(newRoomId, leaderName.trim(), true))
                            },
                            onFailure = {
                                Toast.makeText(context, "Failed to create study room.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = PlayBtnBg
            ) {
                Text("Start & Create Room", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Back to Dashboard",
                fontSize = 13.sp,
                color = NeuTextSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onNavigate(Home) }
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun ActiveSessionScreen(
    roomId: String,
    userName: String,
    isLeader: Boolean,
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userId = remember { FirebaseSync.getOrGenerateUserId(context) }
    val timerState by TimerService.timerState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showSettings by remember { mutableStateOf(false) }

    // Settings inputs
    var focusInput by remember { mutableStateOf("25") }
    var breakInput by remember { mutableStateOf("5") }
    var longBreakInput by remember { mutableStateOf("15") }
    var intervalsInput by remember { mutableStateOf("4") }

    // Start service
    LaunchedEffect(roomId) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra("ROOM_ID", roomId)
            putExtra("USER_ID", userId)
            putExtra("USER_NAME", userName)
            putExtra("IS_LEADER", isLeader)
        }
        context.startService(intent)
    }

    var hasConnected by remember { mutableStateOf(false) }

    // Redirect home if room finishes/exits in service
    LaunchedEffect(timerState) {
        val state = timerState
        if (state != null) {
            hasConnected = true
            if (showSettings.not()) {
                focusInput = (state.template.focusTime / 60).toString()
                breakInput = (state.template.shortBreakTime / 60).toString()
                longBreakInput = (state.template.longBreakTime / 60).toString()
                intervalsInput = state.template.longBreakInterval.toString()
            }
        } else if (hasConnected) {
            onNavigate(Home)
        }
    }

    if (timerState == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(NeuBg)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NeuTextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to study room...", color = NeuTextSecondary)
            }
        }
        return
    }

    val state = timerState!!

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(NeuBg)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Room header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Room: $roomId",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeuTextPrimary
                )
                Text(
                    text = "Interval: ${state.currentInterval}/${state.template.longBreakInterval}",
                    fontSize = 12.sp,
                    color = NeuTextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Copy Link Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .neuFlat(cornerRadius = 12.dp, shadowOffset = 2.dp)
                        .clickable {
                            val link = "https://shutupnstudy-1734a.firebaseapp.com/?room=$roomId"
                            clipboardManager.setText(AnnotatedString(link))
                            Toast.makeText(context, "Invite link copied!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Text("🔗", fontSize = 16.sp)
                }

                // Leader Settings Button
                if (isLeader) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .neuFlat(cornerRadius = 12.dp, shadowOffset = 2.dp)
                            .clickable { showSettings = true }
                    ) {
                        Text("⚙️", fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer Dial
        TimerDial(
            secondsRemaining = state.secondsRemaining,
            totalDuration = state.totalDuration,
            phase = state.currentPhase,
            status = state.status,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Leader Control Panel
        if (isLeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .neuFlat(cornerRadius = 32.dp, shadowOffset = 5.dp, blurRadius = 8.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .clickable {
                            val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESET }
                            context.startService(intent)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = NeuTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play / Pause Button
                val isRunning = state.status == "running"
                if (isRunning) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .neuPressed(cornerRadius = 45.dp, backgroundColor = PauseBtnBg)
                            .clip(RoundedCornerShape(45.dp))
                            .clickable {
                                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_PAUSE }
                                context.startService(intent)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .neuFlat(cornerRadius = 45.dp, shadowOffset = 6.dp, blurRadius = 10.dp, backgroundColor = PlayBtnBg)
                            .clip(RoundedCornerShape(45.dp))
                            .clickable {
                                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_PLAY }
                                context.startService(intent)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF00966C),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Skip Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .neuFlat(cornerRadius = 32.dp, shadowOffset = 5.dp, blurRadius = 8.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .clickable {
                            val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_SKIP }
                            context.startService(intent)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        tint = NeuTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            // Participant Info Mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neuFlat(cornerRadius = 16.dp)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("🛡️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Synchronized to Leader's Clock",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Participants Header (matching web style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("", fontSize = 16.sp, color = NeuTextSecondary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Study Buddies (${state.participants.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NeuTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Participants list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.participants) { p ->
                val isBuddyLeader = p.role == "leader"
                val isBuddySelf = p.id == userId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neuFlat(cornerRadius = 16.dp, shadowOffset = 3.dp, blurRadius = 6.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar circle with pressed shadow
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .neuPressed(cornerRadius = 19.dp)
                        ) {
                            if (isBuddyLeader) {
                                Canvas(modifier = Modifier.size(20.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val path = Path().apply {
                                        moveTo(w * 0.5f, h * 0.12f)
                                        lineTo(w * 0.85f, h * 0.22f)
                                        lineTo(w * 0.85f, h * 0.58f)
                                        quadraticTo(w * 0.85f, h * 0.82f, w * 0.5f, h * 0.92f)
                                        quadraticTo(w * 0.15f, h * 0.82f, w * 0.15f, h * 0.58f)
                                        lineTo(w * 0.15f, h * 0.22f)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(0xFFD97706), // Amber outline
                                        style = Stroke(
                                            width = 1.75f.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            } else {
                                Canvas(modifier = Modifier.size(16.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawCircle(
                                        color = NeuTextSecondary,
                                        radius = w * 0.22f,
                                        center = Offset(w * 0.5f, h * 0.35f),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawArc(
                                        color = NeuTextSecondary,
                                        startAngle = 180f,
                                        sweepAngle = 180f,
                                        useCenter = false,
                                        topLeft = Offset(w * 0.18f, h * 0.62f),
                                        size = Size(w * 0.64f, h * 0.5f),
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = p.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeuTextPrimary
                                )
                                if (isBuddySelf) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "(You)",
                                        fontSize = 10.sp,
                                        color = NeuTextMuted
                                    )
                                }
                            }
                            Text(
                                    text = if (isBuddyLeader) "Leader" else "Studying",
                                    fontSize = 11.sp,
                                    color = NeuTextMuted
                            )
                        }
                    }

                    // Pulsing status dot on the right
                    val phaseColor = when (state.currentPhase) {
                        "shortBreak" -> ColorBreak
                        "longBreak" -> ColorLongBreak
                        else -> ColorFocus
                    }

                    if (state.status == "running") {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1.0f,
                            targetValue = 2.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "scale"
                        )
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "alpha"
                        )

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = phaseColor,
                                    radius = 4.5f.dp.toPx() * pulseScale,
                                    alpha = pulseAlpha
                                )
                                drawCircle(
                                    color = phaseColor,
                                    radius = 4.5f.dp.toPx()
                                )
                            }
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                            Canvas(modifier = Modifier.size(9.dp)) {
                                drawCircle(color = phaseColor, radius = 4.5f.dp.toPx())
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Leave Session Button
        NeuButton(
            onClick = {
                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_STOP }
                context.stopService(intent)
                if (isLeader) {
                    FirebaseSync.deleteRoom(roomId, userId) {
                        onNavigate(Home)
                    }
                } else {
                    FirebaseSync.leaveRoom(roomId, userId)
                    onNavigate(Home)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLeader) "End & Close Session" else "Leave Study Room", color = NeuTextSecondary, fontWeight = FontWeight.Bold)
        }
    }

    // Leader Settings Dialog
    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeuBg, shape = RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "⚙️ Room Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuTextPrimary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("FOCUS (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    NeuTextField(
                        value = focusInput,
                        onValueChange = { focusInput = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("BREAK (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    NeuTextField(
                        value = breakInput,
                        onValueChange = { breakInput = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("LONG BREAK (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    NeuTextField(
                        value = longBreakInput,
                        onValueChange = { longBreakInput = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("INTERVALS COUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    NeuTextField(
                        value = intervalsInput,
                        onValueChange = { intervalsInput = it },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    NeuButton(
                        onClick = {
                            val fSecs = focusInput.toLongOrNull()?.let { it * 60 } ?: state.template.focusTime
                            val bSecs = breakInput.toLongOrNull()?.let { it * 60 } ?: state.template.shortBreakTime
                            val lbSecs = longBreakInput.toLongOrNull()?.let { it * 60 } ?: state.template.longBreakTime
                            val intCount = intervalsInput.toLongOrNull() ?: state.template.longBreakInterval

                            val duration = when (state.currentPhase) {
                                "shortBreak" -> bSecs
                                "longBreak" -> lbSecs
                                else -> fSecs
                            }

                            val overrideMap = mapOf(
                                "template.focusTime" to fSecs,
                                "template.shortBreakTime" to bSecs,
                                "template.longBreakTime" to lbSecs,
                                "template.longBreakInterval" to intCount,
                                "state.lastAction" to "settings"
                            )

                            FirebaseSync.updateTimerState(roomId, "idle", duration, overrideMap)
                            showSettings = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = PlayBtnBg
                    ) {
                        Text("Save & Apply Settings", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    NeuButton(
                        onClick = { showSettings = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Overlay for when the entire study session cycle is completed
        if (state.cycleCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCCF0F0F3)) // Semi-transparent overlay matching NeuBg
                    .padding(24.dp)
                    .clickable(enabled = false) {}, // Consume clicks so they don't pass through to buttons underneath
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neuFlat(cornerRadius = 24.dp)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 48.sp
                    )

                    Text(
                        text = "Pomodoro Cycle Complete!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (isLeader) {
                            "Outstanding work! You successfully finished the entire study cycle. Would you like to start a new cycle or close this room?"
                        } else {
                            "Fantastic effort! You completed the full cycle. Waiting for the room leader (${state.roomId}) to choose the next step..."
                        },
                        fontSize = 14.sp,
                        color = NeuTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    if (isLeader) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            NeuButton(
                                onClick = {
                                    val intent = Intent(context, TimerService::class.java).apply {
                                        action = TimerService.ACTION_CONTINUE
                                    }
                                    context.startService(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = PlayBtnBg
                            ) {
                                Text("Start New Cycle", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                            }

                            NeuButton(
                                onClick = {
                                    val intent = Intent(context, TimerService::class.java).apply {
                                        action = TimerService.ACTION_DISCARD
                                    }
                                    context.startService(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = Color(0xFFFFEBEB)
                            ) {
                                Text("Discard & Close Room", color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NeuTextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Awaiting leader decision...",
                                fontSize = 12.sp,
                                color = NeuTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun SoloActiveSessionScreen(
    onNavigate: (androidx.navigation3.runtime.NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timerState by TimerService.timerState.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var focusInput by remember { mutableStateOf("25") }
    var breakInput by remember { mutableStateOf("5") }
    var longBreakInput by remember { mutableStateOf("15") }
    var intervalsInput by remember { mutableStateOf("4") }

    // Start service with roomId = "local"
    LaunchedEffect(Unit) {
        val intent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_START
            putExtra("ROOM_ID", "local")
            putExtra("USER_ID", "local")
            putExtra("USER_NAME", "Solo")
            putExtra("IS_LEADER", true)
        }
        context.startService(intent)
    }

    var hasConnected by remember { mutableStateOf(false) }

    LaunchedEffect(timerState) {
        val state = timerState
        if (state != null) {
            hasConnected = true
            if (showSettings.not()) {
                focusInput = (state.template.focusTime / 60).toString()
                breakInput = (state.template.shortBreakTime / 60).toString()
                longBreakInput = (state.template.longBreakTime / 60).toString()
                intervalsInput = state.template.longBreakInterval.toString()
            }
        } else if (hasConnected) {
            onNavigate(Home)
        }
    }

    if (timerState == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(NeuBg)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = NeuTextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Initializing Solo Timer...", color = NeuTextSecondary)
            }
        }
        return
    }

    val state = timerState!!

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(NeuBg)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Solo Study Timer",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuTextPrimary
                    )
                    Text(
                        text = "Interval: ${state.currentInterval}/${state.template.longBreakInterval}",
                        fontSize = 12.sp,
                        color = NeuTextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Settings Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .neuFlat(cornerRadius = 12.dp, shadowOffset = 2.dp)
                            .clickable { showSettings = true }
                    ) {
                        Text("⚙️", fontSize = 16.sp)
                    }

                    // Back to Dashboard Button (exit solo timer)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .neuFlat(cornerRadius = 12.dp, shadowOffset = 2.dp)
                            .clickable {
                                val intent = Intent(context, TimerService::class.java).apply {
                                    action = TimerService.ACTION_STOP
                                }
                                context.startService(intent)
                                onNavigate(Home)
                            }
                    ) {
                        Text("🚪", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Timer Dial
            TimerDial(
                secondsRemaining = state.secondsRemaining,
                totalDuration = state.totalDuration,
                phase = state.currentPhase,
                status = state.status,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .neuFlat(cornerRadius = 32.dp, shadowOffset = 5.dp, blurRadius = 8.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .clickable {
                            val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESET }
                            context.startService(intent)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = NeuTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play / Pause Button
                val isRunning = state.status == "running"
                if (isRunning) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .neuPressed(cornerRadius = 45.dp, backgroundColor = PauseBtnBg)
                            .clip(RoundedCornerShape(45.dp))
                            .clickable {
                                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_PAUSE }
                                context.startService(intent)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .neuFlat(cornerRadius = 45.dp, shadowOffset = 6.dp, blurRadius = 10.dp)
                            .clip(RoundedCornerShape(45.dp))
                            .clickable {
                                val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_PLAY }
                                context.startService(intent)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Skip Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .neuFlat(cornerRadius = 32.dp, shadowOffset = 5.dp, blurRadius = 8.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .clickable {
                            val intent = Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_SKIP }
                            context.startService(intent)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip",
                        tint = NeuTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Overlay for when the entire study session cycle is completed
        if (state.cycleCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCCF0F0F3))
                    .padding(24.dp)
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neuFlat(cornerRadius = 24.dp)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 48.sp
                    )

                    Text(
                        text = "Study Cycle Complete!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeuTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Outstanding work! You successfully finished the entire Pomodoro study cycle. Would you like to start a new cycle or close the session?",
                        fontSize = 14.sp,
                        color = NeuTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        NeuButton(
                            onClick = {
                                val intent = Intent(context, TimerService::class.java).apply {
                                    action = TimerService.ACTION_CONTINUE
                                }
                                context.startService(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = PlayBtnBg
                        ) {
                            Text("Start New Cycle", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                        }

                        NeuButton(
                            onClick = {
                                val intent = Intent(context, TimerService::class.java).apply {
                                    action = TimerService.ACTION_DISCARD
                                }
                                context.startService(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFFFFEBEB)
                        ) {
                            Text("Discard & Exit", color = Color(0xFF991B1B), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Solo Settings Dialog
        if (showSettings) {
            Dialog(onDismissRequest = { showSettings = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NeuBg, shape = RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "⚙️ Timer Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuTextPrimary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("FOCUS (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        NeuTextField(
                            value = focusInput,
                            onValueChange = { focusInput = it },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("BREAK (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        NeuTextField(
                            value = breakInput,
                            onValueChange = { breakInput = it },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("LONG BREAK (MIN)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        NeuTextField(
                            value = longBreakInput,
                            onValueChange = { longBreakInput = it },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("INTERVALS COUNT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeuTextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        NeuTextField(
                            value = intervalsInput,
                            onValueChange = { intervalsInput = it },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = NeuTextPrimary, fontSize = 13.sp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        NeuButton(
                            onClick = {
                                val fSecs = focusInput.toLongOrNull()?.let { it * 60 } ?: state.template.focusTime
                                val bSecs = breakInput.toLongOrNull()?.let { it * 60 } ?: state.template.shortBreakTime
                                val lbSecs = longBreakInput.toLongOrNull()?.let { it * 60 } ?: state.template.longBreakTime
                                val intCount = intervalsInput.toLongOrNull() ?: state.template.longBreakInterval

                                val intent = Intent(context, TimerService::class.java).apply {
                                    action = TimerService.ACTION_UPDATE_SETTINGS
                                    putExtra("FOCUS_TIME", fSecs)
                                    putExtra("SHORT_BREAK_TIME", bSecs)
                                    putExtra("LONG_BREAK_TIME", lbSecs)
                                    putExtra("LONG_BREAK_INTERVAL", intCount)
                                }
                                context.startService(intent)
                                showSettings = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = PlayBtnBg
                        ) {
                            Text("Save & Apply Settings", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        NeuButton(
                            onClick = { showSettings = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = NeuTextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimerDial(
    secondsRemaining: Long,
    totalDuration: Long,
    phase: String,
    status: String,
    modifier: Modifier = Modifier
) {
    val progress = if (totalDuration > 0) secondsRemaining.toFloat() / totalDuration.toFloat() else 1f
    val phaseColor = when (phase) {
        "shortBreak" -> ColorBreak
        "longBreak" -> ColorLongBreak
        else -> ColorFocus
    }

    // Smoothly animate the progress to make the sweep animation completely fluid
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (secondsRemaining == totalDuration) {
            snap() // Snap immediately on timer reset
        } else {
            tween(durationMillis = 1000, easing = LinearEasing)
        },
        label = "TimerProgress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .neuFlat(cornerRadius = 120.dp, shadowOffset = 6.dp, blurRadius = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 8.dp.toPx()
            val trackWidth = 14.dp.toPx()
            val progressWidth = 8.dp.toPx()

            // 1. Draw the carved groove base track (uniform light-gray slot)
            drawCircle(
                color = Color(0xFFE2E2E6),
                center = center,
                radius = radius,
                style = Stroke(width = trackWidth)
            )

            // 2. Draw a consistent, soft dark inner shadow along the inner edge of the groove (all the way around)
            drawCircle(
                color = Color(0x18000000), // Very soft, organic dark shadow of the inner stone wall
                center = center,
                radius = radius - trackWidth / 2,
                style = Stroke(width = 1.5f.dp.toPx())
            )

            // 3. Draw a consistent, soft white highlight along the outer edge of the groove (all the way around)
            drawCircle(
                color = Color(0xB2FFFFFF), // Clean white highlight of the outer stone cut
                center = center,
                radius = radius + trackWidth / 2,
                style = Stroke(width = 1.5f.dp.toPx())
            )

            // 4. Draw the red/colored progress arc inside the groove (centered at the same radius)
            drawArc(
                color = phaseColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = progressWidth, cap = StrokeCap.Round)
            )
        }

        // Timer Text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatTime(secondsRemaining),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    color = NeuTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (phase) {
                    "shortBreak" -> "Short Break"
                    "longBreak" -> "Long Break"
                    else -> "Focus Phase"
                }.uppercase(),
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NeuTextSecondary,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}

private fun extractRoomCode(input: String): String {
    return if (input.contains("?room=")) {
        input.substringAfter("?room=").substringBefore("&")
    } else {
        input
    }
}

@Composable
fun ResetIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(20.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 2.5f.dp.toPx()
        val centerX = w * 0.5f
        val centerY = h * 0.5f
        val radius = w * 0.33f

        // Draw circular arc from -45 to -315 degrees counter-clockwise (so gap is at top-right)
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = -270f,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Arrowhead at the start of the arc (which is at -45 degrees)
        val angleRad = Math.toRadians(-45.0)
        val tipX = centerX + radius * Math.cos(angleRad).toFloat()
        val tipY = centerY + radius * Math.sin(angleRad).toFloat()

        val path = Path().apply {
            moveTo(tipX - w * 0.12f, tipY - h * 0.02f)
            lineTo(tipX, tipY)
            lineTo(tipX + w * 0.02f, tipY + h * 0.12f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun PlayIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(28.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 3.5f.dp.toPx()

        // Optically centered triangle pointing right (slightly shifted right)
        val path = Path().apply {
            moveTo(w * 0.41f, h * 0.28f)
            lineTo(w * 0.41f, h * 0.72f)
            lineTo(w * 0.77f, h * 0.5f)
            close()
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun PauseIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(28.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 3.5f.dp.toPx()

        // Two vertical bars
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.41f, h * 0.3f),
            end = androidx.compose.ui.geometry.Offset(w * 0.41f, h * 0.7f),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.59f, h * 0.3f),
            end = androidx.compose.ui.geometry.Offset(w * 0.59f, h * 0.7f),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SkipIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().padding(20.dp)) {
        val w = size.width
        val h = size.height
        val strokePx = 2.5f.dp.toPx()

        // Triangle pointing right
        val path = Path().apply {
            moveTo(w * 0.32f, h * 0.32f)
            lineTo(w * 0.32f, h * 0.68f)
            lineTo(w * 0.62f, h * 0.5f)
            close()
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Vertical bar
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.32f),
            end = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.68f),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle(color = NeuTextPrimary, fontSize = 14.sp)
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .neuPressed(cornerRadius = 12.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(text = placeholder, color = NeuTextMuted, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
