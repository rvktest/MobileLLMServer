package com.google.ai.edge.gallery.server

import com.google.ai.edge.gallery.data.Model
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerStatus(
  val running: Boolean = false,
  val apiUrl: String = "",
  val backend: String = "",
  val busy: Boolean = false,
)

object ServerRuntimeState {
  private val _status = MutableStateFlow(ServerStatus())
  val status: StateFlow<ServerStatus> = _status.asStateFlow()

  private var activeModel: Model? = null
  private var backendLabel: String = ""
  private var availableLocalModelIds: Set<String> = emptySet()
  private val inferenceInProgress = AtomicBoolean(false)

  fun setStatus(status: ServerStatus) {
    val backend = if (status.backend.isNotBlank()) status.backend else backendLabel
    _status.value = status.copy(backend = backend, busy = inferenceInProgress.get())
  }

  fun setActiveModel(model: Model?) {
    activeModel = model
  }

  fun getActiveModel(): Model? = activeModel

  fun setBackendLabel(label: String) {
    backendLabel = label
    _status.value = _status.value.copy(backend = label)
  }

  fun getBackendLabel(): String = backendLabel

  fun setAvailableLocalModelIds(modelIds: Collection<String>) {
    availableLocalModelIds = modelIds.filter { it.isNotBlank() }.toSet()
  }

  fun getAvailableLocalModelIds(): Set<String> = availableLocalModelIds

  fun isBusy(): Boolean = inferenceInProgress.get()

  fun tryStartInference(): Boolean {
    val started = inferenceInProgress.compareAndSet(false, true)
    if (started) {
      _status.value = _status.value.copy(busy = true)
    }
    return started
  }

  fun finishInference() {
    inferenceInProgress.set(false)
    _status.value = _status.value.copy(busy = false)
  }

  fun stop() {
    inferenceInProgress.set(false)
    activeModel = null
    backendLabel = ""
    _status.value = ServerStatus(running = false, apiUrl = "", backend = "", busy = false)
  }
}
