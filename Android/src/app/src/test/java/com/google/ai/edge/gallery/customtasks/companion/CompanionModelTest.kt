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

import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.RuntimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionModelTest {
  @Test
  fun companionModelDefaultsToGemma4E2bMultimodal() {
    val model = createCompanionModel()

    assertEquals("Gemma-4-E2B-it", model.name)
    assertEquals("gemma-4-E2B-it.litertlm", model.downloadFileName)
    assertEquals(2_588_147_712L, model.sizeInBytes)
    assertEquals(8, model.minDeviceMemoryInGb)
    assertEquals(RuntimeType.LITERT_LM, model.runtimeType)
    assertEquals(listOf(Accelerator.CPU, Accelerator.GPU), model.accelerators)
    assertTrue(model.llmSupportImage)
    assertTrue(model.llmSupportAudio)
    assertTrue(model.url.contains("huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"))
  }
}
