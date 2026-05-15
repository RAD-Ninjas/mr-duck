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

import com.google.ai.edge.litertlm.Role
import com.google.ai.edge.litertlm.Content
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionConversationHistoryTest {
  @Test
  fun tokenBudgetRemovesOldestTurnsAndKeepsRecentContext() {
    val history = CompanionConversationHistory(tokenBudget = 120)

    val firstTurn =
      CompanionConversationTurn(
        user = CompanionHistoryMessage.user(text = "first user message".repeat(20)),
        agent = CompanionHistoryMessage.agent(text = "first answer".repeat(20)),
      )
    val secondTurn =
      CompanionConversationTurn(
        user = CompanionHistoryMessage.user(text = "second user message"),
        agent = CompanionHistoryMessage.agent(text = "second answer"),
      )

    history.addTurn(firstTurn)
    val trimmed = history.addTurn(secondTurn)

    assertTrue(trimmed)
    assertEquals(listOf(secondTurn), history.turns)
    assertTrue(history.estimatedTokens <= 120)
  }

  @Test
  fun litertMessagesPreserveUserThenAgentOrder() {
    val history = CompanionConversationHistory(tokenBudget = 1000)
    history.addTurn(
      CompanionConversationTurn(
        user = CompanionHistoryMessage.user(text = "question", audioWav = byteArrayOf(1), imagePng = byteArrayOf(2)),
        agent = CompanionHistoryMessage.agent(text = "answer"),
      )
    )

    val messages = history.toLiteRtMessages()

    assertEquals(2, messages.size)
    assertEquals(Role.USER, messages[0].role)
    assertEquals(Role.MODEL, messages[1].role)
  }

  @Test
  fun replayHistoryDoesNotReplayMediaBytes() {
    val history = CompanionConversationHistory(tokenBudget = 1000)
    history.addTurn(
      CompanionConversationTurn(
        user = CompanionHistoryMessage.user(text = COMPANION_HISTORY_USER_PLACEHOLDER, audioWav = byteArrayOf(1), imagePng = byteArrayOf(2)),
        agent = CompanionHistoryMessage.agent(text = "answer"),
      )
    )

    val userContents = history.toLiteRtMessages().first().contents.contents

    assertTrue(userContents.all { it is Content.Text })
    assertFalse(userContents.any { it is Content.ImageBytes })
    assertFalse(userContents.any { it is Content.AudioBytes })
  }
}
