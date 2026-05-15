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

import android.Manifest
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatus
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionScreenStateTest {
  @Test
  fun startupPermissionsIncludeAudioAndCamera() {
    assertEquals(
      listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA),
      companionStartupPermissions(),
    )
  }

  @Test
  fun interactionIsHiddenUntilModelDownloadedAndInitialized() {
    assertFalse(
      shouldShowCompanionInteraction(
        downloadStatus = ModelDownloadStatus(ModelDownloadStatusType.IN_PROGRESS),
        initializationStatus = null,
      )
    )
    assertFalse(
      shouldShowCompanionInteraction(
        downloadStatus = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED),
        initializationStatus = ModelInitializationStatus(ModelInitializationStatusType.INITIALIZING),
      )
    )

    assertTrue(
      shouldShowCompanionInteraction(
        downloadStatus = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED),
        initializationStatus = ModelInitializationStatus(ModelInitializationStatusType.INITIALIZED),
      )
    )
  }

  @Test
  fun holdGestureKeyDoesNotChangeWhenRecordingStarts() {
    assertEquals(
      companionHoldGestureKey(enabled = true, recording = false),
      companionHoldGestureKey(enabled = true, recording = true),
    )
  }

  @Test
  fun holdButtonTextReflectsRecordingState() {
    assertEquals("Hold to talk", companionHoldButtonLabel(recording = false))
    assertEquals("Release to send", companionHoldButtonLabel(recording = true))
  }

  @Test
  fun holdButtonSizeDoesNotChangeWhenRecordingStarts() {
    assertEquals(
      companionHoldButtonDiameterDp(recording = false),
      companionHoldButtonDiameterDp(recording = true),
    )
  }

  @Test
  fun attentionGlowOnlyAppearsSubtlyWhileRecording() {
    assertEquals(0f, companionAttentionGlowAlpha(recording = false, amplitude = 32767), 0.001f)

    val quiet = companionAttentionGlowAlpha(recording = true, amplitude = 0)
    val loud = companionAttentionGlowAlpha(recording = true, amplitude = 32767)

    assertTrue(quiet > 0f)
    assertTrue(loud > quiet)
    assertTrue(loud >= 0.34f)
    assertTrue(loud <= 0.42f)
  }

  @Test
  fun listeningBorderPulsesOnlyWhileRecording() {
    assertEquals(0f, companionListeningBorderAlpha(recording = false, pulse = 1f), 0.001f)

    val dim = companionListeningBorderAlpha(recording = true, pulse = 0f)
    val bright = companionListeningBorderAlpha(recording = true, pulse = 1f)

    assertTrue(dim > 0f)
    assertTrue(bright > dim)
  }

  @Test
  fun visibleResponseUsesOnlyLatestAgentText() {
    val messages =
      listOf(
        ChatMessageText(content = "old", side = ChatSide.AGENT),
        ChatMessageText(content = "user", side = ChatSide.USER),
        ChatMessageText(content = "new", side = ChatSide.AGENT),
      )

    assertEquals("new", companionVisibleResponseText(messages))
  }

  @Test
  fun existingVisibleMessagesAreClearedBeforeNewTurn() {
    val messages = listOf(ChatMessageText(content = "previous", side = ChatSide.AGENT))

    assertTrue(shouldClearCompanionVisibleMessagesBeforeTurn(messages))
    assertFalse(shouldClearCompanionVisibleMessagesBeforeTurn(emptyList()))
  }

  @Test
  fun companionHistoryKeepsMostRecentTwentyMessages() {
    val messages =
      (0 until 25).map { index -> ChatMessageText(content = "$index", side = ChatSide.AGENT) }

    val trimmed = trimCompanionMessages(messages)

    assertEquals(20, trimmed.size)
    assertEquals("5", (trimmed.first() as ChatMessageText).content)
    assertEquals("24", (trimmed.last() as ChatMessageText).content)
  }
}
