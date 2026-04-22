package com.google.ai.edge.gallery.server

import com.google.ai.edge.gallery.data.Model
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerStatus(
  val running: Boolean = false,
  val apiUrl: String = "",
)

object ServerRuntimeState {
  private val _status = MutableStateFlow(ServerStatus())
  val status: StateFlow<ServerStatus> = _status.asStateFlow()

  private var activeModel: Model? = null

  fun setStatus(status: ServerStatus) {
    _status.value = status
  }

  fun setActiveModel(model: Model) {
    activeModel = model
  }

  fun getActiveModel(): Model? = activeModel

  fun stop() {
    _status.value = ServerStatus(running = false, apiUrl = "")
  }
}
