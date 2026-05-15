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
import com.google.ai.edge.gallery.data.BooleanSwitchConfig
import com.google.ai.edge.gallery.data.ConfigKey
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelCapability
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.createLlmChatConfigs

const val COMPANION_TASK_ID = "llm_companion"
const val COMPANION_TASK_LABEL = "Mr. Duck"
val COMPANION_ENABLE_IMAGE_CONTEXT_CONFIG_KEY =
  ConfigKey("companion_enable_image_context", "Use camera context")

const val COMPANION_SYSTEM_PROMPT =
  "You are Mr. Duck: a cute tiny rubber duck perched on the user's desk with deadpan, " +
    "dry wit and suspiciously strong opinions for a bath toy. You are soft around the edges, " +
    "sharp on timing, and not a generic AI assistant. Answer questions directly and " +
    "finish the user's task first; personality must not replace the answer. Do not label, " +
    "caption, enumerate, or narrate inputs. Never start with Image, Audio, Snapshot, or " +
    "similar input labels. Use visual comments only when relevant. Reply with understatement, " +
    "observational humor, gentle roasts, and small adorable images like tiny wings, one " +
    "heroic braincell, desk puddles, and pond paperwork. Be warm and cute, but not bubbly, " +
    "peppy, motivational, or customer-service polished. A tiny emoji is allowed as dry " +
    "punctuation, never as confetti. You may comment on visible, non-sensitive appearance " +
    "details such as clothing, hair, expression, posture, lighting, desk setup, or general " +
    "vibe. Do not identify people or infer sensitive traits. Keep replies short unless the " +
    "user asks for more."

fun createCompanionModel(): Model {
  val accelerators = listOf(Accelerator.GPU, Accelerator.CPU)
  return Model(
    name = "Gemma-4-E2B-it",
    displayName = "Mr. Duck's Brain",
    info = "A private duck brain that listens, looks around, and has opinions.",
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
    llmMaxToken = COMPANION_CONTEXT_WINDOW_TOKENS,
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
        defaultMaxToken = COMPANION_CONTEXT_WINDOW_TOKENS,
        defaultMaxContextLength = COMPANION_CONTEXT_WINDOW_TOKENS,
        defaultTopK = 64,
        defaultTopP = 0.95f,
        defaultTemperature = 1.0f,
        accelerators = accelerators,
        supportThinking = true,
        supportSpeculativeDecoding = true,
      ) +
        BooleanSwitchConfig(
          key = COMPANION_ENABLE_IMAGE_CONTEXT_CONFIG_KEY,
          defaultValue = true,
          needReinitialization = false,
        ),
  )
}

fun companionPromptForTurn(): String {
  return "The attached audio is the user's current message. Silently understand the speech " +
    "and answer that spoken request directly. The routing text here is not the user's " +
    "request. The attached camera frame is private context from your desk-duck " +
    "perch and private visual context; use it only if relevant, and do not caption or " +
    "list the inputs. Stay Mr. Duck: cute, witty, deadpan, observant, useful, and not sugary."
}

data class CompanionFrameDebugInfo(val width: Int, val height: Int)

fun companionTurnDebugLog(
  prompt: String,
  frameSizes: List<CompanionFrameDebugInfo>,
  audioPcmBytes: Int,
  audioWavBytes: Int,
  sampleRate: Int,
  hiddenHistoryTurns: Int,
  resetBeforeTurn: Boolean,
): String {
  val firstFrame = frameSizes.firstOrNull()?.let { "${it.width}x${it.height}" } ?: "none"
  return "LiteRT turn payload: order=image,audio,text; imageCount=${frameSizes.size}; " +
    "firstImage=$firstFrame; audioPcmBytes=$audioPcmBytes; audioWavBytes=$audioWavBytes; " +
    "sampleRate=$sampleRate; hiddenTurns=$hiddenHistoryTurns; " +
    "resetBeforeTurn=$resetBeforeTurn; prompt=\"$prompt\""
}

fun enableCompanionThinkingByDefault(model: Model) {
  model.configValues =
    model.configValues.toMutableMap().also { values ->
      values[ConfigKeys.ENABLE_THINKING.label] = true
    }
}
