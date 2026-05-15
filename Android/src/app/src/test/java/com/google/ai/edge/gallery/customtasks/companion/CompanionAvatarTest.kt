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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionAvatarTest {
  @Test
  fun duckAvatarAssetAndAttributionStayStable() {
    assertEquals("models/rubber_duck.glb", COMPANION_AVATAR_ASSET_PATH)
    assertTrue(COMPANION_AVATAR_ATTRIBUTION.contains("J-Toastie"))
    assertTrue(COMPANION_AVATAR_ATTRIBUTION.contains("CC BY"))
  }

  @Test
  fun listeningMotionUsesMicAmplitude() {
    val quiet = companionAvatarMotion(recording = true, responding = false, amplitude = 0)
    val loud = companionAvatarMotion(recording = true, responding = false, amplitude = 32767)

    assertTrue(loud.bobHeight > quiet.bobHeight)
    assertTrue(loud.scale > quiet.scale)
  }

  @Test
  fun duckFacesCameraAtRest() {
    val motion = companionAvatarMotion(recording = false, responding = false, amplitude = 0)

    assertEquals(0f, companionAvatarYawDegrees(motion = motion, wave = 0f), 0.001f)
  }

  @Test
  fun respondingMotionIsMoreAnimatedThanIdle() {
    val idle = companionAvatarMotion(recording = false, responding = false, amplitude = 0)
    val responding = companionAvatarMotion(recording = false, responding = true, amplitude = 0)

    assertTrue(responding.bobHeight > idle.bobHeight)
    assertTrue(responding.wobbleDegrees > idle.wobbleDegrees)
  }
}
