package cc.juanma.mcm3d.recording

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cc.juanma.mcm3d.MainActivity
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.appGraph
import cc.juanma.mcm3d.core.route.RouteGuide
import cc.juanma.mcm3d.sensing.SensingThread
import cc.juanma.mcm3d.ui.formatDistance
import cc.juanma.mcm3d.ui.instructionText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Foreground service for everything recorded with the screen off: the user's outing and the
 * diagnostic field test. Both can run at the same time; the service stops when neither does.
 */
class RecordingService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var session: SessionRecorder? = null
  private var outing: OutingRecorder? = null
  private var outingStarting = false
  private var stopOutingWhenStarted = false
  private var lastNotificationMs = 0L

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_OUTING -> startOuting(intent.getLongExtra(EXTRA_FOLLOW_ROUTE, NO_ROUTE))
      ACTION_STOP_OUTING -> stopOuting()
      ACTION_START_SESSION -> startSession()
      ACTION_STOP_SESSION -> stopSession()
      ACTION_MARK -> mark()
    }
    if (session == null && outing == null && !outingStarting) stopSelf()
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    session?.let { active -> SensingThread.handler.post { active.stop() } }
    session = null
    outing?.let(::finishOuting)
    outing = null
    scope.cancel()
    super.onDestroy()
  }

  private fun startOuting(followRouteId: Long) {
    if (outing != null || outingStarting) return
    outingStarting = true
    stopOutingWhenStarted = false
    goForeground()
    val follow = followRouteId.takeIf { it != NO_ROUTE }
    OutingStatus.update(
      OutingState(active = true, starting = true, followRouteId = follow, startElapsedMs = SystemClock.elapsedRealtime()),
    )
    OutingStatus.updateTrace(Trace.EMPTY)
    val routes = appGraph.routes
    scope.launch {
      val startWallMs = System.currentTimeMillis()
      val followRoute = follow?.let { id ->
        val route = routes.route(id)
        val geometry = routes.loadGeometry(id)
        if (route != null && geometry != null) FollowRoute(id, route.name, RouteGuide(geometry.lats, geometry.lons)) else null
      }
      val routeId = routes.createRecording(defaultOutingName(), startWallMs)
      val appender = withContext(Dispatchers.IO) { routes.openAppender(routeId, startWallMs) }
      val recorder = OutingRecorder(
        context = applicationContext,
        routeId = routeId,
        appender = appender,
        follow = followRoute,
        onMark = { number, waypoint ->
          val named = waypoint.copy(name = getString(R.string.waypoint_default_name, number))
          appGraph.appScope.launch { routes.addWaypoint(routeId, named) }
        },
        onChanged = { important -> scope.launch { refreshNotification(important) } },
      )
      outing = recorder
      outingStarting = false
      SensingThread.handler.post { recorder.start() }
      if (stopOutingWhenStarted) stopOuting()
    }
  }

  private fun stopOuting() {
    if (outingStarting) {
      stopOutingWhenStarted = true
      return
    }
    val recorder = outing ?: return
    outing = null
    finishOuting(recorder)
    updateForegroundOrStop()
  }

  private fun finishOuting(recorder: OutingRecorder) {
    val graph = appGraph
    SensingThread.handler.post {
      recorder.stop()
      OutingStatus.update(OutingState())
      OutingStatus.updateTrace(Trace.EMPTY)
      graph.appScope.launch { graph.routes.finishRecording(recorder.routeId) }
    }
  }

  private fun startSession() {
    if (session != null) return
    goForeground()
    val created = SessionRecorder(applicationContext) { refreshNotificationFromAnyThread() }
    session = created
    SensingThread.handler.post { created.start() }
  }

  private fun stopSession() {
    val active = session ?: return
    session = null
    SensingThread.handler.post { active.stop() }
    updateForegroundOrStop()
  }

  private fun mark() {
    outing?.let { recorder -> SensingThread.handler.post { recorder.mark() } }
    session?.let { recorder -> SensingThread.handler.post { recorder.mark() } }
  }

  private fun updateForegroundOrStop() {
    if (session == null && outing == null && !outingStarting) {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      stopSelf()
    } else {
      refreshNotification(important = true)
    }
  }

  private fun goForeground() {
    ensureChannel()
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
    ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
  }

  private fun refreshNotificationFromAnyThread() {
    scope.launch { refreshNotification(important = true) }
  }

  private fun refreshNotification(important: Boolean) {
    val now = SystemClock.elapsedRealtime()
    if (!important && now - lastNotificationMs < NOTIFICATION_MIN_INTERVAL_MS) return
    lastNotificationMs = now
    val manager = NotificationManagerCompat.from(this)
    if (!manager.areNotificationsEnabled()) return
    try {
      manager.notify(NOTIFICATION_ID, buildNotification())
    } catch (denied: SecurityException) {
    }
  }

  private fun ensureChannel() {
    val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
      .setName(getString(R.string.recording_channel))
      .setShowBadge(false)
      .build()
    NotificationManagerCompat.from(this).createNotificationChannel(channel)
  }

  private fun buildNotification(): Notification {
    val outingState = OutingStatus.state.value
    val outingActive = outingState.active
    val title: String
    val text: String
    if (outingActive) {
      title = getString(R.string.outing_notification_title)
      text = outingSummary(outingState)
    } else {
      val still = RecordingStatus.state.value.mode == RecordingMode.STILL
      title = getString(R.string.recording_notification_title)
      text = getString(if (still) R.string.recording_mode_still else R.string.recording_mode_moving)
    }
    val openIntent = Intent(this, MainActivity::class.java)
      .setAction(if (outingActive) MainActivity.ACTION_OPEN_OUTING else Intent.ACTION_MAIN)
      .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val openApp = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_recording)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setContentIntent(openApp)
      .addAction(0, getString(R.string.recording_mark), servicePendingIntent(ACTION_MARK, 1))
    if (outingActive) {
      builder.addAction(0, getString(R.string.outing_finish), servicePendingIntent(ACTION_STOP_OUTING, 2))
      if (outingState.startElapsedMs > 0) {
        builder.setUsesChronometer(true).setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - outingState.startElapsedMs))
      }
    } else {
      builder.addAction(0, getString(R.string.recording_stop), servicePendingIntent(ACTION_STOP_SESSION, 3))
    }
    return builder.build()
  }

  private fun outingSummary(state: OutingState): String {
    if (state.starting) return getString(R.string.outing_starting)
    val parts = mutableListOf<String>()
    val instruction = state.instruction
    when {
      state.mode == RecordingMode.STILL -> parts += getString(R.string.outing_paused_short)
      !state.hasFix -> parts += getString(R.string.outing_searching_short)
      instruction != null -> parts += instructionText(resources, instruction).let { (title, detail) -> listOfNotNull(title, detail).joinToString(" ") }
    }
    parts += getString(R.string.outing_walked_short, formatDistance(state.distanceM))
    if (session != null) parts += getString(R.string.outing_with_field_test)
    return parts.joinToString(" · ")
  }

  private fun defaultOutingName(): String {
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
    return getString(R.string.outing_default_name, stamp)
  }

  private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
    PendingIntent.getService(
      this,
      requestCode,
      Intent(this, RecordingService::class.java).setAction(action),
      PendingIntent.FLAG_IMMUTABLE,
    )

  companion object {
    private const val ACTION_START_OUTING = "cc.juanma.mcm3d.recording.START_OUTING"
    private const val ACTION_STOP_OUTING = "cc.juanma.mcm3d.recording.STOP_OUTING"
    private const val ACTION_START_SESSION = "cc.juanma.mcm3d.recording.START"
    private const val ACTION_STOP_SESSION = "cc.juanma.mcm3d.recording.STOP"
    private const val ACTION_MARK = "cc.juanma.mcm3d.recording.MARK"
    private const val EXTRA_FOLLOW_ROUTE = "follow_route"
    private const val NO_ROUTE = -1L
    private const val CHANNEL_ID = "recording"
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_MIN_INTERVAL_MS = 15_000L

    /** Publishes the starting state at once, so a screen opened right after this call never sees it idle. */
    fun startOuting(context: Context, followRouteId: Long?) {
      if (OutingStatus.state.value.active) return
      OutingStatus.update(
        OutingState(active = true, starting = true, followRouteId = followRouteId, startElapsedMs = SystemClock.elapsedRealtime()),
      )
      try {
        ContextCompat.startForegroundService(
          context,
          intent(context, ACTION_START_OUTING).putExtra(EXTRA_FOLLOW_ROUTE, followRouteId ?: NO_ROUTE),
        )
      } catch (error: IllegalStateException) {
        OutingStatus.update(OutingState())
      }
    }

    fun stopOuting(context: Context) {
      context.startService(intent(context, ACTION_STOP_OUTING))
    }

    fun startSession(context: Context) =
      ContextCompat.startForegroundService(context, intent(context, ACTION_START_SESSION))

    fun stopSession(context: Context) {
      context.startService(intent(context, ACTION_STOP_SESSION))
    }

    fun mark(context: Context) {
      context.startService(intent(context, ACTION_MARK))
    }

    private fun intent(context: Context, action: String) =
      Intent(context, RecordingService::class.java).setAction(action)
  }
}
