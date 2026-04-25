package com.google.ai.edge.gallery.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRuntimeStateTest {

  @Test
  fun tryStartInference_allowsOnlyOneActiveRequest() {
    ServerRuntimeState.stop()

    assertTrue(ServerRuntimeState.tryStartInference())
    assertTrue(ServerRuntimeState.isBusy())

    assertFalse(ServerRuntimeState.tryStartInference())

    ServerRuntimeState.finishInference()
    assertFalse(ServerRuntimeState.isBusy())
  }

  @Test
  fun stop_resetsBusyState() {
    ServerRuntimeState.stop()
    ServerRuntimeState.tryStartInference()

    ServerRuntimeState.stop()

    assertFalse(ServerRuntimeState.isBusy())
  }
}