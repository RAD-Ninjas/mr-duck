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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlin.math.PI
import kotlin.math.sin

const val COMPANION_AVATAR_ASSET_PATH = "models/rubber_duck.glb"

data class CompanionAvatarMotion(
  val scale: Float,
  val bobHeight: Float,
  val wobbleDegrees: Float,
  val cycleMillis: Int,
)

fun companionAvatarMotion(
  recording: Boolean,
  responding: Boolean,
  amplitude: Int,
): CompanionAvatarMotion {
  val normalizedAmplitude = (amplitude / 32767f).coerceIn(0f, 1f)
  return when {
    recording ->
      CompanionAvatarMotion(
        scale = 1f + normalizedAmplitude * 0.08f,
        bobHeight = 0.03f + normalizedAmplitude * 0.08f,
        wobbleDegrees = 6f + normalizedAmplitude * 8f,
        cycleMillis = 520,
      )
    responding ->
      CompanionAvatarMotion(
        scale = 1.04f,
        bobHeight = 0.07f,
        wobbleDegrees = 10f,
        cycleMillis = 760,
      )
    else ->
      CompanionAvatarMotion(
        scale = 1f,
        bobHeight = 0.025f,
        wobbleDegrees = 3f,
        cycleMillis = 2400,
      )
  }
}

fun companionAvatarYawDegrees(motion: CompanionAvatarMotion, wave: Float): Float =
  motion.wobbleDegrees * wave

fun companionThinkingWaveAlpha(index: Int, phase: Float): Float {
  val shiftedPhase = phase - index * 0.08f
  val wave = (sin(shiftedPhase * 2f * PI.toFloat()) + 1f) / 2f
  return 0.45f + wave * 0.55f
}

@Composable
fun CompanionAvatar(
  text: String,
  recording: Boolean,
  responding: Boolean,
  amplitude: Int,
  thinking: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val motion = companionAvatarMotion(recording = recording, responding = responding, amplitude = amplitude)
  val transition = rememberInfiniteTransition(label = "companionAvatarMotion")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = motion.cycleMillis, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "companionAvatarPhase",
    )
  val wave = sin(phase * 2f * PI.toFloat())
  val engine = rememberEngine()
  val modelLoader = rememberModelLoader(engine)
  val modelInstance = rememberModelInstance(modelLoader, COMPANION_AVATAR_ASSET_PATH)

  Box(modifier = modifier.fillMaxSize()) {
    Scene(
      modifier = Modifier.fillMaxSize(),
      engine = engine,
      modelLoader = modelLoader,
      cameraManipulator =
        rememberCameraManipulator(
          orbitHomePosition = Position(0f, 0.35f, 1.45f),
          targetPosition = Position(0f, 0.25f, 0f),
        ),
    ) {
      modelInstance?.let { instance ->
        ModelNode(
          modelInstance = instance,
          scaleToUnits = 0.72f,
          centerOrigin = Position(0f, 0f, 0f),
          position = Position(0f, 0.08f + motion.bobHeight * wave, 0f),
          rotation =
            Rotation(
              y = companionAvatarYawDegrees(motion = motion, wave = wave),
              z = motion.wobbleDegrees * 0.35f * wave,
            ),
          scale = Scale(motion.scale),
        )
      }
    }

    if (modelInstance == null) {
      CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }

    AvatarSpeechPanel(
      text = text,
      thinking = thinking,
      phase = phase,
      modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).heightIn(max = 180.dp),
    )
  }
}

@Composable
private fun AvatarSpeechPanel(
  text: String,
  thinking: Boolean,
  phase: Float,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(28.dp))
        .background(
          Brush.verticalGradient(
            colors =
              listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
              )
          )
        )
        .padding(horizontal = 18.dp, vertical = 14.dp)
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle =
      MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Medium,
      )
    if (thinking) {
      Text(
        text =
          buildAnnotatedString {
            text.forEachIndexed { index, char ->
              withStyle(SpanStyle(color = textColor.copy(alpha = companionThinkingWaveAlpha(index, phase)))) {
                append(char)
              }
            }
          },
        style = textStyle,
        textAlign = TextAlign.Center,
      )
    } else {
      Text(
        text = text,
        style = textStyle,
        color = textColor,
        textAlign = TextAlign.Center,
      )
    }
  }
}
