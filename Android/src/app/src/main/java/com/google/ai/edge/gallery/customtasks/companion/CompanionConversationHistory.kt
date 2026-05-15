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

import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import java.io.ByteArrayOutputStream

const val COMPANION_CONTEXT_WINDOW_TOKENS = 32_000
const val COMPANION_RESPONSE_RESERVE_TOKENS = 4_000
const val COMPANION_HISTORY_BUDGET_TOKENS =
  COMPANION_CONTEXT_WINDOW_TOKENS - COMPANION_RESPONSE_RESERVE_TOKENS
const val COMPANION_HISTORY_USER_PLACEHOLDER =
  "The user sent a spoken message with private visual context."

private const val COMPANION_MESSAGE_OVERHEAD_TOKENS = 16
private const val COMPANION_IMAGE_TOKEN_ESTIMATE = 2048
private const val COMPANION_AUDIO_BYTES_PER_TOKEN_ESTIMATE = 128

data class CompanionHistoryMessage(
  val text: String,
  val fromUser: Boolean,
  val audioWav: ByteArray? = null,
  val imagePng: ByteArray? = null,
) {
  val estimatedTokens: Int
    get() =
      COMPANION_MESSAGE_OVERHEAD_TOKENS +
        estimateCompanionTextTokens(text) +
        (if (imagePng == null) 0 else COMPANION_IMAGE_TOKEN_ESTIMATE) +
        ((audioWav?.size ?: 0) + COMPANION_AUDIO_BYTES_PER_TOKEN_ESTIMATE - 1) /
          COMPANION_AUDIO_BYTES_PER_TOKEN_ESTIMATE

  fun toLiteRtMessage(): Message {
    val replayText = text.ifBlank { COMPANION_HISTORY_USER_PLACEHOLDER }
    val litertContents = Contents.of(Content.Text(replayText))
    return if (fromUser) Message.user(litertContents) else Message.model(litertContents)
  }

  companion object {
    fun user(text: String, audioWav: ByteArray? = null, imagePng: ByteArray? = null) =
      CompanionHistoryMessage(text = text, fromUser = true, audioWav = audioWav, imagePng = imagePng)

    fun agent(text: String) = CompanionHistoryMessage(text = text, fromUser = false)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CompanionHistoryMessage) return false
    return text == other.text &&
      fromUser == other.fromUser &&
      audioWav.contentEquals(other.audioWav) &&
      imagePng.contentEquals(other.imagePng)
  }

  override fun hashCode(): Int {
    var result = text.hashCode()
    result = 31 * result + fromUser.hashCode()
    result = 31 * result + (audioWav?.contentHashCode() ?: 0)
    result = 31 * result + (imagePng?.contentHashCode() ?: 0)
    return result
  }
}

data class CompanionConversationTurn(
  val user: CompanionHistoryMessage,
  val agent: CompanionHistoryMessage,
) {
  val estimatedTokens: Int
    get() = user.estimatedTokens + agent.estimatedTokens

  fun toLiteRtMessages(): List<Message> = listOf(user.toLiteRtMessage(), agent.toLiteRtMessage())
}

class CompanionConversationHistory(
  private val tokenBudget: Int = COMPANION_HISTORY_BUDGET_TOKENS,
) {
  private val retainedTurns = mutableListOf<CompanionConversationTurn>()

  val turns: List<CompanionConversationTurn>
    get() = retainedTurns.toList()

  val estimatedTokens: Int
    get() = retainedTurns.sumOf { it.estimatedTokens }

  fun addTurn(turn: CompanionConversationTurn): Boolean {
    retainedTurns.add(turn)
    return trimToBudget(extraTokens = 0, keepAtLeastOneTurn = true)
  }

  fun trimToFit(nextUserMessage: CompanionHistoryMessage): Boolean =
    trimToBudget(extraTokens = nextUserMessage.estimatedTokens, keepAtLeastOneTurn = false)

  fun toLiteRtMessages(): List<Message> = retainedTurns.flatMap { it.toLiteRtMessages() }

  fun clear() {
    retainedTurns.clear()
  }

  private fun trimToBudget(extraTokens: Int, keepAtLeastOneTurn: Boolean): Boolean {
    var trimmed = false
    val minimumTurns = if (keepAtLeastOneTurn) 1 else 0
    while (retainedTurns.size > minimumTurns && estimatedTokens + extraTokens > tokenBudget) {
      retainedTurns.removeAt(0)
      trimmed = true
    }
    return trimmed
  }
}

fun estimateCompanionTextTokens(text: String): Int = (text.length + 3) / 4

fun Bitmap.toCompanionPngByteArray(): ByteArray {
  val stream = ByteArrayOutputStream()
  compress(Bitmap.CompressFormat.PNG, 100, stream)
  return stream.toByteArray()
}
