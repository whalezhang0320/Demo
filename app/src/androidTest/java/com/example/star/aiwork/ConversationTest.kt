/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.star.aiwork

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.star.aiwork.data.exampleUiState
import com.example.star.aiwork.data.local.datasource.MessageLocalDataSourceImpl
import com.example.star.aiwork.data.remote.StreamingChatRemoteDataSource
import com.example.star.aiwork.data.repository.AiRepositoryImpl
import com.example.star.aiwork.data.repository.MessagePersistenceGatewayImpl
import com.example.star.aiwork.data.repository.MessageRepositoryImpl
import com.example.star.aiwork.domain.usecase.PauseStreamingUseCase
import com.example.star.aiwork.domain.usecase.RollbackMessageUseCase
import com.example.star.aiwork.domain.usecase.SendMessageUseCase
import com.example.star.aiwork.infra.network.SseClient
import com.example.star.aiwork.ui.conversation.ConversationContent
import com.example.star.aiwork.ui.conversation.ConversationLogic
import com.example.star.aiwork.ui.conversation.ConversationTestTag
import com.example.star.aiwork.ui.conversation.ConversationUiState
import com.example.star.aiwork.ui.theme.JetchatTheme
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.rememberCoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Checks that the features in the Conversation screen work as expected.
 */
class ConversationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val themeIsDark = MutableStateFlow(false)

    @Before
    fun setUp() {
        // Launch the conversation screen
        composeTestRule.setContent {
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            val sseClient = SseClient()
            val remoteDataSource = StreamingChatRemoteDataSource(sseClient)
            val aiRepository = AiRepositoryImpl(remoteDataSource)
            val messageLocalDataSource = MessageLocalDataSourceImpl(context)
            val messageRepository = MessageRepositoryImpl(messageLocalDataSource)
            val persistenceGateway = MessagePersistenceGatewayImpl(messageRepository)

            val sendMessageUseCase = SendMessageUseCase(aiRepository, persistenceGateway, scope)
            val pauseStreamingUseCase = PauseStreamingUseCase(aiRepository)
            val rollbackMessageUseCase = RollbackMessageUseCase(aiRepository, persistenceGateway)

            val previewLogic = ConversationLogic(
                uiState = conversationTestUiState,
                context = context,
                authorMe = "me",
                timeNow = "now",
                sendMessageUseCase = sendMessageUseCase,
                pauseStreamingUseCase = pauseStreamingUseCase,
                rollbackMessageUseCase = rollbackMessageUseCase,
                sessionId = "123",
                getProviderSettings = { emptyList() },
                persistenceGateway = persistenceGateway,
                onRenameSession = { _, _ -> }
            )

            JetchatTheme(isDarkTheme = themeIsDark.collectAsStateWithLifecycle(false).value) {
                ConversationContent(
                    uiState = conversationTestUiState,
                    logic = previewLogic,
                    navigateToProfile = { },
                    onNavIconPressed = { },
                )
            }
        }
    }

    @Test
    fun app_launches() {
        // Check that the conversation screen is visible on launch
        composeTestRule.onNodeWithTag(ConversationTestTag).assertIsDisplayed()
    }

    @Test
    fun userScrollsUp_jumpToBottomAppears() {
        // Check list is snapped to bottom and swipe up
        findJumpToBottom().assertDoesNotExist()
        composeTestRule.onNodeWithTag(ConversationTestTag).performTouchInput {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x, this.center.y + 500),
                durationMillis = 200,
            )
        }
        // Check that the jump to bottom button is shown
        findJumpToBottom().assertIsDisplayed()
    }

    @Test
    fun jumpToBottom_snapsToBottomAndDisappears() {
        // When the scroll is not snapped to the bottom
        composeTestRule.onNodeWithTag(ConversationTestTag).performTouchInput {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x, this.center.y + 500),
                durationMillis = 200,
            )
        }
        // Snap scroll to the bottom
        findJumpToBottom().performClick()

        // Check that the button is hidden
        findJumpToBottom().assertDoesNotExist()
    }

    @Test
    fun jumpToBottom_snapsToBottomAfterUserInteracted() {
        // First swipe
        composeTestRule.onNodeWithTag(
            testTag = ConversationTestTag,
            useUnmergedTree = true, // https://issuetracker.google.com/issues/184825850
        ).performTouchInput {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x, this.center.y + 500),
                durationMillis = 200,
            )
        }
        // Second, snap to bottom
        findJumpToBottom().performClick()

        // Open Emoji selector
        openEmojiSelector()

        // Assert that the list is still snapped to bottom
        findJumpToBottom().assertDoesNotExist()
    }

    @Test
    fun changeTheme_scrollIsPersisted() {
        // Swipe to show the jump to bottom button
        composeTestRule.onNodeWithTag(ConversationTestTag).performTouchInput {
            this.swipe(
                start = this.center,
                end = Offset(this.center.x, this.center.y + 500),
                durationMillis = 200,
            )
        }

        // Check that the jump to bottom button is shown
        findJumpToBottom().assertIsDisplayed()

        // Set theme to dark
        themeIsDark.value = true

        // Check that the jump to bottom button is still shown
        findJumpToBottom().assertIsDisplayed()
    }

    private fun findJumpToBottom() = composeTestRule.onNodeWithText(
        composeTestRule.activity.getString(R.string.jumpBottom),
        useUnmergedTree = true,
    )

    private fun openEmojiSelector() = composeTestRule
        .onNodeWithContentDescription(
            label = composeTestRule.activity.getString(R.string.emoji_selector_bt_desc),
            useUnmergedTree = true, // https://issuetracker.google.com/issues/184825850
        )
        .performClick()
}

/**
 * Make the list of messages longer so the test makes sense on tablets.
 */
private val conversationTestUiState = ConversationUiState(
    initialMessages = (exampleUiState.messages.plus(exampleUiState.messages)),
    channelName = "#composers",
    channelMembers = 42,
)
