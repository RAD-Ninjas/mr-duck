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
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.NumberSliderConfig
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.ValueType
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
  fun settingsButtonShowsOnlyWhenModelReadyAndIdle() {
    val readyDownload = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)
    val readyInitialization = ModelInitializationStatus(ModelInitializationStatusType.INITIALIZED)

    assertTrue(
      shouldShowCompanionSettings(
        downloadStatus = readyDownload,
        initializationStatus = readyInitialization,
        inProgress = false,
        recording = false,
        hasConfigs = true,
      )
    )
    assertFalse(
      shouldShowCompanionSettings(
        downloadStatus = readyDownload,
        initializationStatus = readyInitialization,
        inProgress = true,
        recording = false,
        hasConfigs = true,
      )
    )
    assertFalse(
      shouldShowCompanionSettings(
        downloadStatus = readyDownload,
        initializationStatus = readyInitialization,
        inProgress = false,
        recording = true,
        hasConfigs = true,
      )
    )
    assertFalse(
      shouldShowCompanionSettings(
        downloadStatus = ModelDownloadStatus(ModelDownloadStatusType.IN_PROGRESS),
        initializationStatus = readyInitialization,
        inProgress = false,
        recording = false,
        hasConfigs = true,
      )
    )
    assertFalse(
      shouldShowCompanionSettings(
        downloadStatus = readyDownload,
        initializationStatus = readyInitialization,
        inProgress = false,
        recording = false,
        hasConfigs = false,
      )
    )
  }

  @Test
  fun configReinitializationOnlyWhenChangedConfigRequiresIt() {
    val reinitConfig =
      NumberSliderConfig(
        key = ConfigKeys.MAX_TOKENS,
        sliderMin = 1f,
        sliderMax = 10f,
        defaultValue = 4f,
        valueType = ValueType.INT,
        needReinitialization = true,
      )
    val liveConfig =
      NumberSliderConfig(
        key = ConfigKeys.TEMPERATURE,
        sliderMin = 0f,
        sliderMax = 2f,
        defaultValue = 1f,
        valueType = ValueType.FLOAT,
        needReinitialization = false,
      )

    assertTrue(
      companionConfigNeedsReinitialization(
        configs = listOf(reinitConfig, liveConfig),
        oldValues = mapOf("Max tokens" to 4, "Temperature" to 1f),
        newValues = mapOf("Max tokens" to 8, "Temperature" to 1f),
      )
    )
    assertFalse(
      companionConfigNeedsReinitialization(
        configs = listOf(reinitConfig, liveConfig),
        oldValues = mapOf("Max tokens" to 4, "Temperature" to 1f),
        newValues = mapOf("Max tokens" to 4, "Temperature" to 1.2f),
      )
    )
  }

  @Test
  fun imageContextToggleControlsCameraCaptureAndTurnFrames() {
    assertTrue(shouldCaptureCompanionCameraFrame(recording = true, imageContextEnabled = true))
    assertFalse(shouldCaptureCompanionCameraFrame(recording = true, imageContextEnabled = false))
    assertFalse(shouldCaptureCompanionCameraFrame(recording = false, imageContextEnabled = true))

    assertEquals(listOf("frame"), companionFramesForTurn(listOf("frame"), imageContextEnabled = true))
    assertEquals(emptyList<String>(), companionFramesForTurn(listOf("frame"), imageContextEnabled = false))
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
    assertEquals("Hold to tell the duck", companionHoldButtonLabel(recording = false))
    assertEquals("Release. The duck has notes.", companionHoldButtonLabel(recording = true))
  }

  @Test
  fun idleAndBusyLabelsStayCuteAndWitty() {
    assertEquals("Tell the tiny duck what's on your mind.", companionIdleGreeting())
    assertEquals("The duck is forming an opinion...", companionRespondingLabel())
    assertEquals("Start over", companionResetLabel())
  }

  @Test
  fun duckGraphicsAttributionUsesFooterWording() {
    assertEquals(
      "Duck graphics: Rubber Duck by J-Toastie, CC BY via Poly Pizza",
      companionDuckGraphicsAttribution(),
    )
  }

  @Test
  fun setupLabelsAvoidTechnicalModelLanguage() {
    assertEquals("Hatching tiny thoughts...", companionDownloadStatusLabel(null))
    assertEquals(
      "Tiny brain installed. Ready to quack away.",
      companionDownloadStatusLabel(ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)),
    )
    assertEquals(
      "The duck got distracted.",
      companionDownloadStatusLabel(ModelDownloadStatus(ModelDownloadStatusType.FAILED)),
    )
    assertEquals(
      "Hatching tiny thoughts...",
      companionDownloadStatusLabel(ModelDownloadStatus(ModelDownloadStatusType.IN_PROGRESS)),
    )
    assertEquals(
      "Hatching tiny thoughts...",
      companionDownloadStatusLabel(ModelDownloadStatus(ModelDownloadStatusType.UNZIPPING)),
    )
    assertEquals("Ready to judge kindly.", companionInitializationStatusLabel(ModelInitializationStatus(ModelInitializationStatusType.INITIALIZED)))
    assertEquals("The duck fell over.", companionInitializationStatusLabel(ModelInitializationStatus(ModelInitializationStatusType.ERROR)))
    assertEquals("Fluffing the braincells...", companionInitializationStatusLabel(null))
    assertEquals("Try again", companionDownloadRetryLabel())
    assertEquals("Prop the duck up", companionInitializeRetryLabel())
  }

  @Test
  fun captureErrorsAreCuteAndWitty() {
    assertEquals("I heard nothing. Tiny tragedy.", companionEmptyCaptureMessage())
    assertEquals("My braincell slipped. Again, but cuter.", companionResponseErrorMessage())
  }

  @Test
  fun holdButtonSizeDoesNotChangeWhenRecordingStarts() {
    assertEquals(
      companionHoldButtonDiameterDp(recording = false),
      companionHoldButtonDiameterDp(recording = true),
    )
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
  fun visibleResponseIgnoresPreviousAgentTextWhenLatestMessageIsNotAgentText() {
    val messages =
      listOf(
        ChatMessageText(content = "old", side = ChatSide.AGENT),
        ChatMessageText(content = "user", side = ChatSide.USER),
      )

    assertEquals(null, companionVisibleResponseText(messages))
  }

  @Test
  fun existingMessagesAreRetainedBeforeNewTurnForHiddenHistory() {
    val messages = listOf(ChatMessageText(content = "previous", side = ChatSide.AGENT))

    assertFalse(shouldClearCompanionVisibleMessagesBeforeTurn(messages))
    assertFalse(shouldClearCompanionVisibleMessagesBeforeTurn(emptyList()))
  }

  @Test
  fun userBoundaryIsInsertedForEveryCapturedTurn() {
    assertTrue(shouldInsertCompanionUserBoundaryBeforeTurn(emptyList()))
    assertTrue(
      shouldInsertCompanionUserBoundaryBeforeTurn(
        listOf(ChatMessageText(content = "previous", side = ChatSide.AGENT))
      )
    )
    assertTrue(
      shouldInsertCompanionUserBoundaryBeforeTurn(
        listOf(ChatMessageText(content = "hidden user turn", side = ChatSide.USER))
      )
    )

    val boundary = companionUserBoundaryMessage()
    assertEquals(ChatSide.USER, boundary.side)
    assertEquals(COMPANION_HISTORY_USER_PLACEHOLDER, boundary.content)
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
