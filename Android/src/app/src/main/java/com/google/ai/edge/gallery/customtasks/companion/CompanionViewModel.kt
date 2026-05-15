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
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import com.google.ai.edge.gallery.data.SystemPromptRepository
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.proto.UserData
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageAudioClip
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageLoading
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModelBase
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AGCompanionViewModel"

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
  private val frameSampler = createCompanionFrameSampler<Bitmap>()
  private val conversationHistory = CompanionConversationHistory()

  private val _captureUiState = MutableStateFlow(CompanionCaptureUiState())
  val captureUiState = _captureUiState.asStateFlow()

  fun startCapture(includeImages: Boolean = true) {
    if (_captureUiState.value.recording) {
      return
    }
    if (includeImages) {
      frameSampler.start()
    } else {
      frameSampler.clear()
    }
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

  fun finishCaptureAndSend(model: Model, includeImages: Boolean = true) {
    if (!_captureUiState.value.recording) {
      return
    }
    _captureUiState.update { it.copy(recording = false, amplitude = 0) }
    viewModelScope.launch {
      val audioData = audioRecorder.stop()
      val frames = companionFramesForTurn(frameSampler.frames, imageContextEnabled = includeImages)
      if (audioData.isEmpty() && frames.isEmpty()) {
        _captureUiState.update { it.copy(error = companionEmptyCaptureMessage()) }
        return@launch
      }
      _captureUiState.update { it.copy(error = "") }
      val audioMessage =
        if (audioData.isEmpty()) {
          null
        } else {
          ChatMessageAudioClip(
            audioData = audioData,
            sampleRate = SAMPLE_RATE,
            side = ChatSide.USER,
          )
        }
      val audioWav = audioMessage?.genByteArrayForWav()
      val userHistoryMessage =
        CompanionHistoryMessage.user(
          text = COMPANION_HISTORY_USER_PLACEHOLDER,
          audioWav = audioWav,
          imagePng = frames.firstOrNull()?.toCompanionPngByteArray(),
        )
      val resetBeforeTurn = conversationHistory.trimToFit(userHistoryMessage)
      val turnPrompt = companionPromptForTurn()
      Log.d(
        TAG,
        companionTurnDebugLog(
          prompt = turnPrompt,
          frameSizes = frames.map { frame -> CompanionFrameDebugInfo(frame.width, frame.height) },
          audioPcmBytes = audioData.size,
          audioWavBytes = audioWav?.size ?: 0,
          sampleRate = SAMPLE_RATE,
          hiddenHistoryTurns = conversationHistory.turns.size,
          resetBeforeTurn = resetBeforeTurn,
        ),
      )
      if (resetBeforeTurn) {
        resetRuntimeConversationToHiddenHistory(model = model)
      }
      val visibleMessages = uiState.value.messagesByModel[model.name] ?: emptyList()
      if (shouldInsertCompanionUserBoundaryBeforeTurn(visibleMessages)) {
        addMessage(model = model, message = companionUserBoundaryMessage())
      }
      generateResponse(
        model = model,
        input = turnPrompt,
        images = frames,
        audioMessages = if (audioMessage == null) emptyList() else listOf(audioMessage),
        onError = {
          if (getLastMessage(model = model) is ChatMessageLoading) {
            removeLastMessage(model = model)
          }
          _captureUiState.update { it.copy(error = companionResponseErrorMessage()) }
        },
        onDone = {
          rememberSuccessfulTurn(model = model, userHistoryMessage = userHistoryMessage)
        },
        allowThinking = true,
      )
    }
  }

  fun resetCompanionSession(task: Task, model: Model) {
    frameSampler.clear()
    conversationHistory.clear()
    _captureUiState.value = CompanionCaptureUiState()
    resetSession(
      task = task,
      model = model,
      systemInstruction = Contents.of(COMPANION_SYSTEM_PROMPT),
      supportImage = true,
      supportAudio = true,
    )
  }

  private fun rememberSuccessfulTurn(model: Model, userHistoryMessage: CompanionHistoryMessage) {
    val responseText =
      companionVisibleResponseText(uiState.value.messagesByModel[model.name] ?: emptyList()) ?: return
    val trimmed =
      conversationHistory.addTurn(
        CompanionConversationTurn(
          user = userHistoryMessage,
          agent = CompanionHistoryMessage.agent(text = responseText),
        )
      )
    trimUiMessageCache(model = model)
    if (trimmed) {
      viewModelScope.launch { resetRuntimeConversationToHiddenHistory(model = model) }
    }
  }

  private fun trimUiMessageCache(model: Model) {
    val overflow = (uiState.value.messagesByModel[model.name]?.size ?: 0) - COMPANION_MAX_HISTORY_MESSAGES
    repeat(overflow.coerceAtLeast(0)) { removeMessageAt(model = model, index = 0) }
  }

  private suspend fun resetRuntimeConversationToHiddenHistory(model: Model) {
    setIsResettingSession(true)
    try {
      while (model.instance == null) {
        delay(100)
      }
      model.runtimeHelper.resetConversation(
        model = model,
        supportImage = true,
        supportAudio = true,
        systemInstruction = Contents.of(COMPANION_SYSTEM_PROMPT),
        initialMessages = conversationHistory.toLiteRtMessages(),
      )
    } finally {
      setIsResettingSession(false)
    }
  }

  override fun onCleared() {
    audioRecorder.release()
    super.onCleared()
  }
}
