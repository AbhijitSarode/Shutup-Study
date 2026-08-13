package com.abhijit.shutupNstudy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.abhijit.shutupNstudy.R
import com.abhijit.shutupNstudy.service.TimerService

class TimerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = TimerService.timerState.value
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, state)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.abhijit.shutupNstudy.WIDGET_UPDATE") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, TimerWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: TimerService.Companion.ServiceTimerState?
    ) {
        val views = RemoteViews(context.packageName, R.layout.timer_widget_layout)

        if (state == null) {
            views.setTextViewText(R.id.widget_status, "No Active Session")
            views.setTextViewText(R.id.widget_timer, "--:--")
            views.setViewVisibility(R.id.widget_control_layout, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_control_layout, if (state.isLeader) View.VISIBLE else View.GONE)

            val phaseLabel = when (state.currentPhase) {
                "shortBreak" -> "Short Break"
                "longBreak" -> "Long Break"
                else -> "Focus Phase"
            }

            views.setTextViewText(R.id.widget_status, "$phaseLabel - ${state.status.uppercase()}")
            views.setTextViewText(R.id.widget_timer, formatTime(state.secondsRemaining))

            // Play/Pause button setup
            val playPauseAction = if (state.status == "running") TimerService.ACTION_PAUSE else TimerService.ACTION_PLAY
            val playPauseIntent = Intent(context, TimerService::class.java).apply {
                action = playPauseAction
            }
            val playPausePending = PendingIntent.getService(
                context,
                10,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePending)
            views.setTextViewText(R.id.widget_btn_play_pause, if (state.status == "running") "Pause" else "Play")

            // Reset button setup
            val resetIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_RESET
            }
            val resetPending = PendingIntent.getService(
                context,
                11,
                resetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_reset, resetPending)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun formatTime(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}
