/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.customtasks.companion

import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.RuntimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionModelTest {
  @Test
  fun companionModelDefaultsToGemma4E2bMultimodal() {
    val model = createCompanionModel()

    assertEquals("Gemma-4-E2B-it", model.name)
    assertEquals("Mr. Duck's Brain", model.displayName)
    assertFalse(model.info.contains("LiteRT-LM"))
    assertFalse(model.info.contains("Gemma"))
    assertEquals("gemma-4-E2B-it.litertlm", model.downloadFileName)
    assertEquals(2_588_147_712L, model.sizeInBytes)
    assertEquals(8, model.minDeviceMemoryInGb)
    assertEquals(RuntimeType.LITERT_LM, model.runtimeType)
    assertEquals(listOf(Accelerator.GPU, Accelerator.CPU), model.accelerators)
    assertTrue(model.llmSupportImage)
    assertTrue(model.llmSupportAudio)
    assertTrue(model.url.contains("huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"))
  }

  @Test
  fun companionModelDefaultsToGpuBackend() {
    val model = createCompanionModel()
    model.preProcess()

    assertEquals(Accelerator.GPU.label, model.getStringConfigValue(ConfigKeys.ACCELERATOR))
    assertEquals(Accelerator.GPU, model.visionAccelerator)
  }

  @Test
  fun companionModelEnablesImageContextByDefault() {
    val model = createCompanionModel()
    model.preProcess()

    assertTrue(model.getBooleanConfigValue(COMPANION_ENABLE_IMAGE_CONTEXT_CONFIG_KEY))
    assertTrue(model.configs.any { it.key == COMPANION_ENABLE_IMAGE_CONTEXT_CONFIG_KEY })
  }

  @Test
  fun companionModelUsesFullContextWindowByDefault() {
    val model = createCompanionModel()
    model.preProcess()

    assertEquals(COMPANION_CONTEXT_WINDOW_TOKENS, model.getIntConfigValue(ConfigKeys.MAX_TOKENS))
  }

  @Test
  fun companionPromptMakesDuckCuteDeadpanAndWitty() {
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("Mr. Duck"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("rubber duck"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("not a generic AI assistant"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("cute"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("tiny"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("soft around the edges"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("deadpan"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("dry wit"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("not bubbly"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("gentle roasts"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("tiny emoji"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("Answer questions directly"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("personality must not replace the answer"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("Use visual comments only when relevant"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("Do not label, caption, enumerate, or narrate inputs"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("Never start with Image, Audio, Snapshot"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("clothing"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("posture"))
    assertTrue(COMPANION_SYSTEM_PROMPT.contains("Do not identify people"))
    assertFalse(COMPANION_SYSTEM_PROMPT.contains("over-the-top"))
    assertFalse(COMPANION_SYSTEM_PROMPT.contains("emoji-friendly"))
  }

  @Test
  fun turnPromptReinforcesDuckPersonaAndVisibleContext() {
    val prompt = companionPromptForTurn()

    assertTrue(prompt.contains("attached audio is the user's current message"))
    assertTrue(prompt.contains("Silently understand the speech"))
    assertTrue(prompt.contains("routing text here is not the user's request"))
    assertTrue(prompt.contains("attached camera frame is private context"))
    assertTrue(prompt.contains("private visual context"))
    assertTrue(prompt.contains("Mr. Duck"))
    assertTrue(prompt.contains("answer that spoken request directly"))
    assertTrue(prompt.contains("do not caption or list the inputs"))
    assertTrue(prompt.contains("cute"))
    assertTrue(prompt.contains("witty"))
    assertTrue(prompt.contains("deadpan"))
    assertTrue(prompt.contains("not sugary"))
  }

  @Test
  fun debugLogSummarizesCurrentLiteRtPayload() {
    val summary =
      companionTurnDebugLog(
        prompt = "prompt text",
        frameSizes = listOf(CompanionFrameDebugInfo(width = 640, height = 480)),
        audioPcmBytes = 32_000,
        audioWavBytes = 32_044,
        sampleRate = 16_000,
        hiddenHistoryTurns = 2,
        resetBeforeTurn = true,
      )

    assertTrue(summary.contains("LiteRT turn payload"))
    assertTrue(summary.contains("order=image,audio,text"))
    assertTrue(summary.contains("imageCount=1"))
    assertTrue(summary.contains("firstImage=640x480"))
    assertTrue(summary.contains("audioPcmBytes=32000"))
    assertTrue(summary.contains("audioWavBytes=32044"))
    assertTrue(summary.contains("sampleRate=16000"))
    assertTrue(summary.contains("hiddenTurns=2"))
    assertTrue(summary.contains("resetBeforeTurn=true"))
    assertTrue(summary.contains("prompt=\"prompt text\""))
  }
}
