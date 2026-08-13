package com.abhijit.shutupNstudy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import com.nothing.ketchum.GlyphManager
import com.nothing.ketchum.GlyphFrame
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.Common
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import com.abhijit.shutupNstudy.MainActivity
import com.abhijit.shutupNstudy.R
import com.abhijit.shutupNstudy.audio.AudioSynth
import com.abhijit.shutupNstudy.data.FirebaseSync
import com.abhijit.shutupNstudy.data.Participant
import com.abhijit.shutupNstudy.data.RoomTemplate
import com.abhijit.shutupNstudy.data.StudyRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TimerService : Service() {
    companion object {
        private const val TAG = "TimerService"
        private const val CHANNEL_ID = "shutup_study_timer_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESET = "ACTION_RESET"
        const val ACTION_SKIP = "ACTION_SKIP"
        const val ACTION_CONTINUE = "ACTION_CONTINUE"
        const val ACTION_DISCARD = "ACTION_DISCARD"
        const val ACTION_FOREGROUND = "ACTION_FOREGROUND"
        const val ACTION_BACKGROUND = "ACTION_BACKGROUND"
        const val ACTION_UPDATE_SETTINGS = "ACTION_UPDATE_SETTINGS"

        data class ServiceTimerState(
            val roomId: String = "",
            val isLeader: Boolean = false,
            val status: String = "idle",
            val currentPhase: String = "focus",
            val secondsRemaining: Long = 1500L,
            val totalDuration: Long = 1500L,
            val currentInterval: Long = 1L,
            val cycleCompleted: Boolean = false,
            val participants: List<Participant> = emptyList(),
            val template: RoomTemplate = RoomTemplate()
        )

        private val _timerState = MutableStateFlow<ServiceTimerState?>(null)
        val timerState = _timerState.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var roomJob: Job? = null
    private var participantsJob: Job? = null
    private var countdownJob: Job? = null
    private var heartbeatJob: Job? = null
    private var leaderHeartbeatJob: Job? = null

    private var roomId = ""
    private var userId = ""
    private var userName = ""
    private var isLeader = false

    private var prevPhase: String? = null
    private var autoStartOnJoin = false
    private var isAppInForeground = true

    private var glyphManager: GlyphManager? = null
    private var isGlyphSessionOpen = false

    override fun onCreate() {
        super.onCreate()
        FirebaseSync.initialize(this)
        createNotificationChannel()
        initGlyph()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")

        if (action != ACTION_START && roomId.isEmpty()) {
            Log.d(TAG, "No active study session. Stopping service for action: $action")
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START -> {
                roomId = intent.getStringExtra("ROOM_ID") ?: ""
                userId = intent.getStringExtra("USER_ID") ?: ""
                userName = intent.getStringExtra("USER_NAME") ?: ""
                isLeader = intent.getBooleanExtra("IS_LEADER", false)
                autoStartOnJoin = true
                isAppInForeground = true

                if (roomId == "local") {
                    isLeader = true // Solo timer user is always the leader
                    val defaultTemplate = RoomTemplate(
                        focusTime = 1500L,
                        shortBreakTime = 300L,
                        longBreakTime = 900L,
                        longBreakInterval = 4L
                    )
                    _timerState.value = ServiceTimerState(
                        roomId = "local",
                        isLeader = true,
                        status = "running",
                        currentPhase = "focus",
                        secondsRemaining = 1500L,
                        totalDuration = 1500L,
                        currentInterval = 1L,
                        template = defaultTemplate
                    )
                    manageCountdown("running", 1500L)
                } else {
                    observeFirestoreRoom()
                    startHeartbeat()
                }
            }
            ACTION_STOP -> {
                stopTimerService()
            }
            ACTION_PLAY -> {
                handlePlay()
            }
            ACTION_PAUSE -> {
                handlePause()
            }
            ACTION_RESET -> {
                handleReset()
            }
            ACTION_SKIP -> {
                handleSkip()
            }
            ACTION_CONTINUE -> {
                handleContinue()
            }
            ACTION_DISCARD -> {
                handleDiscard()
            }
            ACTION_FOREGROUND -> {
                isAppInForeground = true
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
             ACTION_BACKGROUND -> {
                isAppInForeground = false
                val state = _timerState.value
                if (state != null) {
                    val phaseLabel = when (state.currentPhase) {
                        "shortBreak" -> "Short Break"
                        "longBreak" -> "Long Break"
                        else -> "Focus"
                    }
                    val statusText = if (state.status == "running") {
                        "$phaseLabel • ${formatTime(state.secondsRemaining)}"
                    } else {
                        "$phaseLabel • Paused"
                    }
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(
                            phaseLabel,
                            statusText,
                            state.totalDuration.toInt(),
                            state.secondsRemaining.toInt()
                        )
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification("Study Session Active", "")
                    )
                }
            }
            ACTION_UPDATE_SETTINGS -> {
                val focusSec = intent.getLongExtra("FOCUS_TIME", 1500L)
                val breakSec = intent.getLongExtra("SHORT_BREAK_TIME", 300L)
                val longBreakSec = intent.getLongExtra("LONG_BREAK_TIME", 900L)
                val intervals = intent.getLongExtra("LONG_BREAK_INTERVAL", 4L)

                val currentState = _timerState.value
                if (currentState != null && roomId == "local") {
                    val newTemplate = currentState.template.copy(
                        focusTime = focusSec,
                        shortBreakTime = breakSec,
                        longBreakTime = longBreakSec,
                        longBreakInterval = intervals
                    )
                    val nextState = currentState.copy(
                        status = "idle",
                        currentPhase = "focus",
                        secondsRemaining = focusSec,
                        totalDuration = focusSec,
                        currentInterval = 1L,
                        template = newTemplate,
                        cycleCompleted = false
                    )
                    _timerState.value = nextState
                    manageCountdown("idle", focusSec)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun observeFirestoreRoom() {
        roomJob?.cancel()
        participantsJob?.cancel()

        roomJob = serviceScope.launch {
            FirebaseSync.observeRoom(roomId).collect { room ->
                if (room == null) {
                    Log.d(TAG, "Room was deleted or doesn't exist. Stopping service.")
                    stopTimerService()
                    return@collect
                }
                updateStateFromRoom(room)
            }
        }

        participantsJob = serviceScope.launch {
            FirebaseSync.observeParticipants(roomId).collect { list ->
                val currentState = _timerState.value
                if (currentState != null) {
                    _timerState.value = currentState.copy(participants = list)
                }
            }
        }
    }

    private fun updateStateFromRoom(room: StudyRoom) {
        val state = room.state
        val template = room.template

        // Auto-start on join if leader and room is currently idle
        if (isLeader && autoStartOnJoin && state.status == "idle") {
            autoStartOnJoin = false
            FirebaseSync.updateTimerState(roomId, "running", state.timerSecondsRemaining, mapOf("state.lastAction" to "start"))
            return
        }
        autoStartOnJoin = false

        // Determine total duration
        val totalDuration = when (state.currentPhase) {
            "shortBreak" -> template.shortBreakTime
            "longBreak" -> template.longBreakTime
            else -> template.focusTime
        }

        // Calculate actual seconds remaining based on Firestore sync timestamp
        var calculatedRemaining = state.timerSecondsRemaining
        val now = System.currentTimeMillis()
        if (state.status == "running" && state.updatedAt != null) {
            val elapsedMs = now - state.updatedAt.toDate().time
            val elapsedSecs = Math.max(0L, elapsedMs / 1000L)
            calculatedRemaining = Math.max(0L, state.timerSecondsRemaining - elapsedSecs)
        }

        val nextState = ServiceTimerState(
            roomId = roomId,
            isLeader = isLeader,
            status = state.status,
            currentPhase = state.currentPhase,
            secondsRemaining = calculatedRemaining,
            totalDuration = totalDuration,
            currentInterval = state.currentInterval,
            cycleCompleted = state.cycleCompleted,
            participants = _timerState.value?.participants ?: emptyList(),
            template = template
        )

        // Play chime sound if phase changes
        if (prevPhase != null && prevPhase != state.currentPhase) {
            AudioSynth.playSound(state.currentPhase)
        }
        prevPhase = state.currentPhase

        _timerState.value = nextState

        // Update countdown loop
        manageCountdown(state.status, calculatedRemaining)

        // Sync Glyph state immediately
        if (state.status == "running") {
            openGlyphSession()
            updateGlyphProgress(calculatedRemaining * 1000, totalDuration * 1000)
        } else {
            closeGlyphSession()
        }

        // Manage leader heartbeat
        manageLeaderHeartbeat(state.status)

        // Update notification
        val phaseLabel = when (state.currentPhase) {
            "shortBreak" -> "Short Break"
            "longBreak" -> "Long Break"
            else -> "Focus Phase"
        }
        val statusText = if (state.status == "running") {
            "$phaseLabel • ${formatTime(calculatedRemaining)}"
        } else {
            "Paused • ${formatTime(calculatedRemaining)}"
        }
        updateNotification(phaseLabel, statusText, totalDuration.toInt(), calculatedRemaining.toInt())
    }

    private fun manageCountdown(status: String, secondsRemaining: Long) {
        countdownJob?.cancel()
        if (status == "running") {
            openGlyphSession()
            countdownJob = serviceScope.launch {
                val durationMs = secondsRemaining * 1000
                val startTime = System.currentTimeMillis()
                val endTime = startTime + durationMs
                var lastLoggedSecond = secondsRemaining

                while (System.currentTimeMillis() < endTime) {
                    val now = System.currentTimeMillis()
                    val remainingMs = endTime - now
                    val remainingSeconds = (remainingMs + 999) / 1000

                    val currentState = _timerState.value
                    if (currentState != null) {
                        // Update Glyph progress smoothly at 100ms intervals
                        updateGlyphProgress(remainingMs, currentState.totalDuration * 1000)

                        // Update notification/state only when the integer second changes
                        if (remainingSeconds != lastLoggedSecond) {
                            lastLoggedSecond = remainingSeconds
                            _timerState.value = currentState.copy(secondsRemaining = remainingSeconds)
                            val phaseLabel = when (currentState.currentPhase) {
                                "shortBreak" -> "Short Break"
                                "longBreak" -> "Long Break"
                                else -> "Focus Phase"
                            }
                            updateNotification(phaseLabel, "$phaseLabel • ${formatTime(remainingSeconds)}", currentState.totalDuration.toInt(), remainingSeconds.toInt())
                        }
                    }

                    delay(100)
                }

                if (isLeader) {
                    handleTimerComplete()
                }
            }
        } else {
            closeGlyphSession()
        }
    }

    private fun manageLeaderHeartbeat(status: String) {
        leaderHeartbeatJob?.cancel()
        if (roomId == "local") return
        if (isLeader && status == "running") {
            leaderHeartbeatJob = serviceScope.launch {
                while (true) {
                    delay(15000)
                    val remaining = _timerState.value?.secondsRemaining ?: 0L
                    FirebaseSync.updateTimerState(roomId, "running", remaining)
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        if (roomId == "local") return
        FirebaseSync.joinRoom(roomId, userId, userName, isLeader)
        heartbeatJob = serviceScope.launch {
            while (true) {
                delay(45000)
                FirebaseSync.updateHeartbeat(roomId, userId)
            }
        }
    }

    private fun handlePlay() {
        if (!isLeader) return
        AudioSynth.playSound("click")
        val remaining = _timerState.value?.secondsRemaining ?: 1500L
        if (roomId == "local") {
            val currentState = _timerState.value ?: return
            val nextState = currentState.copy(status = "running")
            _timerState.value = nextState
            manageCountdown("running", nextState.secondsRemaining)
        } else {
            FirebaseSync.updateTimerState(roomId, "running", remaining, mapOf("state.lastAction" to "start"))
        }
    }

    private fun handlePause() {
        if (!isLeader) return
        AudioSynth.playSound("click")
        val remaining = _timerState.value?.secondsRemaining ?: 1500L
        if (roomId == "local") {
            val currentState = _timerState.value ?: return
            val nextState = currentState.copy(status = "paused")
            _timerState.value = nextState
            manageCountdown("paused", nextState.secondsRemaining)
        } else {
            FirebaseSync.updateTimerState(roomId, "paused", remaining, mapOf("state.lastAction" to "pause"))
        }
    }

    private fun handleReset() {
        if (!isLeader) return
        AudioSynth.playSound("click")
        val state = _timerState.value ?: return
        val duration = when (state.currentPhase) {
            "shortBreak" -> state.template.shortBreakTime
            "longBreak" -> state.template.longBreakTime
            else -> state.template.focusTime
        }
        if (roomId == "local") {
            val nextState = state.copy(status = "idle", secondsRemaining = duration)
            _timerState.value = nextState
            manageCountdown("idle", duration)
        } else {
            FirebaseSync.updateTimerState(roomId, "idle", duration, mapOf("state.lastAction" to "reset"))
        }
    }

    private fun handleSkip() {
        if (!isLeader) return
        AudioSynth.playSound("click")
        transitionPhase(isSkipped = true)
    }

    private fun handleContinue() {
        if (!isLeader) return
        AudioSynth.playSound("click")
        val state = _timerState.value ?: return
        val duration = state.template.focusTime
        if (roomId == "local") {
            val nextState = state.copy(
                status = "running",
                currentPhase = "focus",
                secondsRemaining = duration,
                currentInterval = 1L,
                cycleCompleted = false
            )
            _timerState.value = nextState
            manageCountdown("running", duration)
        } else {
            FirebaseSync.updateTimerState(roomId, "running", duration, mapOf(
                "state.currentPhase" to "focus",
                "state.currentInterval" to 1L,
                "state.cycleCompleted" to false,
                "state.lastAction" to "start"
            ))
        }
    }

    private fun handleDiscard() {
        if (!isLeader) return
        AudioSynth.playSound("click")
        if (roomId == "local") {
            stopTimerService()
        } else {
            FirebaseSync.deleteRoom(roomId, userId) {
                stopTimerService()
            }
        }
    }

    private fun handleTimerComplete() {
        val state = _timerState.value ?: return
        if (state.currentPhase == "longBreak") {
            if (roomId == "local") {
                val nextState = state.copy(status = "paused", cycleCompleted = true)
                _timerState.value = nextState
                manageCountdown("paused", 0)
            } else {
                FirebaseSync.updateTimerState(roomId, "paused", 0, mapOf(
                    "state.cycleCompleted" to true,
                    "state.lastAction" to "complete"
                ))
            }
        } else {
            transitionPhase(isSkipped = false)
        }
    }

    private fun transitionPhase(isSkipped: Boolean) {
        val state = _timerState.value ?: return
        var nextPhase = "focus"
        var nextSeconds = state.template.focusTime
        var nextInterval = state.currentInterval

        if (state.currentPhase == "focus") {
            if (state.currentInterval >= state.template.longBreakInterval) {
                nextPhase = "longBreak"
                nextSeconds = state.template.longBreakTime
                nextInterval = 1
            } else {
                nextPhase = "shortBreak"
                nextSeconds = state.template.shortBreakTime
                nextInterval = state.currentInterval + 1
            }
        } else {
            nextPhase = "focus"
            nextSeconds = state.template.focusTime
        }

        if (roomId == "local") {
            val nextState = state.copy(
                status = "running",
                currentPhase = nextPhase,
                secondsRemaining = nextSeconds,
                currentInterval = nextInterval
            )
            _timerState.value = nextState
            manageCountdown("running", nextSeconds)
        } else {
            FirebaseSync.updateTimerState(roomId, "running", nextSeconds, mapOf(
                "state.currentPhase" to nextPhase,
                "state.currentInterval" to nextInterval,
                "state.lastAction" to if (isSkipped) "skip" else "complete"
            ))
        }
    }

    private fun stopTimerService() {
        Log.d(TAG, "Stopping Timer Service")
        heartbeatJob?.cancel()
        roomJob?.cancel()
        participantsJob?.cancel()
        countdownJob?.cancel()
        leaderHeartbeatJob?.cancel()

        if (roomId.isNotEmpty()) {
            FirebaseSync.leaveRoom(roomId, userId)
            roomId = ""
        }
        _timerState.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Study Timer"
            val descriptionText = "Displays the synchronized Pomodoro study timer"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String, progressMax: Int = 0, progressCurrent: Int = 0): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_round)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        if (progressMax > 0) {
            builder.setProgress(progressMax, progressCurrent, false)
        }

        if (isLeader) {
            val status = _timerState.value?.status ?: "idle"
            if (status == "running") {
                val pauseIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }
                val pausePending = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePending)
            } else {
                val playIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PLAY }
                val playPending = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(android.R.drawable.ic_media_play, "Start", playPending)
            }

            val resetIntent = Intent(this, TimerService::class.java).apply { action = ACTION_RESET }
            val resetPending = PendingIntent.getService(this, 3, resetIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_menu_revert, "Reset", resetPending)
        }

        return builder.build()
    }

    private fun updateNotification(title: String, content: String, progressMax: Int = 0, progressCurrent: Int = 0) {
        if (!isAppInForeground) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content, progressMax, progressCurrent))
        }

        // Also trigger App Widget update explicitly!
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val component = ComponentName(this, com.abhijit.shutupNstudy.widget.TimerWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
        if (appWidgetIds.isNotEmpty()) {
            val widgetUpdateIntent = Intent(this, com.abhijit.shutupNstudy.widget.TimerWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            sendBroadcast(widgetUpdateIntent)
        }
    }

    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initGlyph() {
        try {
            glyphManager = GlyphManager.getInstance(applicationContext)
            glyphManager?.init(object : GlyphManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    Log.d(TAG, "GlyphManager connected")
                    try {
                        // Register specifically for Nothing Phone (2) device configuration
                        val registered = glyphManager?.register(Glyph.DEVICE_22111) ?: false
                        Log.d(TAG, "GlyphManager registered: $registered")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to register Glyph device: ${e.message}")
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    Log.d(TAG, "GlyphManager disconnected")
                    isGlyphSessionOpen = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "GlyphSDK init error: ${e.message}")
        }
    }

    private fun openGlyphSession() {
        if (!isGlyphSessionOpen) {
            try {
                glyphManager?.openSession()
                isGlyphSessionOpen = true
                Log.d(TAG, "Glyph session opened")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Glyph session: ${e.message}")
            }
        }
    }

    private fun closeGlyphSession() {
        if (isGlyphSessionOpen) {
            try {
                glyphManager?.turnOff()
                glyphManager?.closeSession()
                isGlyphSessionOpen = false
                Log.d(TAG, "Glyph session closed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close Glyph session: ${e.message}")
            }
        }
    }

    private fun updateGlyphProgress(remainingMs: Long, totalDurationMs: Long) {
        if (glyphManager == null || !isGlyphSessionOpen) return
        try {
            val progress = if (totalDurationMs > 0) {
                ((remainingMs.toFloat() / totalDurationMs.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                100
            }

            val builder = GlyphFrame.Builder(Glyph.DEVICE_22111)
            val frame = builder.buildChannelC().build()
            
            glyphManager?.displayProgress(frame, progress)
            Log.d(TAG, "Updated Glyph progress to: $progress%")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Glyph progress: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeGlyphSession()
        try {
            glyphManager?.unInit()
        } catch (e: Exception) {
            Log.e(TAG, "Error during glyphManager unInit: ${e.message}")
        }
        stopTimerService()
    }
}
