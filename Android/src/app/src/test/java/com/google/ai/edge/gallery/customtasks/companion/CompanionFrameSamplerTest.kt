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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionFrameSamplerTest {
  @Test
  fun samplesFirstFrameThenOnlyAtConfiguredInterval() {
    val sampler = CompanionFrameSampler<String>(sampleEveryMs = 3000L, maxFrames = 10)

    assertTrue(sampler.maybeSample(frame = "first", nowMs = 0L))
    assertFalse(sampler.maybeSample(frame = "too soon", nowMs = 1000L))
    assertTrue(sampler.maybeSample(frame = "second", nowMs = 3000L))

    assertEquals(listOf("first", "second"), sampler.frames)
  }

  @Test
  fun stopsSamplingAtMaxFrames() {
    val sampler = CompanionFrameSampler<String>(sampleEveryMs = 1L, maxFrames = 2)

    assertTrue(sampler.maybeSample(frame = "first", nowMs = 0L))
    assertTrue(sampler.maybeSample(frame = "second", nowMs = 1L))
    assertFalse(sampler.maybeSample(frame = "third", nowMs = 2L))

    assertEquals(listOf("first", "second"), sampler.frames)
  }
}
