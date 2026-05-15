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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelDownloadStatus
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
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

fun companionHoldGestureKey(enabled: Boolean, recording: Boolean): Boolean = enabled

fun companionHoldButtonLabel(recording: Boolean): String =
  if (recording) "Release to send" else "Hold to talk"

fun companionHoldButtonDiameterDp(recording: Boolean): Int = 96

fun companionVisibleResponseText(messages: List<ChatMessage>): String? =
  messages.filterIsInstance<ChatMessageText>().lastOrNull { it.side == ChatSide.AGENT }?.content

fun trimCompanionMessages(messages: List<ChatMessage>): List<ChatMessage> =
  messages.takeLast(COMPANION_MAX_HISTORY_MESSAGES)

fun shouldClearCompanionVisibleMessagesBeforeTurn(messages: List<ChatMessage>): Boolean =
  messages.isNotEmpty()

fun companionAttentionGlowAlpha(recording: Boolean, amplitude: Int): Float {
  if (!recording) {
    return 0f
  }
  val normalizedAmplitude = (amplitude / 32767f).coerceIn(0f, 1f)
  return 0.24f + normalizedAmplitude * 0.14f
}

fun companionListeningBorderAlpha(recording: Boolean, pulse: Float): Float {
  if (!recording) {
    return 0f
  }
  return 0.36f + pulse.coerceIn(0f, 1f) * 0.34f
}

@Composable
fun CompanionScreen(
  model: Model,
  downloadStatus: ModelDownloadStatus?,
  initializationStatus: ModelInitializationStatus?,
  viewModel: CompanionViewModel,
  onRetryDownload: () -> Unit,
  onRetryInitialize: () -> Unit,
  onReset: () -> Unit,
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
  val glowAlpha =
    companionAttentionGlowAlpha(recording = captureUiState.recording, amplitude = captureUiState.amplitude)
  val borderTransition = rememberInfiniteTransition(label = "companionListeningBorder")
  val borderPulse by
    borderTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "companionListeningBorderPulse",
    )
  val borderAlpha =
    companionListeningBorderAlpha(recording = captureUiState.recording, pulse = borderPulse)

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
        if (captureUiState.recording) {
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
              responseText != null -> responseText
              responding -> "Thinking..."
              else -> "Hey, tell me about your day."
            },
          recording = false,
          responding = responding,
          amplitude = captureUiState.amplitude,
          attentionGlowAlpha = glowAlpha,
          modifier = Modifier.fillMaxSize(),
        )
        IconButton(
          onClick = onReset,
          enabled = !responding && !captureUiState.recording,
          modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Reset companion")
        }
        if (borderAlpha > 0f) {
          ListeningCardGlowBorder(alpha = borderAlpha, modifier = Modifier.matchParentSize())
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
            viewModel.startCapture()
          }
        },
        onHoldEnd = { viewModel.finishCaptureAndSend(model = model) },
      )
    }
  }
}

@Composable
private fun ListeningCardGlowBorder(alpha: Float, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier.drawWithCache {
        val cornerRadius = 28.dp.toPx()
        val borderWidth = 2.5.dp.toPx()
        val glowWidth = 15.dp.toPx()
        val glowBrush =
          Brush.linearGradient(
            colors =
              listOf(
                Color(0xFFFFF4B8).copy(alpha = alpha),
                Color(0xFFFFC83D).copy(alpha = alpha * 0.9f),
                Color(0xFFFFE08A).copy(alpha = alpha),
              ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
          )
        onDrawBehind {
          drawRoundRect(
            brush = glowBrush,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = glowWidth),
            blendMode = BlendMode.Plus,
          )
          drawRoundRect(
            brush = glowBrush,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = borderWidth),
          )
        }
      }
  )
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
        Text("Model downloaded", color = MaterialTheme.colorScheme.onSurfaceVariant)
      ModelDownloadStatusType.FAILED -> {
        Text(downloadStatus.errorMessage.ifEmpty { "Model download failed" })
        OutlinedButton(onClick = onRetryDownload) { Text("Retry download") }
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
        Text("Downloading model...", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      else -> Text("Preparing model download...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      when (initializationStatus?.status) {
        ModelInitializationStatusType.INITIALIZED ->
          Text("Ready", color = MaterialTheme.colorScheme.primary)
        ModelInitializationStatusType.ERROR -> {
          Text(initializationStatus.error.ifEmpty { "Model initialization failed" })
          OutlinedButton(onClick = onRetryInitialize) { Text("Retry initialize") }
        }
        else -> Text("Initializing...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
