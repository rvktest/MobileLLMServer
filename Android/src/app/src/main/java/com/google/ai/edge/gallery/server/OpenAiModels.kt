package com.google.ai.edge.gallery.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionMessage(
  val role: String,
  val content: String,
)

@Serializable
data class ChatCompletionRequest(
  val model: String? = null,
  val messages: List<ChatCompletionMessage>,
  val stream: Boolean = false,
  @SerialName("max_tokens") val maxTokens: Int? = null,
  val temperature: Float? = null,
)

@Serializable
data class ChatCompletionChoice(
  val index: Int,
  val message: ChatCompletionMessage,
  @SerialName("finish_reason") val finishReason: String,
)

@Serializable
data class ChatCompletionResponse(
  val id: String,
  @SerialName("object") val objectType: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<ChatCompletionChoice>,
)

@Serializable
data class ChatCompletionChunkDelta(
  val role: String? = null,
  val content: String? = null,
)

@Serializable
data class ChatCompletionChunkChoice(
  val index: Int,
  val delta: ChatCompletionChunkDelta,
  @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatCompletionChunkResponse(
  val id: String,
  @SerialName("object") val objectType: String = "chat.completion.chunk",
  val created: Long,
  val model: String,
  val choices: List<ChatCompletionChunkChoice>,
)

@Serializable
data class OpenAiModel(
  val id: String,
  @SerialName("object") val objectType: String = "model",
  val created: Long,
  @SerialName("owned_by") val ownedBy: String = "local",
)

@Serializable
data class ModelsResponse(
  @SerialName("object") val objectType: String = "list",
  val data: List<OpenAiModel>,
)

@Serializable
data class OpenAiErrorBody(
  val message: String,
  val type: String = "invalid_request_error",
  val code: String? = null,
)

@Serializable
data class OpenAiErrorResponse(val error: OpenAiErrorBody)

@Serializable
data class HealthResponse(
  val status: String = "ok",
  @SerialName("server_running") val serverRunning: Boolean,
)

@Serializable
data class ReadinessResponse(
  val status: String,
  @SerialName("server_running") val serverRunning: Boolean,
  @SerialName("api_url") val apiUrl: String,
  @SerialName("active_model") val activeModel: String? = null,
  @SerialName("model_selected") val modelSelected: Boolean,
  @SerialName("model_initialized") val modelInitialized: Boolean,
  val busy: Boolean,
)
