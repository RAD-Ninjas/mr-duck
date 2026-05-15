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
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.Config
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.data.convertValueToTargetType
import com.google.ai.edge.gallery.ui.common.ConfigDialog
import com.google.ai.edge.gallery.ui.common.LiveCameraView
import com.google.ai.edge.gallery.ui.common.chat.ChatMessage
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatus
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType

const val COMPANION_MAX_HISTORY_MESSAGES = 20

fun companionStartupPermissions(): List<String> =
  listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

fun shouldShowCompanionInteraction(
  downloadStatus: ModelDownloadStatus?,
  initializationStatus: ModelInitializationStatus?,
): Boolean =
  downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED &&
    initializationStatus?.status == ModelInitializationStatusType.INITIALIZED

fun shouldShowCompanionSettings(
  downloadStatus: ModelDownloadStatus?,
  initializationStatus: ModelInitializationStatus?,
  inProgress: Boolean,
  recording: Boolean,
  hasConfigs: Boolean,
): Boolean =
  hasConfigs &&
    !inProgress &&
    !recording &&
    shouldShowCompanionInteraction(downloadStatus = downloadStatus, initializationStatus = initializationStatus)

fun companionConfigNeedsReinitialization(
  configs: List<Config>,
  oldValues: Map<String, Any>,
  newValues: Map<String, Any>,
): Boolean =
  configs.any { config ->
    if (!config.needReinitialization) {
      return@any false
    }
    val key = config.key.label
    val oldValue = oldValues[key]?.let { convertValueToTargetType(it, config.valueType) }
    val newValue = newValues[key]?.let { convertValueToTargetType(it, config.valueType) }
    oldValue != newValue
  }

fun shouldCaptureCompanionCameraFrame(recording: Boolean, imageContextEnabled: Boolean): Boolean =
  recording && imageContextEnabled

fun <T> companionFramesForTurn(frames: List<T>, imageContextEnabled: Boolean): List<T> =
  if (imageContextEnabled) frames else emptyList()

fun companionHoldGestureKey(enabled: Boolean, recording: Boolean): Boolean = enabled

fun companionHoldButtonLabel(recording: Boolean): String =
  if (recording) "Release. The duck has notes." else "Hold to tell the duck"

fun companionIdleGreeting(): String = "Tell the tiny duck what's on your mind."

fun companionRespondingLabel(): String = "The duck is forming an opinion..."

fun companionResetLabel(): String = "Start over"

fun companionSettingsLabel(): String = "Duck controls"

fun companionDuckGraphicsAttribution(): String =
  "Duck graphics: Rubber Duck by J-Toastie, CC BY via Poly Pizza"

fun companionDownloadStatusLabel(downloadStatus: ModelDownloadStatus?): String =
  when (downloadStatus?.status) {
    ModelDownloadStatusType.SUCCEEDED -> "Tiny brain installed. Ready to quack away."
    ModelDownloadStatusType.FAILED -> "The duck got distracted."
    else -> "Hatching tiny thoughts..."
  }

fun companionDownloadRetryLabel(): String = "Try again"

fun companionInitializationStatusLabel(initializationStatus: ModelInitializationStatus?): String =
  when (initializationStatus?.status) {
    ModelInitializationStatusType.INITIALIZED -> "Ready to judge kindly."
    ModelInitializationStatusType.ERROR -> "The duck fell over."
    else -> "Fluffing the braincells..."
  }

fun companionInitializeRetryLabel(): String = "Prop the duck up"

fun companionEmptyCaptureMessage(): String = "I heard nothing. Tiny tragedy."

fun companionResponseErrorMessage(): String = "My braincell slipped. Again, but cuter."

fun companionHoldButtonDiameterDp(recording: Boolean): Int = 96

fun companionVisibleResponseText(messages: List<ChatMessage>): String? {
  val latestTextMessage = messages.lastOrNull() as? ChatMessageText ?: return null
  return latestTextMessage.content.takeIf { latestTextMessage.side == ChatSide.AGENT }
}

fun trimCompanionMessages(messages: List<ChatMessage>): List<ChatMessage> =
  messages.takeLast(COMPANION_MAX_HISTORY_MESSAGES)

fun shouldClearCompanionVisibleMessagesBeforeTurn(messages: List<ChatMessage>): Boolean =
  false

fun shouldInsertCompanionUserBoundaryBeforeTurn(messages: List<ChatMessage>): Boolean = true

fun companionUserBoundaryMessage(): ChatMessageText =
  ChatMessageText(content = COMPANION_HISTORY_USER_PLACEHOLDER, side = ChatSide.USER)

@Composable
fun CompanionScreen(
  model: Model,
  downloadStatus: ModelDownloadStatus?,
  initializationStatus: ModelInitializationStatus?,
  viewModel: CompanionViewModel,
  onRetryDownload: () -> Unit,
  onRetryInitialize: () -> Unit,
  onReset: () -> Unit,
  onConfigChanged: (oldValues: Map<String, Any>, newValues: Map<String, Any>) -> Unit,
) {
  val context = LocalContext.current
  val captureUiState by viewModel.captureUiState.collectAsState()
  val chatUiState by viewModel.uiState.collectAsState()
  var audioPermissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var startupPermissionsRequested by remember { mutableStateOf(false) }
  var showConfigDialog by remember { mutableStateOf(false) }
  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      audioPermissionGranted = granted
    }
  val startupPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
      audioPermissionGranted =
        results[Manifest.permission.RECORD_AUDIO] == true ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

  LaunchedEffect(Unit) {
    if (startupPermissionsRequested) {
      return@LaunchedEffect
    }
    val missingPermissions =
      companionStartupPermissions()
        .filter { permission ->
          ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        .toTypedArray()
    if (missingPermissions.isNotEmpty()) {
      startupPermissionsRequested = true
      startupPermissionLauncher.launch(missingPermissions)
    }
  }

  val responding = chatUiState.inProgress || chatUiState.preparing
  val interactionVisible = shouldShowCompanionInteraction(downloadStatus, initializationStatus)
  val ready = interactionVisible && !responding
  val messages = chatUiState.messagesByModel[model.name] ?: emptyList()
  val responseText = companionVisibleResponseText(messages)
  val visibleResponseText = responseText?.takeIf { it.isNotBlank() }
  val imageContextEnabled =
    model.getBooleanConfigValue(COMPANION_ENABLE_IMAGE_CONTEXT_CONFIG_KEY, defaultValue = true)

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    if (!interactionVisible) {
      CompanionSetupScreen(
        downloadStatus = downloadStatus,
        initializationStatus = initializationStatus,
        error = captureUiState.error,
        onRetryDownload = onRetryDownload,
        onRetryInitialize = onRetryInitialize,
      )
      return@Surface
    }

    Column(
      modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .weight(1f)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
      ) {
        if (
          shouldCaptureCompanionCameraFrame(
            recording = captureUiState.recording,
            imageContextEnabled = imageContextEnabled,
          )
        ) {
          LiveCameraView(
            preferredSize = 512,
            outputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888,
            renderPreview = false,
            onBitmap = { bitmap, imageProxy: ImageProxy ->
              try {
                viewModel.onCameraFrame(bitmap)
              } finally {
                imageProxy.close()
              }
            },
          )
        }
        CompanionAvatar(
          text =
            when {
              visibleResponseText != null -> visibleResponseText
              responding -> companionRespondingLabel()
              else -> companionIdleGreeting()
            },
          recording = captureUiState.recording,
          responding = responding,
          amplitude = captureUiState.amplitude,
          thinking = responding && visibleResponseText == null,
          modifier = Modifier.fillMaxSize(),
        )
        if (
          shouldShowCompanionSettings(
            downloadStatus = downloadStatus,
            initializationStatus = initializationStatus,
            inProgress = responding,
            recording = captureUiState.recording,
            hasConfigs = model.configs.isNotEmpty(),
          )
        ) {
          IconButton(
            onClick = { showConfigDialog = true },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
          ) {
            Icon(Icons.Rounded.Tune, contentDescription = companionSettingsLabel())
          }
        }
        Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
          IconButton(
            onClick = onReset,
            enabled = !responding && !captureUiState.recording,
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = companionResetLabel())
          }
        }
      }

      if (captureUiState.error.isNotEmpty()) {
        Text(
          text = captureUiState.error,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
        )
      }

      HoldToTalkButton(
        enabled = ready,
        recording = captureUiState.recording,
        amplitude = captureUiState.amplitude,
        onHoldStart = {
          if (!audioPermissionGranted) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          } else {
            viewModel.startCapture(includeImages = imageContextEnabled)
          }
        },
        onHoldEnd = {
          viewModel.finishCaptureAndSend(model = model, includeImages = imageContextEnabled)
        },
      )
      Text(
        text = companionDuckGraphicsAttribution(),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f),
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
      )
    }
  }

  if (showConfigDialog) {
    ConfigDialog(
      title = companionSettingsLabel(),
      configs = model.configs,
      initialValues = model.configValues,
      onDismissed = { showConfigDialog = false },
      onOk = { values, _, _ ->
        showConfigDialog = false
        onConfigChanged(model.configValues, values)
      },
    )
  }
}

@Composable
private fun CompanionSetupScreen(
  downloadStatus: ModelDownloadStatus?,
  initializationStatus: ModelInitializationStatus?,
  error: String,
  onRetryDownload: () -> Unit,
  onRetryInitialize: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    StatusPanel(
      downloadStatus = downloadStatus,
      initializationStatus = initializationStatus,
      error = error,
      onRetryDownload = onRetryDownload,
      onRetryInitialize = onRetryInitialize,
    )
  }
}

@Composable
private fun StatusPanel(
  downloadStatus: ModelDownloadStatus?,
  initializationStatus: ModelInitializationStatus?,
  error: String,
  onRetryDownload: () -> Unit,
  onRetryInitialize: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    when (downloadStatus?.status) {
      ModelDownloadStatusType.SUCCEEDED ->
        Text(companionDownloadStatusLabel(downloadStatus), color = MaterialTheme.colorScheme.onSurfaceVariant)
      ModelDownloadStatusType.FAILED -> {
        Text(companionDownloadStatusLabel(downloadStatus))
        OutlinedButton(onClick = onRetryDownload) { Text(companionDownloadRetryLabel()) }
      }
      ModelDownloadStatusType.IN_PROGRESS,
      ModelDownloadStatusType.UNZIPPING -> {
        LinearProgressIndicator(
          progress = {
            val total = downloadStatus.totalBytes.takeIf { it > 0 } ?: return@LinearProgressIndicator 0f
            downloadStatus.receivedBytes.toFloat() / total.toFloat()
          },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(companionDownloadStatusLabel(downloadStatus), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      else -> Text(companionDownloadStatusLabel(downloadStatus), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      when (initializationStatus?.status) {
        ModelInitializationStatusType.INITIALIZED ->
          Text(companionInitializationStatusLabel(initializationStatus), color = MaterialTheme.colorScheme.primary)
        ModelInitializationStatusType.ERROR -> {
          Text(companionInitializationStatusLabel(initializationStatus))
          OutlinedButton(onClick = onRetryInitialize) { Text(companionInitializeRetryLabel()) }
        }
        else -> Text(companionInitializationStatusLabel(initializationStatus), color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }

    if (error.isNotEmpty()) {
      Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
  }
}

@Composable
private fun HoldToTalkButton(
  enabled: Boolean,
  recording: Boolean,
  amplitude: Int,
  onHoldStart: () -> Unit,
  onHoldEnd: () -> Unit,
) {
  val buttonSize = companionHoldButtonDiameterDp(recording = recording).dp
  val alpha = if (enabled || recording) 1f else 0.45f
  val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
  val contentColor = MaterialTheme.colorScheme.onSurface
  Surface(
    modifier =
      Modifier.size(buttonSize)
        .pointerInput(companionHoldGestureKey(enabled = enabled, recording = recording)) {
          detectTapGestures(
            onPress = {
              if (!enabled) {
                return@detectTapGestures
              }
              onHoldStart()
              try {
                awaitRelease()
              } finally {
                onHoldEnd()
              }
            }
          )
        },
    shape = CircleShape,
    color = containerColor.copy(alpha = alpha),
    contentColor = contentColor,
    tonalElevation = 2.dp,
    shadowElevation = if (enabled || recording) 3.dp else 0.dp,
  ) {
    Box(
      modifier = Modifier.fillMaxSize().padding(22.dp),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
  }
  Text(
    text = companionHoldButtonLabel(recording = recording),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
