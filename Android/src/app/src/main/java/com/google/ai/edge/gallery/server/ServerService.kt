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
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.runtimeHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val CHANNEL_ID = "mobile_llm_server_channel"
private const val CHANNEL_NAME = "MobileLLMServer"
private const val NOTIFICATION_ID = 8080
private const val ERROR_NOTIFICATION_ID = 8081
private const val WAKE_LOCK_TAG = "MobileLLMServer:WakeLock"
private const val WIFI_LOCK_TAG = "MobileLLMServer:WifiLock"
// Keep locks long enough for practical server sessions while ensuring automatic release.
private const val LOCK_TIMEOUT_12_HOURS_MS = 12 * 60 * 60 * 1000L

@AndroidEntryPoint
class ServerService : Service() {

  @Inject lateinit var dataStoreRepository: DataStoreRepository

  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> stopServerAndService(shouldStopSelf = true)
      else -> {
        val modelPath = intent?.getStringExtra(EXTRA_MODEL_PATH)
        val useGpu = intent?.getBooleanExtra(EXTRA_USE_GPU, true) ?: true
        startServer(modelPath = modelPath, useGpu = useGpu)
      }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    stopServerAndService(shouldStopSelf = false)
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun startServer(modelPath: String?, useGpu: Boolean) {
    createNotificationChannel()
    acquireLocks()
    startForeground(NOTIFICATION_ID, buildNotification(apiUrl = getString(R.string.server_default_api_url)))

    serviceScope.launch {
      val configuredModel =
        buildConfiguredModel(modelPath = modelPath, useGpu = useGpu)
          ?: run {
            onStartupFailed(
              "Select a downloaded model on the home screen before starting the server."
            )
            return@launch
          }

      val currentModel = ServerRuntimeState.getActiveModel()
      val configuredBackend =
        configuredModel.getStringConfigValue(
          key = ConfigKeys.ACCELERATOR,
          defaultValue = Accelerator.CPU.label,
        )
      val samePath =
        currentModel != null &&
          currentModel.getPath(applicationContext) == configuredModel.getPath(applicationContext)
      val sameBackend = ServerRuntimeState.getBackendLabel() == configuredBackend

      if (currentModel?.instance != null && (!samePath || !sameBackend)) {
        cleanupModel(currentModel)
      }

      if (configuredModel.instance == null) {
        val initError = initializeModel(configuredModel)
        if (initError.isNotEmpty()) {
          onStartupFailed(initError)
          return@launch
        }
      }

      ServerRuntimeState.setActiveModel(configuredModel)
      ServerRuntimeState.setBackendLabel(configuredBackend)

      val apiUrl = LocalLlmServer.start(dataStoreRepository = dataStoreRepository, context = applicationContext)
      ServerRuntimeState.setStatus(
        ServerStatus(running = true, apiUrl = apiUrl, backend = configuredBackend)
      )
      dataStoreRepository.saveServerAutostartEnabled(true)
      updateRunningNotification(apiUrl)
    }
  }

  private suspend fun initializeModel(model: Model): String {
    return suspendCancellableCoroutine { continuation ->
      model.runtimeHelper.initialize(
        context = applicationContext,
        model = model,
        supportImage = false,
        supportAudio = false,
        onDone = { error -> continuation.resume(error) },
        coroutineScope = serviceScope,
      )
    }
  }

  private suspend fun cleanupModel(model: Model) {
    suspendCancellableCoroutine<Unit> { continuation ->
      model.runtimeHelper.cleanUp(model = model) { continuation.resume(Unit) }
    }
  }

  private fun buildConfiguredModel(modelPath: String?, useGpu: Boolean): Model? {
    val activeModel = ServerRuntimeState.getActiveModel() ?: return null
    val resolvedPath = modelPath?.takeIf { it.isNotBlank() } ?: activeModel.getPath(applicationContext)
    val acceleratorLabel = if (useGpu) Accelerator.GPU.label else Accelerator.CPU.label
    val updatedConfigValues = activeModel.configValues.toMutableMap()
    updatedConfigValues[ConfigKeys.ACCELERATOR.label] = acceleratorLabel

    return activeModel.copy(localModelFilePathOverride = resolvedPath).apply {
      configValues = updatedConfigValues
    }
  }

  private fun onStartupFailed(message: String) {
    stopServerAndService(shouldStopSelf = false)
    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(ERROR_NOTIFICATION_ID, buildErrorNotification(message))
    stopSelf()
  }

  private fun updateRunningNotification(apiUrl: String) {
    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(NOTIFICATION_ID, buildNotification(apiUrl))
  }

  private fun stopServerAndService(shouldStopSelf: Boolean) {
    LocalLlmServer.stop()
    releaseLocks()
    ServerRuntimeState.getActiveModel()?.let { model ->
      if (model.instance != null) {
        model.runtimeHelper.cleanUp(model = model) {}
      }
    }
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

  private fun buildErrorNotification(errorMessage: String): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.server_status_title))
      .setContentText("Server start failed: $errorMessage")
      .setSmallIcon(android.R.drawable.stat_notify_error)
      .setAutoCancel(true)
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
    const val EXTRA_MODEL_PATH = "com.google.ai.edge.gallery.server.extra.MODEL_PATH"
    const val EXTRA_USE_GPU = "com.google.ai.edge.gallery.server.extra.USE_GPU"

    fun buildStartIntent(context: Context, modelPath: String? = null, useGpu: Boolean = true): Intent {
      return Intent(context, ServerService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_USE_GPU, useGpu)
        if (!modelPath.isNullOrBlank()) {
          putExtra(EXTRA_MODEL_PATH, modelPath)
        }
      }
    }

    fun buildStopIntent(context: Context): Intent {
      return Intent(context, ServerService::class.java).apply { action = ACTION_STOP }
    }
  }
}
