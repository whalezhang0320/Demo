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
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource

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
 * 包含文本输入框、发送按钮、表情选择器按钮、图片选择器按钮以及语音录制按钮。
 *
 * @param onMessageSent 消息发送回调。
 * @param modifier 修饰符。
 * @param resetScroll 重置聊天列表滚动位置的回调。
 * @param onStartRecording 开始录音回调。
 * @param onStopRecording 停止录音回调。
 * @param isRecording 是否正在录音。
 * @param isGenerating 是否正在生成回答。
 * @param onPauseStream 暂停流式生成回调。
 * @param textFieldValue 当前文本框的值（Hoisted State）。
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
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    isRecording: Boolean = false,
    isGenerating: Boolean = false,  // 新增：是否正在生成回答
    onPauseStream: () -> Unit = {},  // 新增：暂停流式生成回调
    isTranscribing: Boolean = false,  // 新增
    pendingTranscription: String = "",  // 新增
    textFieldValue: TextFieldValue = TextFieldValue(),
    onTextChanged: (TextFieldValue) -> Unit = {},
    selectedImageUri: Uri? = null,
    onImageSelected: (Uri?) -> Unit = {}
) {
    // 当前选中的辅助输入面板（表情、图片等）
    var currentInputSelector by rememberSaveable { mutableStateOf(InputSelector.NONE) }
    // 是否显示功能未实现弹窗
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
        // 选中图片后关闭选择器面板（如果之前是通过面板打开的，虽然这里直接使用系统选择器）
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
        UserInputText(
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
            isRecording = isRecording,
            isTranscribing = isTranscribing,  // 新增
            pendingTranscription = pendingTranscription,  // 新增
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            isGenerating = isGenerating,
            onPauseStream = onPauseStream,
            onMessageSent = {
                onMessageSent(textFieldValue.text)
                // 清空输入框
                onTextChanged(TextFieldValue())
                // 重置状态
                dismissKeyboard()
                keyboardController?.hide()
            },
            onEmojiClicked = {
                if (currentInputSelector == InputSelector.EMOJI) {
                    currentInputSelector = InputSelector.NONE
                    keyboardController?.show()
                } else {
                    currentInputSelector = InputSelector.EMOJI
                    keyboardController?.hide()
                }
            },
            onImageClicked = {
                // 直接打开系统图片选择器
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        // 表情选择器面板
        SelectorExpanded(
            currentSelector = currentInputSelector,
            onCloseRequested = dismissKeyboard,
            onTextAdded = {
                // 将表情插入到当前光标位置
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

    // 聚焦请求器，用于在面板打开时保持焦点（如果需要）
    val focusRequester = remember { FocusRequester() }

    SideEffect {
        if (currentSelector == InputSelector.EMOJI) {
            focusRequester.requestFocus()
        }
    }

    Surface(tonalElevation = 8.dp) {
        when (currentSelector) {
            InputSelector.EMOJI -> EmojiSelector(onTextAdded, focusRequester)
            InputSelector.DM -> NotAvailablePopup(onCloseRequested)
            InputSelector.PICTURE -> FunctionalityNotAvailablePanel() // 图片现在直接通过 UserInputText 处理，这里保留作为备用
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
 * 用户输入文本框和主要操作按钮区域。
 *
 * ====== 修改说明 ======
 * 1. 录音按钮移入输入框内部左侧（替换了表情按钮）
 * 2. 外部右侧按钮仅作为发送/暂停键
 * 3. 录音按钮与发送按钮不再重复/切换
 * =====================
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserInputText(
    keyboardType: KeyboardType = KeyboardType.Text,
    onTextChanged: (TextFieldValue) -> Unit,
    textFieldValue: TextFieldValue,
    keyboardShown: Boolean,
    onTextFieldFocused: (Boolean) -> Unit,
    focusState: Boolean,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    isGenerating: Boolean,  // 新增：是否正在生成回答
    onPauseStream: () -> Unit,  // 新增：暂停流式生成回调
    onMessageSent: () -> Unit,
    onEmojiClicked: () -> Unit,
    isTranscribing: Boolean,  // 新增
    pendingTranscription: String,  // 新增
    onImageClicked: () -> Unit

) {
    val a11yLabel = stringResource(id = R.string.textfield_desc)

    // ====== 修改开始：新布局结构 ======
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
                // 语音按钮 - 在输入框内部左侧 (替代表情按钮)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    try {
                                        onStartRecording()
                                        awaitRelease()
                                    } finally {
                                        onStopRecording()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = stringResource(R.string.record_audio),
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 图片按钮 - 在输入框内部左侧
                IconButton(
                    onClick = onImageClicked,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.InsertPhoto,
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
                            keyboardType = keyboardType,
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

        // 发送/暂停按钮 - 在输入框外部右侧
        // 录音按钮已移至左侧
        Box(
            modifier = Modifier
                .padding(end = 16.dp, bottom = 8.dp)  // ✅ padding 在最外层
                .size(48.dp)  // ✅ 固定大小 48dp
                .background(
                    color = when {
                        isGenerating -> MaterialTheme.colorScheme.error  // 生成中：红色（暂停）
                        textFieldValue.text.isNotEmpty() -> MaterialTheme.colorScheme.primary  // 有文字：蓝色
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)  // 无文字：半透明蓝色（禁用）
                    },
                    shape = RoundedCornerShape(10.dp)  // ✅ 圆角 10dp
                )
                .then(
                    when {
                        isGenerating -> {
                            // 生成中：点击暂停
                            Modifier.clickable(onClick = onPauseStream)
                        }
                        textFieldValue.text.isNotEmpty() -> {
                            // 有文字时：普通点击发送
                            Modifier.clickable(
                                onClick = onMessageSent,
                                enabled = textFieldValue.text.isNotBlank()
                            )
                        }
                        else -> {
                            // 无文字：不可点击
                            Modifier
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // 图标切换
            if (isGenerating) {
                // 暂停图标（方形）
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Pause stream",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)  // ✅ 图标 22dp
                )
            } else {
                // 发送图标
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = stringResource(R.string.send),
                    tint = Color.White.copy(alpha = if (textFieldValue.text.isNotEmpty()) 1f else 0.5f),
                    modifier = Modifier.size(22.dp)  // ✅ 图标 22dp
                )
            }
        }
    }
    // ====== 修改结束 ======
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
 * 包含一个简单的表情列表供用户选择。
 */
@Composable
fun EmojiSelector(
    onTextAdded: (String) -> Unit,
    focusRequester: FocusRequester
) {
    var selected by remember { mutableStateOf(EmojiStickerSelector.EMOJI) }

    Column(
        modifier = Modifier
            .focusRequester(focusRequester)
            .height(320.dp) // 固定高度
    ) {
        // 顶部标签页 (Emoji / Stickers)
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

        // 表情内容区域
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
        // 简单的表情列表
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