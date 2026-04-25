package com.google.ai.edge.gallery.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.DataStoreRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val CHANNEL_ID = "mobile_llm_server_channel"
private const val CHANNEL_NAME = "MobileLLMServer"
private const val NOTIFICATION_ID = 8080
private const val WAKE_LOCK_TAG = "MobileLLMServer:WakeLock"
private const val WIFI_LOCK_TAG = "MobileLLMServer:WifiLock"
// Keep locks long enough for practical server sessions while ensuring automatic release.
private const val LOCK_TIMEOUT_12_HOURS_MS = 12 * 60 * 60 * 1000L

@AndroidEntryPoint
class ServerService : Service() {

  @Inject lateinit var dataStoreRepository: DataStoreRepository

  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> stopServerAndService(shouldStopSelf = true)
      else -> startServer()
    }
    return START_STICKY
  }

  override fun onDestroy() {
    stopServerAndService(shouldStopSelf = false)
    super.onDestroy()
  }

  private fun startServer() {
    createNotificationChannel()
    acquireLocks()

    val apiUrl = LocalLlmServer.start(dataStoreRepository = dataStoreRepository)
    startForeground(NOTIFICATION_ID, buildNotification(apiUrl))
    dataStoreRepository.saveServerAutostartEnabled(true)
    ServerRuntimeState.setStatus(ServerStatus(running = true, apiUrl = apiUrl))
  }

  private fun stopServerAndService(shouldStopSelf: Boolean) {
    LocalLlmServer.stop()
    releaseLocks()
    dataStoreRepository.saveServerAutostartEnabled(false)
    ServerRuntimeState.stop()
    stopForeground(STOP_FOREGROUND_REMOVE)
    if (shouldStopSelf) {
      stopSelf()
    }
  }

  private fun buildNotification(apiUrl: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.server_local_api_url, apiUrl))
      .setSmallIcon(android.R.drawable.stat_sys_upload_done)
      .setOngoing(true)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
      val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun acquireLocks() {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (wakeLock?.isHeld != true) {
      wakeLock =
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
          setReferenceCounted(false)
          acquire(LOCK_TIMEOUT_12_HOURS_MS)
        }
    }

    val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    if (wifiLock?.isHeld != true) {
      wifiLock =
        wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG).apply {
          setReferenceCounted(false)
          acquire()
        }
    }
  }

  private fun releaseLocks() {
    if (wakeLock?.isHeld == true) {
      wakeLock?.release()
    }
    wakeLock = null

    if (wifiLock?.isHeld == true) {
      wifiLock?.release()
    }
    wifiLock = null
  }

  companion object {
    const val ACTION_START = "com.google.ai.edge.gallery.server.action.START"
    const val ACTION_STOP = "com.google.ai.edge.gallery.server.action.STOP"

    fun buildStartIntent(context: Context): Intent {
      return Intent(context, ServerService::class.java).apply { action = ACTION_START }
    }

    fun buildStopIntent(context: Context): Intent {
      return Intent(context, ServerService::class.java).apply { action = ACTION_STOP }
    }
  }
}
