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

import android.content.Context
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskData
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class CompanionTask @Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = COMPANION_TASK_ID,
      label = COMPANION_TASK_LABEL,
      category = Category.LLM,
      description = "A tiny on-device AI companion that listens and sees through the front camera.",
      shortDescription = "On-device companion",
      models = mutableListOf(createCompanionModel()),
      defaultSystemPrompt = COMPANION_SYSTEM_PROMPT,
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (error: String) -> Unit,
  ) {
    model.runtimeHelper.initialize(
      context = context,
      model = model,
      taskId = task.id,
      supportImage = true,
      supportAudio = true,
      onDone = onDone,
      coroutineScope = coroutineScope,
      systemInstruction = systemInstruction ?: Contents.of(COMPANION_SYSTEM_PROMPT),
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    model.runtimeHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val myData = data as CustomTaskData
    CompanionApp(modelManagerViewModel = myData.modelManagerViewModel)
  }
}

@Module
@InstallIn(SingletonComponent::class)
internal object CompanionTaskModule {
  @Provides
  @IntoSet
  fun provideCompanionTask(): CustomTask {
    return CompanionTask()
  }
}
