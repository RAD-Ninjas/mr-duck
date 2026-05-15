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
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelCapability
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.createLlmChatConfigs

const val COMPANION_TASK_ID = "llm_companion"
const val COMPANION_TASK_LABEL = "Companion"

const val COMPANION_SYSTEM_PROMPT =
  """You are a small, warm on-device AI companion. The user may send audio plus chronological front-camera snapshots captured while they were speaking. Reply naturally and concisely. If visual context is useful, mention it without over-explaining."""

fun createCompanionModel(): Model {
  val accelerators = listOf(Accelerator.CPU, Accelerator.GPU)
  return Model(
    name = "Gemma-4-E2B-it",
    displayName = "Gemma 4 E2B",
    info =
      "A multimodal Gemma 4 E2B LiteRT-LM model for on-device companion chat with image and audio input.",
    url =
      "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm?download=true",
    sizeInBytes = 2_588_147_712L,
    minDeviceMemoryInGb = 8,
    downloadFileName = "gemma-4-E2B-it.litertlm",
    version = "6e5c4f1e395deb959c494953478fa5cec4b8008f",
    isLlm = true,
    runtimeType = RuntimeType.LITERT_LM,
    showBenchmarkButton = false,
    showRunAgainButton = false,
    llmSupportImage = true,
    llmSupportAudio = true,
    llmMaxToken = 4000,
    accelerators = accelerators,
    visionAccelerator = Accelerator.GPU,
    bestForTaskIds = listOf(COMPANION_TASK_ID),
    capabilities = listOf(ModelCapability.LLM_THINKING, ModelCapability.SPECULATIVE_DECODING),
    capabilityToTaskTypes =
      mapOf(
        ModelCapability.LLM_THINKING to listOf(COMPANION_TASK_ID),
        ModelCapability.SPECULATIVE_DECODING to listOf(COMPANION_TASK_ID),
      ),
    configs =
      createLlmChatConfigs(
        defaultMaxToken = 4000,
        defaultMaxContextLength = 32000,
        defaultTopK = 64,
        defaultTopP = 0.95f,
        defaultTemperature = 1.0f,
        accelerators = accelerators,
        supportThinking = true,
        supportSpeculativeDecoding = true,
      ),
  )
}

fun companionPromptForTurn(): String {
  return "The images are chronological front-camera snapshots captured while I spoke. Listen to the audio and respond naturally as my on-device companion."
}

fun enableCompanionThinkingByDefault(model: Model) {
  model.configValues =
    model.configValues.toMutableMap().also { values ->
      values[ConfigKeys.ENABLE_THINKING.label] = true
    }
}
