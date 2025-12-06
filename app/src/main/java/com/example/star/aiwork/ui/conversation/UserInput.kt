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

package com.example.star.aiwork.ui.conversation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.star.aiwork.R
import com.example.star.aiwork.ui.FunctionalityNotAvailablePopup
import com.example.star.aiwork.ui.components.BackPressHandler
import com.example.star.aiwork.ui.theme.JetchatTheme

/**
 * 键盘类型枚举。
 * 用于测试时的语义标识。
 */
enum class InputSelector {
    NONE,
    MAP,
    DM,
    EMOJI,
    PHONE,
    PICTURE
}

/**
 * 表情选择器高度枚举。
 */
enum class EmojiStickerSelector {
    EMOJI,
    STICKER
}

@Preview
@Composable
fun UserInputPreview() {
    JetchatTheme {
        UserInput(onMessageSent = {})
    }
}

/**
 * 用户输入区域组件。
 *
 * @param onMessageSent 消息发送回调。
 * @param modifier 修饰符。
 * @param resetScroll 重置聊天列表滚动位置的回调。
 * @param isVoiceMode 是否处于语音输入模式。
 * @param onVoiceModeChanged 语音模式切换回调。
 * @param voiceInputStage 当前语音输入阶段。
 * @param onVoiceStageChanged 语音阶段变更回调。
 * @param pendingTranscription 暂存转写文本。
 * @param onTranscriptionChanged 转写文本变更回调。
 * @param currentVolume 当前音量。
 * @param onVolumeChanged 音量变更回调。
 * @param onStartRecording 开始录音回调。
 * @param onStopRecording 停止录音回调。
 * @param isGenerating 是否正在生成回答。
 * @param onPauseStream 暂停流式生成回调。
 * @param textFieldValue 当前文本框的值。
 * @param onTextChanged 文本变更回调。
 * @param selectedImageUri 当前选中的图片 URI。
 * @param onImageSelected 图片选中回调。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserInput(
    onMessageSent: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetScroll: () -> Unit = {},
    isVoiceMode: Boolean = false,
    onVoiceModeChanged: (Boolean) -> Unit = {},
    voiceInputStage: VoiceInputStage = VoiceInputStage.IDLE,
    onVoiceStageChanged: (VoiceInputStage) -> Unit = {},
    pendingTranscription: String = "",
    onTranscriptionChanged: (String) -> Unit = {},
    currentVolume: Float = 0f,
    onVolumeChanged: (Float) -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    isGenerating: Boolean = false,
    onPauseStream: () -> Unit = {},
    textFieldValue: TextFieldValue = TextFieldValue(),
    onTextChanged: (TextFieldValue) -> Unit = {},
    selectedImageUri: Uri? = null,
    onImageSelected: (Uri?) -> Unit = {}
) {
    // 当前选中的辅助输入面板（表情、图片等）
    var currentInputSelector by rememberSaveable { mutableStateOf(InputSelector.NONE) }
    val dismissKeyboard = { currentInputSelector = InputSelector.NONE }

    // 拦截返回键以关闭辅助面板
    if (currentInputSelector != InputSelector.NONE) {
        BackPressHandler(onBackPressed = dismissKeyboard)
    }

    // 用于键盘控制
    val keyboardController = LocalSoftwareKeyboardController.current

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        onImageSelected(uri)
        currentInputSelector = InputSelector.NONE
    }

    Column(modifier = modifier) {
        // 图片预览区域
        if (selectedImageUri != null) {
            Box(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { onImageSelected(null) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove image",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Divider()

        // ====== 主输入区域：根据模式切换 ======
        if (isVoiceMode) {
            // 语音模式：显示"按住说话"按钮
            VoiceModeInput(
                onVoiceModeChanged = onVoiceModeChanged,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                voiceInputStage = voiceInputStage,
                onVoiceStageChanged = onVoiceStageChanged
            )
        } else {
            // 文本模式：显示文本输入框
            TextModeInput(
                textFieldValue = textFieldValue,
                onTextChanged = onTextChanged,
                keyboardShown = currentInputSelector == InputSelector.NONE,
                onTextFieldFocused = { focused ->
                    if (focused) {
                        currentInputSelector = InputSelector.NONE
                        resetScroll()
                    }
                },
                focusState = currentInputSelector == InputSelector.NONE,
                isGenerating = isGenerating,
                onPauseStream = onPauseStream,
                onMessageSent = {
                    onMessageSent(textFieldValue.text)
                    onTextChanged(TextFieldValue())
                    dismissKeyboard()
                    keyboardController?.hide()
                },
                onVoiceModeClicked = {
                    onVoiceModeChanged(true) // 切换到语音模式
                },
                onImageClicked = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }

        // 表情选择器面板
        SelectorExpanded(
            currentSelector = currentInputSelector,
            onCloseRequested = dismissKeyboard,
            onTextAdded = {
                val currentText = textFieldValue.text
                val selection = textFieldValue.selection
                val newText = currentText.replaceRange(selection.start, selection.end, it)
                val newCursorPos = selection.start + it.length
                onTextChanged(
                    TextFieldValue(
                        text = newText,
                        selection = TextRange(newCursorPos)
                    )
                )
            }
        )
    }

    // 语音输入悬浮面板
    VoiceInputPanel(
        stage = voiceInputStage,
        transcription = pendingTranscription,
        volume = currentVolume,
        onTextChanged = onTranscriptionChanged,
        onConfirm = {
            // 确认发送
            onMessageSent(pendingTranscription)
            onTranscriptionChanged("")
            onVoiceStageChanged(VoiceInputStage.IDLE)
        },
        onCancel = {
            // 取消
            onTranscriptionChanged("")
            onVoiceStageChanged(VoiceInputStage.IDLE)
        }
    )
}

/**
 * 文本模式输入（原有逻辑）
 */
@Composable
private fun TextModeInput(
    textFieldValue: TextFieldValue,
    onTextChanged: (TextFieldValue) -> Unit,
    keyboardShown: Boolean,
    onTextFieldFocused: (Boolean) -> Unit,
    focusState: Boolean,
    isGenerating: Boolean,
    onPauseStream: () -> Unit,
    onMessageSent: () -> Unit,
    onVoiceModeClicked: () -> Unit,
    onImageClicked: () -> Unit
) {
    val a11yLabel = stringResource(id = R.string.textfield_desc)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp, max = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 输入框 - 包含内部按钮
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 语音按钮 - 点击切换到语音模式
                IconButton(
                    onClick = onVoiceModeClicked,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = stringResource(R.string.record_audio),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 图片按钮
                IconButton(
                    onClick = onImageClicked,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_image),
                        contentDescription = stringResource(R.string.attach_photo_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 文本输入区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    var lastFocusState by remember { mutableStateOf(false) }

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { onTextChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (lastFocusState != state.isFocused) {
                                    onTextFieldFocused(state.isFocused)
                                }
                                lastFocusState = state.isFocused
                            }
                            .semantics {
                                contentDescription = a11yLabel
                                keyboardShownProperty = keyboardShown
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Default
                        ),
                        maxLines = 4,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    // 提示文本
                    if (textFieldValue.text.isEmpty() && !focusState) {
                        Text(
                            text = stringResource(id = R.string.textfield_hint),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }

        // 发送/暂停按钮
        Box(
            modifier = Modifier
                .padding(end = 16.dp, bottom = 8.dp)
                .size(48.dp)
                .background(
                    color = when {
                        isGenerating -> MaterialTheme.colorScheme.error
                        textFieldValue.text.isNotEmpty() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                .then(
                    when {
                        isGenerating -> Modifier.clickable(onClick = onPauseStream)
                        textFieldValue.text.isNotEmpty() -> Modifier.clickable(
                            onClick = onMessageSent,
                            enabled = textFieldValue.text.isNotBlank()
                        )
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isGenerating) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Pause stream",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = Color.White.copy(alpha = if (textFieldValue.text.isNotEmpty()) 1f else 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * 语音模式输入（"按住说话"按钮）
 */
@Composable
private fun VoiceModeInput(
    onVoiceModeChanged: (Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    voiceInputStage: VoiceInputStage,
    onVoiceStageChanged: (VoiceInputStage) -> Unit
) {
    // 手势状态
    var pressStartY by remember { mutableFloatStateOf(0f) }
    var currentY by remember { mutableFloatStateOf(0f) }
    val cancelThreshold = 100f // 上滑取消阈值（像素）

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "按住说话"按钮
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .background(
                    color = when (voiceInputStage) {
                        VoiceInputStage.RECORDING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        VoiceInputStage.CANCEL_WARNING -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = RoundedCornerShape(26.dp)
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            // ✅ 等待按下 - 立即触发
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressStartY = down.position.y
                            currentY = down.position.y

                            // ✅ 立即开始录音（不需要等待拖动）
                            onStartRecording()
                            onVoiceStageChanged(VoiceInputStage.RECORDING)

                            // ✅ 跟踪手指移动
                            do {
                                val event = awaitPointerEvent()

                                // 更新位置
                                val pointer = event.changes.firstOrNull()
                                if (pointer != null && !pointer.changedToUp()) {
                                    currentY = pointer.position.y
                                    val deltaY = pressStartY - currentY

                                    // 检测上滑
                                    if (deltaY > cancelThreshold) {
                                        if (voiceInputStage != VoiceInputStage.CANCEL_WARNING) {
                                            onVoiceStageChanged(VoiceInputStage.CANCEL_WARNING)
                                        }
                                    } else {
                                        if (voiceInputStage == VoiceInputStage.CANCEL_WARNING) {
                                            onVoiceStageChanged(VoiceInputStage.RECORDING)
                                        }
                                    }
                                }
                            } while (event.changes.any { !it.changedToUp() })

                            // ✅ 松手后处理
                            onStopRecording()

                            if (voiceInputStage == VoiceInputStage.CANCEL_WARNING) {
                                // 取消状态：清空并关闭
                                onVoiceStageChanged(VoiceInputStage.IDLE)
                            } else {
                                // 正常结束：进入编辑状态
                                onVoiceStageChanged(VoiceInputStage.EDITING)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (voiceInputStage) {
                    VoiceInputStage.RECORDING -> "正在录音..."
                    VoiceInputStage.CANCEL_WARNING -> "松开取消"
                    else -> "按住说话"
                },
                color = Color.White,
                fontSize = 16.sp
            )
        }

        // 切换回键盘按钮
        IconButton(
            onClick = { onVoiceModeChanged(false) },
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(10.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = "切换到键盘",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 辅助输入面板扩展区域。
 */
@Composable
private fun SelectorExpanded(
    currentSelector: InputSelector,
    onCloseRequested: () -> Unit,
    onTextAdded: (String) -> Unit
) {
    if (currentSelector == InputSelector.NONE) return

    val focusRequester = remember { FocusRequester() }

    Surface(tonalElevation = 8.dp) {
        when (currentSelector) {
            InputSelector.EMOJI -> EmojiSelector(onTextAdded, focusRequester)
            InputSelector.DM -> NotAvailablePopup(onCloseRequested)
            InputSelector.PICTURE -> FunctionalityNotAvailablePanel()
            InputSelector.MAP -> FunctionalityNotAvailablePanel()
            InputSelector.PHONE -> FunctionalityNotAvailablePanel()
            else -> { throw NotImplementedError() }
        }
    }
}

/**
 * 功能未实现面板。
 */
@Composable
fun FunctionalityNotAvailablePanel() {
    AnimatedVisibility(
        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
        enter = expandHorizontally() + fadeIn(),
        exit = shrinkHorizontally() + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .height(320.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.not_available),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = R.string.not_available_subtitle),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun NotAvailablePopup(onDismissed: () -> Unit) {
    FunctionalityNotAvailablePopup(onDismissed)
}

/**
 * 分隔线组件。
 */
@Composable
fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
}

/**
 * 表情选择器。
 */
@Composable
fun EmojiSelector(
    onTextAdded: (String) -> Unit,
    focusRequester: FocusRequester
) {
    var selected by remember { mutableStateOf(EmojiStickerSelector.EMOJI) }

    Column(
        modifier = Modifier
            .height(320.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            ExtendedSelectorInnerButton(
                text = stringResource(id = R.string.emojis_label),
                onClick = { selected = EmojiStickerSelector.EMOJI },
                selected = true,
                modifier = Modifier.weight(1f)
            )
            ExtendedSelectorInnerButton(
                text = stringResource(id = R.string.stickers_label),
                onClick = { selected = EmojiStickerSelector.STICKER },
                selected = false,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.verticalScroll(rememberScrollState())) {
            EmojiTable(onTextAdded, modifier = Modifier.padding(8.dp))
        }
    }
    if (selected == EmojiStickerSelector.STICKER) {
        NotAvailablePopup(onDismissed = { selected = EmojiStickerSelector.EMOJI })
    }
}

/**
 * 选择器内部按钮。
 */
@Composable
fun ExtendedSelectorInnerButton(
    text: String,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = ButtonDefaults.buttonColors(
        containerColor = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        else Color.Transparent,
        disabledContainerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)
    )
    TextButton(
        onClick = onClick,
        modifier = modifier
            .padding(8.dp)
            .height(36.dp),
        colors = colors,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

/**
 * 表情网格。
 */
@Composable
fun EmojiTable(
    onTextAdded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        repeat(4) { x ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(4) { y ->
                    val emoji = emojis[x * 4 + y]
                    Text(
                        modifier = Modifier.clickable(onClick = { onTextAdded(emoji) })
                            .sizeIn(minWidth = 42.dp, minHeight = 42.dp)
                            .padding(8.dp),
                        text = emoji,
                        style = LocalTextStyle.current.copy(
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }
        }
    }
}

// 示例表情数据
private val emojis = listOf(
    "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE03",
    "\uD83D\uDE04", "\uD83D\uDE05", "\uD83D\uDE06", "\uD83D\uDE09",
    "\uD83D\uDE0A", "\uD83D\uDE0B", "\uD83D\uDE0E", "\uD83D\uDE0D",
    "\uD83D\uDE18", "\uD83D\uDE17", "\uD83D\uDE19", "\uD83D\uDE1A"
)

// Semantics key for testing
val KeyboardShownKey = SemanticsPropertyKey<Boolean>("KeyboardShownKey")
var SemanticsPropertyReceiver.keyboardShownProperty by KeyboardShownKey