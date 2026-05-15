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

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.google.ai.edge.gallery.common.calculatePeakAmplitude
import com.google.ai.edge.gallery.data.MAX_AUDIO_CLIP_DURATION_SEC
import com.google.ai.edge.gallery.data.SAMPLE_RATE
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

class CompanionAudioRecorder {
  private val audioStream = ByteArrayOutputStream()
  private var audioRecord: AudioRecord? = null
  private var recordingJob: Job? = null

  val isRecording: Boolean
    get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

  @SuppressLint("MissingPermission")
  fun start(scope: CoroutineScope, onAmplitudeChanged: (Int) -> Unit) {
    stopSynchronously()
    audioStream.reset()

    val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    val recorder =
      AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        minBufferSize,
      )
    audioRecord = recorder

    recordingJob =
      scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(minBufferSize)
        val startMs = System.currentTimeMillis()
        recorder.startRecording()
        while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          val bytesRead = recorder.read(buffer, 0, buffer.size)
          if (bytesRead > 0) {
            onAmplitudeChanged(calculatePeakAmplitude(buffer = buffer, bytesRead = bytesRead))
            audioStream.write(buffer, 0, bytesRead)
          }
          if (System.currentTimeMillis() - startMs >= MAX_AUDIO_CLIP_DURATION_SEC * 1000L) {
            recorder.stop()
          }
        }
      }
  }

  suspend fun stop(): ByteArray {
    val jobToJoin = recordingJob
    stopSynchronously()
    jobToJoin?.cancelAndJoin()
    return withContext(Dispatchers.IO) {
      val bytes = audioStream.toByteArray()
      audioStream.reset()
      bytes
    }
  }

  fun release() {
    stopSynchronously()
    audioStream.reset()
  }

  private fun stopSynchronously() {
    val recorder = audioRecord
    if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
      recorder.stop()
    }
    recorder?.release()
    audioRecord = null
    recordingJob = null
  }
}
