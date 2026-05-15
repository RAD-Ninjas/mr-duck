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

import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.MAX_IMAGE_COUNT
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import com.google.ai.edge.gallery.data.SystemPromptRepository
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.proto.UserData
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageLoading
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModelBase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val FRAME_SAMPLE_INTERVAL_MS = 3000L

data class CompanionCaptureUiState(
  val recording: Boolean = false,
  val sampledFrameCount: Int = 0,
  val amplitude: Int = 0,
  val error: String = "",
)

@HiltViewModel
class CompanionViewModel
@Inject
constructor(
  systemPromptRepository: SystemPromptRepository,
  userDataDataStore: DataStore<UserData>,
) : LlmChatViewModelBase(systemPromptRepository, userDataDataStore, null) {
  private val audioRecorder = CompanionAudioRecorder()
  private val frameSampler =
    CompanionFrameSampler<Bitmap>(
      sampleEveryMs = FRAME_SAMPLE_INTERVAL_MS,
      maxFrames = MAX_IMAGE_COUNT,
    )

  private val _captureUiState = MutableStateFlow(CompanionCaptureUiState())
  val captureUiState = _captureUiState.asStateFlow()

  fun startCapture() {
    if (_captureUiState.value.recording) {
      return
    }
    frameSampler.clear()
    _captureUiState.value = CompanionCaptureUiState(recording = true)
    audioRecorder.start(viewModelScope) { amplitude ->
      _captureUiState.update { it.copy(amplitude = amplitude) }
    }
  }

  fun onCameraFrame(bitmap: Bitmap) {
    if (!_captureUiState.value.recording) {
      return
    }
    if (!frameSampler.canSample()) {
      return
    }
    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    if (frameSampler.maybeSample(copy)) {
      _captureUiState.update { it.copy(sampledFrameCount = frameSampler.frames.size) }
    }
  }

  fun finishCaptureAndSend(model: Model) {
    if (!_captureUiState.value.recording) {
      return
    }
    _captureUiState.update { it.copy(recording = false, amplitude = 0) }
    viewModelScope.launch {
      val audioData = audioRecorder.stop()
      val frames = frameSampler.frames
      if (audioData.isEmpty() && frames.isEmpty()) {
        _captureUiState.update { it.copy(error = "I did not catch audio or camera frames.") }
        return@launch
      }
      _captureUiState.update { it.copy(error = "") }
      val visibleMessages = uiState.value.messagesByModel[model.name] ?: emptyList()
      if (shouldClearCompanionVisibleMessagesBeforeTurn(visibleMessages)) {
        clearAllMessages(model = model)
      }
      generateResponse(
        model = model,
        input = companionPromptForTurn(),
        images = frames,
        audioMessages =
          if (audioData.isEmpty()) {
            emptyList()
          } else {
            listOf(
              ChatMessageAudioClip(
                audioData = audioData,
                sampleRate = SAMPLE_RATE,
                side = ChatSide.USER,
              )
            )
          },
        onError = { message ->
          if (getLastMessage(model = model) is ChatMessageLoading) {
            removeLastMessage(model = model)
          }
          _captureUiState.update { it.copy(error = message) }
        },
        onDone = { trimHistory(model = model) },
        allowThinking = true,
      )
    }
  }

  fun resetCompanionSession(task: Task, model: Model) {
    frameSampler.clear()
    _captureUiState.value = CompanionCaptureUiState()
    resetSession(
      task = task,
      model = model,
      supportImage = true,
      supportAudio = true,
    )
  }

  private fun trimHistory(model: Model) {
    val overflow = (uiState.value.messagesByModel[model.name]?.size ?: 0) - COMPANION_MAX_HISTORY_MESSAGES
    repeat(overflow.coerceAtLeast(0)) { removeMessageAt(model = model, index = 0) }
  }

  override fun onCleared() {
    audioRecorder.release()
    super.onCleared()
  }
}
