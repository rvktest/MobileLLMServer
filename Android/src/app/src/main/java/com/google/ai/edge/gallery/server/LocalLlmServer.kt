package com.google.ai.edge.gallery.server

import android.util.Log
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.proto.ImportedModel
import com.google.ai.edge.gallery.runtime.runtimeHelper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val TAG = "LocalLlmServer"
private const val SERVER_PORT = 8080
// Max model wait time = MAX_MODEL_INIT_RETRIES * MODEL_INIT_RETRY_DELAY_MS (10s). This keeps API
// calls responsive while still allowing short warmup windows before we return an initialization
// error.
private const val MAX_MODEL_INIT_RETRIES = 100
private const val MODEL_INIT_RETRY_DELAY_MS = 100L

object LocalLlmServer {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private var server: ApplicationEngine? = null

  fun start(dataStoreRepository: DataStoreRepository): String {
    if (server != null) {
      return currentApiUrl()
    }

    server = embeddedServer(Netty, port = SERVER_PORT) { configureRouting(dataStoreRepository) }.start(wait = false)

    return currentApiUrl()
  }

  fun stop() {
    server?.stop(gracePeriodMillis = 500, timeoutMillis = 1500)
    server = null
  }

  fun isRunning(): Boolean = server != null

  private fun currentApiUrl(): String {
    val ip = getLocalIpv4Address()
    return "http://$ip:$SERVER_PORT/v1"
  }

  private fun Application.configureRouting(dataStoreRepository: DataStoreRepository) {
    install(ContentNegotiation) { json(json) }
    install(CORS) {
      anyHost()
      allowHeader("Content-Type")
      allowHeader("Authorization")
      allowMethod(io.ktor.http.HttpMethod.Get)
      allowMethod(io.ktor.http.HttpMethod.Post)
    }

    routing {
      route("/v1") {
        get("/models") {
          val importedModels = dataStoreRepository.readImportedModels()
          val activeModel = ServerRuntimeState.getActiveModel()

          val models =
            buildList {
                addAll(importedModels.map { imported -> imported.toOpenAiModel() })
                if (activeModel != null && none { it.id == activeModel.name }) {
                  add(
                    OpenAiModel(
                      id = activeModel.name,
                      created = System.currentTimeMillis() / 1000,
                    )
                  )
                }
              }
              .sortedBy { it.id.lowercase() }

          call.respond(ModelsResponse(data = models))
        }

        post("/chat/completions") {
          val request = call.receive<ChatCompletionRequest>()
          val userPrompt = request.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
          if (userPrompt.isBlank()) {
            call.respond(
              HttpStatusCode.BadRequest,
              OpenAiErrorResponse(OpenAiErrorBody(message = "Missing user message content.")),
            )
            return@post
          }

          val model = resolveModel(request.model)
          if (model == null) {
            val errorMessage =
              if (request.model.isNullOrBlank()) {
                "No initialized local model is available for inference."
              } else {
                "Requested model '${request.model}' is not active. Select and initialize it in the app first."
              }
            call.respond(
              HttpStatusCode.BadRequest,
              OpenAiErrorResponse(OpenAiErrorBody(message = errorMessage)),
            )
            return@post
          }

          if (request.stream) {
            streamResponse(model = model, prompt = userPrompt, requestModel = request.model)
            return@post
          }

          try {
            val output = runInferenceCollect(model = model, prompt = userPrompt)
            call.respond(
              ChatCompletionResponse(
                id = "chatcmpl-${UUID.randomUUID()}",
                created = System.currentTimeMillis() / 1000,
                model = request.model ?: model.name,
                choices =
                  listOf(
                    ChatCompletionChoice(
                      index = 0,
                      message = ChatCompletionMessage(role = "assistant", content = output),
                      finishReason = "stop",
                    )
                  ),
              )
            )
          } catch (t: Throwable) {
            call.respond(
              HttpStatusCode.InternalServerError,
              OpenAiErrorResponse(OpenAiErrorBody(message = t.message ?: "Inference failed.")),
            )
          }
        }
      }
    }
  }

  private suspend fun io.ktor.server.application.ApplicationCall.streamResponse(
    model: com.google.ai.edge.gallery.data.Model,
    prompt: String,
    requestModel: String?,
  ) {
    val streamId = "chatcmpl-${UUID.randomUUID()}"
    val createdAt = System.currentTimeMillis() / 1000
    val events = Channel<InferenceEvent>(Channel.UNLIMITED)

    startInference(model = model, prompt = prompt, events = events)

    respondTextWriter(contentType = ContentType.Text.EventStream) {
      writeSseData(
        ChatCompletionChunkResponse(
          id = streamId,
          created = createdAt,
          model = requestModel ?: model.name,
          choices =
            listOf(
              ChatCompletionChunkChoice(
                index = 0,
                delta = ChatCompletionChunkDelta(role = "assistant"),
                finishReason = null,
              )
            ),
        )
      )

      for (event in events) {
        when (event) {
          is InferenceEvent.Token -> {
            writeSseData(
              ChatCompletionChunkResponse(
                id = streamId,
                created = createdAt,
                model = requestModel ?: model.name,
                choices =
                  listOf(
                    ChatCompletionChunkChoice(
                      index = 0,
                      delta = ChatCompletionChunkDelta(content = event.text),
                      finishReason = null,
                    )
                  ),
              )
            )
          }

          is InferenceEvent.Done -> {
            writeSseData(
              ChatCompletionChunkResponse(
                id = streamId,
                created = createdAt,
                model = requestModel ?: model.name,
                choices =
                  listOf(
                    ChatCompletionChunkChoice(
                      index = 0,
                      delta = ChatCompletionChunkDelta(),
                      finishReason = "stop",
                    )
                  ),
              )
            )
            write("data: [DONE]\n\n")
            flush()
          }

          is InferenceEvent.Error -> {
            write(
              "data: ${json.encodeToString(OpenAiErrorResponse.serializer(), OpenAiErrorResponse(OpenAiErrorBody(message = event.message)))}\n\n"
            )
            flush()
          }
        }
      }
    }
  }

  private fun java.io.Writer.writeSseData(response: ChatCompletionChunkResponse) {
    write(
      "data: ${json.encodeToString(ChatCompletionChunkResponse.serializer(), response)}\n\n"
    )
    flush()
  }

  private suspend fun runInferenceCollect(
    model: com.google.ai.edge.gallery.data.Model,
    prompt: String,
  ): String {
    val events = Channel<InferenceEvent>(Channel.UNLIMITED)
    startInference(model = model, prompt = prompt, events = events)

    val fullResponse = StringBuilder()
    for (event in events) {
      when (event) {
        is InferenceEvent.Token -> fullResponse.append(event.text)
        is InferenceEvent.Done -> return fullResponse.toString()
        is InferenceEvent.Error -> throw IllegalStateException(event.message)
      }
    }
    return fullResponse.toString()
  }

  private fun startInference(
    model: com.google.ai.edge.gallery.data.Model,
    prompt: String,
    events: Channel<InferenceEvent>,
  ) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
      try {
        awaitModelInitialization(model)

        model.runtimeHelper.runInference(
          model = model,
          input = prompt,
          resultListener = { partialResult, done, _ ->
            if (partialResult.isNotEmpty()) {
              events.trySend(InferenceEvent.Token(partialResult))
            }
            if (done) {
              events.trySend(InferenceEvent.Done)
              events.close()
              scope.cancel()
            }
          },
          cleanUpListener = {},
          onError = {
            events.trySend(InferenceEvent.Error(it))
            events.close()
            scope.cancel()
          },
          coroutineScope = scope,
        )
      } catch (t: Throwable) {
        events.trySend(InferenceEvent.Error(t.message ?: "Inference failed."))
        events.close()
        scope.cancel()
      }
    }
  }

  private suspend fun awaitModelInitialization(model: com.google.ai.edge.gallery.data.Model) {
    repeat(MAX_MODEL_INIT_RETRIES) {
      if (model.instance != null) {
        return
      }
      delay(MODEL_INIT_RETRY_DELAY_MS)
    }
    throw IllegalStateException("Model is not initialized after waiting up to 10 seconds.")
  }

  private fun resolveModel(requestedModel: String?): com.google.ai.edge.gallery.data.Model? {
    val activeModel = ServerRuntimeState.getActiveModel() ?: return null
    if (requestedModel.isNullOrBlank() || requestedModel == activeModel.name) {
      return activeModel
    }
    return null
  }

  private fun ImportedModel.toOpenAiModel(): OpenAiModel {
    return OpenAiModel(id = fileName, created = System.currentTimeMillis() / 1000)
  }

  private fun getLocalIpv4Address(): String {
    return try {
      val networkInterfaces = NetworkInterface.getNetworkInterfaces()
      for (networkInterface in networkInterfaces) {
        val addresses = networkInterface.inetAddresses
        for (address in addresses) {
          if (!address.isLoopbackAddress && address is Inet4Address) {
            return address.hostAddress ?: "0.0.0.0"
          }
        }
      }
      "0.0.0.0"
    } catch (e: Exception) {
      Log.e(TAG, "Failed to resolve local IPv4 address", e)
      "0.0.0.0"
    }
  }
}

private sealed class InferenceEvent {
  data class Token(val text: String) : InferenceEvent()

  data object Done : InferenceEvent()

  data class Error(val message: String) : InferenceEvent()
}
