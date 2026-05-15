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

package com.google.ai.edge.gallery.ui.llmchat

import org.junit.Assert.assertTrue
import org.junit.Test

class LlmChatModelHelperDebugTest {
  @Test
  fun liteRtContentDebugLogShowsFinalPayloadParts() {
    val summary =
      liteRtContentDebugLog(
        listOf(
          LiteRtContentDebugPart.image(width = 240, height = 320, bytes = 1234),
          LiteRtContentDebugPart.audio(bytes = 36520),
          LiteRtContentDebugPart.text(chars = 42, preview = "attached audio route"),
        )
      )

    assertTrue(summary.contains("LiteRT sendMessageAsync payload"))
    assertTrue(summary.contains("contentCount=3"))
    assertTrue(summary.contains("image(240x320,pngBytes=1234)"))
    assertTrue(summary.contains("audio(wavBytes=36520)"))
    assertTrue(summary.contains("text(chars=42,preview=\"attached audio route\")"))
  }
}
