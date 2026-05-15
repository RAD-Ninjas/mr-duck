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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelInitializationStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@Composable
fun CompanionApp(
  modelManagerViewModel: ModelManagerViewModel,
  viewModel: CompanionViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val task = modelManagerUiState.tasks.find { it.id == COMPANION_TASK_ID }
  val model = task?.models?.firstOrNull()

  if (task == null || model == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  val downloadStatus = modelManagerUiState.modelDownloadStatus[model.name]
  val initializationStatus = modelManagerUiState.modelInitializationStatus[model.name]

  LaunchedEffect(model.name) { modelManagerViewModel.selectModel(model) }

  LaunchedEffect(downloadStatus?.status, model.name) {
    when (downloadStatus?.status) {
      ModelDownloadStatusType.NOT_DOWNLOADED,
      ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
      null -> modelManagerViewModel.downloadModel(task = task, model = model)
      else -> Unit
    }
  }

  LaunchedEffect(downloadStatus?.status, initializationStatus?.status, model.name) {
    if (
      downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED &&
        (initializationStatus == null ||
          initializationStatus.status == ModelInitializationStatusType.NOT_INITIALIZED)
    ) {
      modelManagerViewModel.initializeModel(context = context, task = task, model = model)
    }
  }

  CompanionScreen(
    model = model,
    downloadStatus = downloadStatus,
    initializationStatus = initializationStatus,
    viewModel = viewModel,
    onRetryDownload = { modelManagerViewModel.downloadModel(task = task, model = model) },
    onRetryInitialize = { modelManagerViewModel.initializeModel(context = context, task = task, model = model, force = true) },
    onReset = { viewModel.resetCompanionSession(task = task, model = model) },
  )
}
