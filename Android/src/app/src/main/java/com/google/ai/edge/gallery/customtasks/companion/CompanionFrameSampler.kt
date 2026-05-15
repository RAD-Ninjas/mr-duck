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

const val COMPANION_FRAME_CAPTURE_DELAY_MS = 1000L

fun <T> createCompanionFrameSampler(): CompanionFrameSampler<T> =
  CompanionFrameSampler(
    sampleEveryMs = COMPANION_FRAME_CAPTURE_DELAY_MS,
    maxFrames = 1,
    firstSampleDelayMs = COMPANION_FRAME_CAPTURE_DELAY_MS,
  )

class CompanionFrameSampler<T>(
  private val sampleEveryMs: Long,
  private val maxFrames: Int,
  private val firstSampleDelayMs: Long = 0L,
) {
  private val sampledFrames = mutableListOf<T>()
  private var startedMs: Long? = null
  private var lastSampleMs: Long? = null

  val frames: List<T>
    get() = sampledFrames.toList()

  fun canSample(nowMs: Long = System.currentTimeMillis()): Boolean {
    if (sampledFrames.size >= maxFrames) {
      return false
    }
    val startMs = startedMs
    if (firstSampleDelayMs > 0L && (startMs == null || nowMs - startMs < firstSampleDelayMs)) {
      return false
    }
    val previousSampleMs = lastSampleMs
    return previousSampleMs == null || nowMs - previousSampleMs >= sampleEveryMs
  }

  fun maybeSample(frame: T, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (!canSample(nowMs = nowMs)) {
      return false
    }
    sampledFrames.add(frame)
    lastSampleMs = nowMs
    return true
  }

  fun start(nowMs: Long = System.currentTimeMillis()) {
    clear()
    startedMs = nowMs
  }

  fun clear() {
    sampledFrames.clear()
    startedMs = null
    lastSampleMs = null
  }
}
