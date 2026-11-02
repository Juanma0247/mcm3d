package cc.juanma.mcm3d.recording

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cc.juanma.mcm3d.MainActivity
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.sensing.SensingThread

class RecordingService : Service() {
  private var recorder: SessionRecorder? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> startRecording()
      ACTION_MARK -> recorder?.let { SensingThread.handler.post { it.mark() } }
      ACTION_STOP -> stopRecording()
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    recorder?.let { active -> SensingThread.handler.post { active.stop() } }
    recorder = null
    super.onDestroy()
  }

  private fun startRecording() {
    if (recorder != null) return
    ensureChannel()
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      buildNotification(RecordingMode.MOVING),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
    )
    val created = SessionRecorder(applicationContext) { mode -> updateNotification(mode) }
    recorder = created
    SensingThread.handler.post { created.start() }
  }

  private fun stopRecording() {
    recorder?.let { active -> SensingThread.handler.post { active.stop() } }
    recorder = null
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun updateNotification(mode: RecordingMode) {
    val manager = NotificationManagerCompat.from(this)
    if (!manager.areNotificationsEnabled()) return
    try {
      manager.notify(NOTIFICATION_ID, buildNotification(mode))
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

  private fun buildNotification(mode: RecordingMode): Notification {
    val openApp = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_recording)
      .setContentTitle(getString(R.string.recording_notification_title))
      .setContentText(
        getString(
          if (mode == RecordingMode.STILL) R.string.recording_mode_still else R.string.recording_mode_moving,
        ),
      )
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setContentIntent(openApp)
      .addAction(0, getString(R.string.recording_mark), servicePendingIntent(ACTION_MARK, 1))
      .addAction(0, getString(R.string.recording_stop), servicePendingIntent(ACTION_STOP, 2))
      .build()
  }

  private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
    PendingIntent.getService(
      this,
      requestCode,
      Intent(this, RecordingService::class.java).setAction(action),
      PendingIntent.FLAG_IMMUTABLE,
    )

  companion object {
    private const val ACTION_START = "cc.juanma.mcm3d.recording.START"
    private const val ACTION_MARK = "cc.juanma.mcm3d.recording.MARK"
    private const val ACTION_STOP = "cc.juanma.mcm3d.recording.STOP"
    private const val CHANNEL_ID = "recording"
    private const val NOTIFICATION_ID = 1

    fun start(context: Context) =
      ContextCompat.startForegroundService(context, intent(context, ACTION_START))

    fun mark(context: Context) {
      context.startService(intent(context, ACTION_MARK))
    }

    fun stop(context: Context) {
      context.startService(intent(context, ACTION_STOP))
    }

    private fun intent(context: Context, action: String) =
      Intent(context, RecordingService::class.java).setAction(action)
  }
}
