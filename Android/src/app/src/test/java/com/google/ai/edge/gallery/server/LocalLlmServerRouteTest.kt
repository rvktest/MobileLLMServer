package com.google.ai.edge.gallery.server

import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.proto.AccessTokenData
import com.google.ai.edge.gallery.proto.BenchmarkResult
import com.google.ai.edge.gallery.proto.Cutout
import com.google.ai.edge.gallery.proto.ImportedModel
import com.google.ai.edge.gallery.proto.Skill
import com.google.ai.edge.gallery.proto.Theme
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlmServerRouteTest {

  @Test
  fun healthz_returnsOk() =
    testApplication {
      application { LocalLlmServer.configureRouting(FakeDataStoreRepository()) }

      val response = client.get("/healthz")

      assertEquals(HttpStatusCode.OK, response.status)
      assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

  @Test
  fun readyz_returnsServiceUnavailableWhenNoModelSelected() =
    testApplication {
      ServerRuntimeState.stop()
      ServerRuntimeState.setStatus(ServerStatus(running = true, apiUrl = "http://127.0.0.1:8080/v1"))
      application { LocalLlmServer.configureRouting(FakeDataStoreRepository()) }

      val response = client.get("/readyz")

      assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
      assertTrue(response.bodyAsText().contains("\"status\":\"no_model_selected\""))
    }

  @Test
  fun readyz_returnsOkWhenModelIsInitializedAndIdle() =
    testApplication {
      val model = Model(name = "test-model")
      model.instance = Any()
      ServerRuntimeState.stop()
      ServerRuntimeState.setActiveModel(model)
      ServerRuntimeState.setStatus(ServerStatus(running = true, apiUrl = "http://127.0.0.1:8080/v1"))
      application { LocalLlmServer.configureRouting(FakeDataStoreRepository()) }

      val response = client.get("/readyz")

      assertEquals(HttpStatusCode.OK, response.status)
      assertTrue(response.bodyAsText().contains("\"status\":\"ready\""))
    }

  @Test
  fun readyz_returnsServiceUnavailableWhenServerIsBusy() =
    testApplication {
      val model = Model(name = "test-model")
      model.instance = Any()
      ServerRuntimeState.stop()
      ServerRuntimeState.setActiveModel(model)
      ServerRuntimeState.setStatus(ServerStatus(running = true, apiUrl = "http://127.0.0.1:8080/v1"))
      ServerRuntimeState.tryStartInference()
      application { LocalLlmServer.configureRouting(FakeDataStoreRepository()) }

      val response = client.get("/readyz")

      assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
      assertTrue(response.bodyAsText().contains("\"status\":\"busy\""))
      ServerRuntimeState.finishInference()
    }

  @Test
  fun models_returnsActiveModelWhenSelected() =
    testApplication {
      val model = Model(name = "persisted-model")
      ServerRuntimeState.stop()
      ServerRuntimeState.setActiveModel(model)
      application { LocalLlmServer.configureRouting(FakeDataStoreRepository()) }

      val response = client.get("/v1/models")

      assertEquals(HttpStatusCode.OK, response.status)
      assertTrue(response.bodyAsText().contains("persisted-model"))
    }

  @Test
  fun chatCompletions_returnsConflictWhenServerIsBusy() =
    testApplication {
      val model = Model(name = "test-model")
      model.instance = Any()
      ServerRuntimeState.stop()
      ServerRuntimeState.setActiveModel(model)
      ServerRuntimeState.setStatus(ServerStatus(running = true, apiUrl = "http://127.0.0.1:8080/v1"))
      ServerRuntimeState.tryStartInference()
      application { LocalLlmServer.configureRouting(FakeDataStoreRepository()) }

      val response =
        client.post("/v1/chat/completions") {
          contentType(ContentType.Application.Json)
          setBody(
            """
            {
              "model": "test-model",
              "stream": false,
              "messages": [
                {"role": "user", "content": "hello"}
              ]
            }
            """.trimIndent()
          )
        }

      assertEquals(HttpStatusCode.Conflict, response.status)
      assertTrue(response.bodyAsText().contains("server_busy"))
      ServerRuntimeState.finishInference()
    }
}

private class FakeDataStoreRepository : DataStoreRepository {
  override fun saveTextInputHistory(history: List<String>) = Unit

  override fun readTextInputHistory(): List<String> = emptyList()

  override fun saveTheme(theme: Theme) = Unit

  override fun readTheme(): Theme = Theme.THEME_AUTO

  override fun saveSecret(key: String, value: String) = Unit

  override fun readSecret(key: String): String? = null

  override fun deleteSecret(key: String) = Unit

  override fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) = Unit

  override fun clearAccessTokenData() = Unit

  override fun readAccessTokenData(): AccessTokenData? = null

  override fun saveImportedModels(importedModels: List<ImportedModel>) = Unit

  override fun readImportedModels(): List<ImportedModel> = emptyList()

  override fun saveSelectedModelName(modelName: String) = Unit

  override fun readSelectedModelName(): String = ""

  override fun saveServerAutostartEnabled(enabled: Boolean) = Unit

  override fun readServerAutostartEnabled(): Boolean = false

  override fun isTosAccepted(): Boolean = true

  override fun acceptTos() = Unit

  override fun isGemmaTermsOfUseAccepted(): Boolean = true

  override fun acceptGemmaTermsOfUse() = Unit

  override fun getHasRunTinyGarden(): Boolean = false

  override fun setHasRunTinyGarden(hasRun: Boolean) = Unit

  override fun addCutout(cutout: Cutout) = Unit

  override fun getAllCutouts(): List<Cutout> = emptyList()

  override fun setCutout(newCutout: Cutout) = Unit

  override fun setCutouts(cutouts: List<Cutout>) = Unit

  override fun setHasSeenBenchmarkComparisonHelp(seen: Boolean) = Unit

  override fun getHasSeenBenchmarkComparisonHelp(): Boolean = false

  override fun addBenchmarkResult(result: BenchmarkResult) = Unit

  override fun getAllBenchmarkResults(): List<BenchmarkResult> = emptyList()

  override fun deleteBenchmarkResult(index: Int) = Unit

  override fun addSkill(skill: Skill) = Unit

  override fun setSkills(skills: List<Skill>) = Unit

  override fun setSkillSelected(skill: Skill, selected: Boolean) = Unit

  override fun setAllSkillsSelected(selected: Boolean) = Unit

  override fun getAllSkills(): List<Skill> = emptyList()

  override fun deleteSkill(name: String) = Unit

  override suspend fun deleteSkills(names: Set<String>) = Unit

  override fun addViewedPromoId(promoId: String) = Unit

  override fun removeViewedPromoId(promoId: String) = Unit

  override fun hasViewedPromo(promoId: String): Boolean = false
}