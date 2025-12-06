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

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFrom
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.star.aiwork.R
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.star.aiwork.domain.model.Model
import com.example.star.aiwork.domain.model.ProviderSetting
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import android.util.Log
import androidx.compose.runtime.LaunchedEffect
import com.example.star.aiwork.domain.usecase.GenerateChatNameUseCase
import kotlinx.coroutines.flow.onCompletion

const val ConversationTestTag = "ConversationTestTag"

/**
 * 消息列表组件
 * 支持：Markdown渲染、智能复制、加载动画、图片显示
 */
@Composable
fun Messages(
    messages: List<Message>,
    navigateToProfile: (String) -> Unit,
    scrollState: LazyListState,
    modifier: Modifier = Modifier,
    logic: ConversationLogic? = null,
    providerSetting: ProviderSetting? = null,
    model: Model? = null,
    retrieveKnowledge: suspend (String) -> String = { "" },
    scope: CoroutineScope? = null,
    isGenerating: Boolean = false,
    uiState: ConversationUiState? = null  // ← 新增参数
) {
    val coroutineScope = scope ?: rememberCoroutineScope()

    // 提取预览卡片
    val previewCards = remember(messages.size, messages.lastOrNull()?.content) {
        val cards = extractPreviewCardsFromMessages(messages)
        Log.d("Messages", "提取到 ${cards.size} 个预览卡片")
        cards
    }

    Box(modifier = modifier) {
        val authorMe = stringResource(id = R.string.author_me)

        // 找到最后一条助手消息（在 reverseLayout 中，第一条消息是最后一条）
        val lastAssistantMessageIndex = messages.indexOfFirst {
            it.author != authorMe && it.author != "System"
        }
        val showRegenerateButton = lastAssistantMessageIndex >= 0 &&
                logic != null &&
                providerSetting != null &&
                model != null &&
                !messages[lastAssistantMessageIndex].isLoading

        LazyColumn(
            reverseLayout = true,
            state = scrollState,
            modifier = Modifier
                .testTag(ConversationTestTag)
                .fillMaxSize(),
        ) {
            for (index in messages.indices) {
                val prevAuthor = messages.getOrNull(index - 1)?.author
                val nextAuthor = messages.getOrNull(index + 1)?.author
                val content = messages[index]
                val isFirstMessageByAuthor = prevAuthor != content.author
                val isLastMessageByAuthor = nextAuthor != content.author
                val isLastAssistantMessage = index == lastAssistantMessageIndex

                // 为了简单起见，硬编码日期分隔线
                if (index == messages.size - 1) {
                    item {
                        //DayHeader("20 Aug")
                    }
                } else if (index == 2) {
                    item {
                        //DayHeader("Today")
                    }
                }

                item {
                    Message(
                        onAuthorClick = { name -> navigateToProfile(name) },
                        msg = content,
                        isUserMe = content.author == authorMe,
                        isFirstMessageByAuthor = isFirstMessageByAuthor,
                        isLastMessageByAuthor = isLastMessageByAuthor,
                        isLastAssistantMessage = isLastAssistantMessage,
                        showRegenerateButton = showRegenerateButton && isLastAssistantMessage,
                        onRegenerateClick = {
                            coroutineScope.launch {
                                logic?.rollbackAndRegenerate(
                                    providerSetting = providerSetting,
                                    model = model,
                                    retrieveKnowledge = retrieveKnowledge
                                )
                            }
                        },
                        onThumbUpClick = {
                            // TODO: 实现点赞功能
                        },
                        onThumbDownClick = {
                            // TODO: 实现点踩功能
                        },
                        onMoreClick = {
                            // TODO: 实现更多操作功能
                        },
                        isGenerating = isGenerating
                    )
                }
            }
        }

        // 跳转到底部按钮
        val jumpThreshold = with(LocalDensity.current) {
            JumpToBottomThreshold.toPx()
        }

        val jumpToBottomButtonEnabled by remember {
            derivedStateOf {
                scrollState.firstVisibleItemIndex != 0 ||
                        scrollState.firstVisibleItemScrollOffset > jumpThreshold
            }
        }

        JumpToBottom(
            enabled = jumpToBottomButtonEnabled,
            onClicked = {
                coroutineScope.launch {
                    scrollState.animateScrollToItem(0)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 右侧预览边栏
        if (previewCards.isNotEmpty()) {
            Log.d("Messages", "显示预览边栏，卡片数量: ${previewCards.size}")
            PreviewSidebar(
                previewCards = previewCards,
                modifier = Modifier.align(Alignment.CenterEnd),
                uiState = uiState  // ← 新增参数
            )
        } else {
            Log.d("Messages", "没有预览卡片，不显示边栏")
        }
    }
}

/**
 * 单条消息组件 - 支持左右对齐
 */
@Composable
fun Message(
    onAuthorClick: (String) -> Unit,
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
    isLastAssistantMessage: Boolean = false,
    showRegenerateButton: Boolean = false,
    onRegenerateClick: () -> Unit = {},
    onThumbUpClick: () -> Unit = {},
    onThumbDownClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    isGenerating: Boolean = false
) {
    val borderColor = if (isUserMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    val spaceBetweenAuthors = if (isLastMessageByAuthor) Modifier.padding(top = 8.dp) else Modifier

    Row(
        modifier = spaceBetweenAuthors.fillMaxWidth(),
        horizontalArrangement = if (isUserMe) Arrangement.End else Arrangement.Start
    ) {
        // 删除了 AI 头像显示部分

        AuthorAndTextMessage(
            msg = msg,
            isUserMe = isUserMe,
            isFirstMessageByAuthor = isFirstMessageByAuthor,
            isLastMessageByAuthor = isLastMessageByAuthor,
            authorClicked = onAuthorClick,
            isLastAssistantMessage = isLastAssistantMessage,
            showRegenerateButton = showRegenerateButton,
            onRegenerateClick = onRegenerateClick,
            onThumbUpClick = onThumbUpClick,
            onThumbDownClick = onThumbDownClick,
            onMoreClick = onMoreClick,
            isGenerating = isGenerating,
            modifier = Modifier
                .padding(
                    end = if (isUserMe) 16.dp else 16.dp,
                    start = if (isUserMe) 0.dp else 16.dp
                )
                .widthIn(max = if (isUserMe) 300.dp else 370.dp)  // ← 改成这1行
        )
    }
}

/**
 * 消息内容容器 - 作者名 + 消息气泡
 */
@Composable
fun AuthorAndTextMessage(
    msg: Message,
    isUserMe: Boolean,
    isFirstMessageByAuthor: Boolean,
    isLastMessageByAuthor: Boolean,
    authorClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLastAssistantMessage: Boolean = false,
    showRegenerateButton: Boolean = false,
    onRegenerateClick: () -> Unit = {},
    onThumbUpClick: () -> Unit = {},
    onThumbDownClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    isGenerating: Boolean = false
) {
    Column(modifier = modifier) {
        if (isLastMessageByAuthor && !isUserMe) {
            Timestamp(msg)
        }
        ChatItemBubble(msg, isUserMe, authorClicked = authorClicked)
        
        // 在消息气泡底部显示操作按钮（水平并排）
        // 排列顺序：复制 + 点赞 + 点踩 + 重新生成 + 更多操作
        // 当 isGenerating 为 true 时，不显示功能栏
        if (!isUserMe && msg.author != "System" && !isGenerating) {
            val clipboardManager = LocalClipboardManager.current
            val showCopyButton = isPureTextContent(msg.content) && msg.content.isNotEmpty()
            val showRegenerate = isLastAssistantMessage && showRegenerateButton
            
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                // 1. 复制按钮
                if (showCopyButton) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(msg.content))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // 2. 点赞按钮
                IconButton(
                    onClick = onThumbUpClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "点赞",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // 3. 点踩按钮
                IconButton(
                    onClick = onThumbDownClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = "点踩",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // 4. 重新生成按钮（仅最后一条助手消息）
                if (showRegenerate) {
                    IconButton(
                        onClick = onRegenerateClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新生成",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                // 5. 更多操作按钮
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        if (isFirstMessageByAuthor) {
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * 时间戳
 */
@Composable
private fun Timestamp(msg: Message) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = msg.timestamp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alignBy(LastBaseline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// 根据消息类型返回不同的气泡形状
private fun getChatBubbleShape(isUserMe: Boolean): RoundedCornerShape {
    return if (isUserMe) {
        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp) // 用户消息：右上角直角
    } else {
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp) // AI消息：左上角直角
    }
}

/**
 * 日期分隔线
 */
@Composable
fun DayHeader(dayString: String) {
    Row(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 16.dp)
            .height(16.dp),
    ) {
        DayHeaderLine()
        Text(
            text = dayString,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DayHeaderLine()
    }
}

@Composable
private fun RowScope.DayHeaderLine() {
    HorizontalDivider(
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )
}

/**
 * 消息气泡 - 现代化设计，支持Markdown渲染、智能复制、加载动画
 */
@Composable
fun ChatItemBubble(
    message: Message,
    isUserMe: Boolean,
    authorClicked: (String) -> Unit
) {
    val isSystemMessage = message.author == "System"

    // 现代化配色方案
    val backgroundBubbleColor = when {
        isSystemMessage -> MaterialTheme.colorScheme.errorContainer
        isUserMe -> MaterialTheme.colorScheme.primary  // 深蓝色
        else -> Color(0xFFF5F5F5)  // 浅灰白色
    }

    // 边框颜色（柔和半透明）
    val borderColor = when {
        isSystemMessage -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
        isUserMe -> Color.White.copy(alpha = 0.2f)
        else -> Color.Black.copy(alpha = 0.06f)  // 浅灰边框
    }

    // 阴影高度
    val shadowElevation = if (isSystemMessage) 1.dp else 3.dp

    Column {
        Surface(
            color = backgroundBubbleColor,
            shape = getChatBubbleShape(isUserMe),  // 修改这里
            shadowElevation = shadowElevation,
            modifier = Modifier.border(
                width = 1.dp,
                color = borderColor,
                shape = getChatBubbleShape(isUserMe)  // 修改这里
            )
        ) {
            Column {
                // 消息内容
                if (message.isLoading) {
                    // 加载动画
                    LoadingIndicator()
                }

                if (message.content.isNotEmpty()){
                    MarkdownMessage(
                        message = message,
                        isUserMe = isUserMe,
                        authorClicked = authorClicked
                    )
                }
            }
        }

        // 图片显示
        if (message.imageUrl != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = backgroundBubbleColor,
                shape = getChatBubbleShape(isUserMe),  // 修改这里
                shadowElevation = shadowElevation,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = borderColor,
                    shape = getChatBubbleShape(isUserMe)  // 修改这里
                )
            ) {
                AsyncImage(
                    model = message.imageUrl,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(160.dp),
                    contentDescription = stringResource(id = R.string.attached_image),
                )
            }
        } else if (message.image != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = backgroundBubbleColor,
                shape = getChatBubbleShape(isUserMe),  // 修改这里
                shadowElevation = shadowElevation,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = borderColor,
                    shape = getChatBubbleShape(isUserMe)  // 修改这里
                )
            ) {
                Image(
                    painter = painterResource(message.image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(160.dp),
                    contentDescription = stringResource(id = R.string.attached_image),
                )
            }
        }
    }
}

/**
 * Markdown消息内容渲染 - 完整版
 */
@Composable
fun MarkdownMessage(
    message: Message,
    isUserMe: Boolean,
    authorClicked: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val isSystemMessage = message.author == "System"

    val textColor = when {
        isSystemMessage -> Color.Gray
        isUserMe -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val codeBlockBackground = if (isUserMe) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant  // 改这里：用更深的颜色
    }

    val codeTextColor = if (isUserMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    if (isUserMe) {
        var expanded by remember { mutableStateOf(false) }
        var showExpandButton by remember { mutableStateOf(false) }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message.content,
                color = textColor,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textLayoutResult ->
                    if (!expanded && textLayoutResult.hasVisualOverflow) {
                        showExpandButton = true
                    }
                }
            )

            if (showExpandButton) {
                Text(
                    text = if (expanded) "折叠" else "展开",
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { expanded = !expanded }
                        .padding(top = 4.dp)
                )
            }
        }
    } else {
        Column(modifier = Modifier.padding(16.dp)) {
            SimpleMarkdownRenderer(
                markdown = message.content,
                textColor = textColor,
                codeBlockBackground = codeBlockBackground,
                codeTextColor = codeTextColor,
                onCodeBlockCopy = { code ->
                    clipboardManager.setText(AnnotatedString(code))
                    Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

/**
 * 代码块组件 - 带行号、语法高亮和复制按钮（完美对齐）
 */
@Composable
fun CodeBlockWithCopyButton(
    code: String,
    language: String,
    onCopy: () -> Unit,
    backgroundColor: Color,
    textColor: Color
) {
    // 固定使用 VS Code Dark+ 配色
    val codeBackground = Color(0xFF1E1E1E)
    val topBarBackground = Color(0xFF2D2D2D)
    val lineNumberBackground = Color(0xFF252526)
    val lineNumberColor = Color(0xFF858585)

    val lines = code.split("\n")
    val highlightedCode = highlightCode(code, language.lowercase())
    val codeLines = getHighlightedLines(highlightedCode, lines.size)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Surface(
            color = codeBackground,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // 代码块顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(topBarBackground)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = language.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF858585),
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制代码",
                            tint = Color(0xFF858585),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 代码内容区域（支持横向滚动）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        // 每一行都是一个 Row，包含行号和代码
                        codeLines.forEachIndexed { index, lineText ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                // 行号 - 去掉灰色背景，左对齐
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 20.sp,
                                        fontSize = 14.sp
                                    ),
                                    color = lineNumberColor,
                                    modifier = Modifier
                                        .padding(start = 12.dp, end = 16.dp)
                                        .widthIn(min = 32.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )

                                // 代码内容
                                Text(
                                    text = lineText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 20.sp,
                                        fontSize = 14.sp
                                    ),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 将高亮后的代码按行分割
 */
private fun getHighlightedLines(annotatedString: AnnotatedString, lineCount: Int): List<AnnotatedString> {
    val lines = mutableListOf<AnnotatedString>()
    var currentStart = 0

    // 找到所有换行符的位置
    val text = annotatedString.text
    var lineIndex = 0

    while (lineIndex < lineCount) {
        val nextNewline = text.indexOf('\n', currentStart)

        if (nextNewline != -1) {
            // 提取这一行（不包含换行符）
            lines.add(annotatedString.subSequence(currentStart, nextNewline))
            currentStart = nextNewline + 1
        } else {
            // 最后一行
            if (currentStart < text.length) {
                lines.add(annotatedString.subSequence(currentStart, text.length))
            } else {
                // 空行
                lines.add(AnnotatedString(""))
            }
            break
        }
        lineIndex++
    }

    // 如果还有剩余行（空行）
    while (lines.size < lineCount) {
        lines.add(AnnotatedString(""))
    }

    return lines
}

/**
 * AI思考中的加载动画
 */
@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Markdown渲染器 - 支持代码块分离
 */
@Composable
fun SimpleMarkdownRenderer(
    markdown: String,
    textColor: Color,
    codeBlockBackground: Color,
    codeTextColor: Color,
    onCodeBlockCopy: (String) -> Unit
) {
    val codeBlockRegex = Regex("```([\\w]*)?\\n([\\s\\S]*?)```")
    val matches = codeBlockRegex.findAll(markdown).toList()

    if (matches.isEmpty()) {
        // 没有代码块，渲染带格式的文本
        RenderMarkdownText(markdown, textColor, codeBlockBackground)
    } else {
        // 有代码块，逐段渲染
        var lastIndex = 0

        Column {
            matches.forEach { match ->
                val beforeCode = markdown.substring(lastIndex, match.range.first)
                if (beforeCode.isNotEmpty()) {
                    RenderMarkdownText(beforeCode, textColor, codeBlockBackground)
                }

                val language = match.groupValues[1].takeIf { it.isNotEmpty() } ?: "text"
                val code = match.groupValues[2].trim()
                val lineCount = code.split("\n").size

                Spacer(modifier = Modifier.height(8.dp))

                // 超过50行的代码封装成预览卡片
                if (lineCount > 50) {
                    CodePreviewCard(
                        code = code,
                        language = language,
                        lineCount = lineCount,
                        onCodeBlockCopy = onCodeBlockCopy
                    )
                } else {
                    CodeBlockWithCopyButton(
                        code = code,
                        language = language,
                        onCopy = { onCodeBlockCopy(code) },
                        backgroundColor = codeBlockBackground,
                        textColor = codeTextColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                lastIndex = match.range.last + 1
            }

            val afterCode = markdown.substring(lastIndex)
            if (afterCode.isNotEmpty()) {
                RenderMarkdownText(afterCode, textColor, codeBlockBackground)
            }
        }
    }
}

/**
 * 渲染Markdown文本 - 支持标题、列表、引用、表格等
 */
@Composable
fun RenderMarkdownText(
    markdown: String,
    textColor: Color,
    codeBlockBackground: Color
) {
    val lines = markdown.split("\n")
    var inTable = false
    val tableRows = mutableListOf<List<String>>()

    Column {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()

            // 处理表格
            if (line.contains("|") && line.trim().startsWith("|")) {
                if (!inTable) {
                    inTable = true
                    tableRows.clear()
                }
                tableRows.add(line.split("|").map { it.trim() }.filter { it.isNotEmpty() })
                i++
                continue
            } else if (inTable) {
                // 表格结束，渲染表格卡片
                if (tableRows.size >= 2) {
                    TableCard(
                        tableRows = tableRows,
                        textColor = textColor,
                        codeBlockBackground = codeBlockBackground
                    )
                }
                inTable = false
                tableRows.clear()
            }

            // 处理分隔线
            if (line.matches(Regex("^[-*_]{3,}$"))) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = textColor.copy(alpha = 0.3f)
                )
                i++
                continue
            }

            // 处理标题
            val headerMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val text = headerMatch.groupValues[2]
                Text(
                    text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                    style = when (level) {
                        1 -> MaterialTheme.typography.headlineLarge
                        2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        4 -> MaterialTheme.typography.titleLarge
                        5 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                i++
                continue
            }

            // 处理无序列表
            val unorderedListMatch = Regex("^[*-]\\s+(.+)$").find(line)
            if (unorderedListMatch != null) {
                val text = unorderedListMatch.groupValues[1]
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                i++
                continue
            }

            // 处理有序列表
            val orderedListMatch = Regex("^(\\d+)\\.\\s+(.+)$").find(line)
            if (orderedListMatch != null) {
                val number = orderedListMatch.groupValues[1]
                val text = orderedListMatch.groupValues[2]
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = "$number. ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                i++
                continue
            }

            // 处理引用块
            val quoteMatch = Regex("^>\\s+(.+)$").find(line)
            if (quoteMatch != null) {
                val text = quoteMatch.groupValues[1]
                Surface(
                    color = codeBlockBackground.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .background(textColor.copy(alpha = 0.5f))
                        )
                        Text(
                            text = parseInlineMarkdown(text, textColor, codeBlockBackground),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic
                            ),
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
                        )
                    }
                }
                i++
                continue
            }

            // 处理普通段落
            if (line.isNotEmpty()) {
                Text(
                    text = parseInlineMarkdown(line, textColor, codeBlockBackground),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            i++
        }

        // 如果最后还有未渲染的表格
        if (inTable && tableRows.size >= 2) {
            TableCard(
                tableRows = tableRows,
                textColor = textColor,
                codeBlockBackground = codeBlockBackground
            )
        }
    }
}

/**
 * 渲染Markdown表格 - 现代化 Excel 风格
 */
@Composable
fun RenderTable(
    rows: List<List<String>>,
    textColor: Color,
    codeBlockBackground: Color
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Column {
            rows.forEachIndexed { rowIndex, cells ->
                // 跳过分隔行（第二行通常是 |---|---|）
                if (rowIndex == 1 && cells.all { it.matches(Regex("^:?-+:?$")) }) {
                    return@forEachIndexed
                }

                val isHeader = rowIndex == 0

                // 实际数据行索引（去掉表头后）
                val dataRowIndex = if (rowIndex > 1) rowIndex - 2 else 0

                // 斑马纹背景：表头蓝色，奇数行白色，偶数行浅灰
                val rowBackground = when {
                    isHeader -> MaterialTheme.colorScheme.primary
                    dataRowIndex % 2 == 0 -> Color.White
                    else -> Color(0xFFF8F9FA)  // 浅灰色
                }

                Column {
                    Row(
                        modifier = Modifier
                            .background(rowBackground)
                            .fillMaxWidth()
                    ) {
                        cells.forEachIndexed { cellIndex, cell ->
                            val processedCell = cell
                                .replace(Regex("<br\\s*/?>"), "\n")
                                .replace("&nbsp;", " ")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                                .replace("&amp;", "&")
                                .trim()

                            // 单元格内容
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = parseInlineMarkdown(
                                        processedCell,
                                        if (isHeader) Color.White else textColor,
                                        codeBlockBackground
                                    ),
                                    style = if (isHeader) {
                                        MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    color = if (isHeader) Color.White else textColor,
                                    maxLines = Int.MAX_VALUE,
                                    softWrap = true
                                )
                            }

                            // 列分割线（Excel 风格）
                            if (cellIndex < cells.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(48.dp)
                                        .background(
                                            if (isHeader) {
                                                Color.White.copy(alpha = 0.3f)
                                            } else {
                                                Color(0xFFE0E0E0)
                                            }
                                        )
                                )
                            }
                        }
                    }

                    // 行分割线（不在最后一行后添加）
                    if (rowIndex < rows.size - 1) {
                        HorizontalDivider(
                            color = if (isHeader) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                Color(0xFFE0E0E0)
                            },
                            thickness = if (isHeader) 2.dp else 1.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 表格卡片 - 简洁白色风格
 */
@Composable
fun TableCard(
    tableRows: List<List<String>>,
    textColor: Color,
    codeBlockBackground: Color
) {
    val cachedTableRows = remember(tableRows) { tableRows.toList() }
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { showDialog = true },
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 直接显示图标，无背景
                Text(
                    text = "📊",
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "点击查看表格",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.Black.copy(alpha = 0.87f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${cachedTableRows.size} 行 × ${cachedTableRows.firstOrNull()?.size ?: 0} 列",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            // 右侧眼睛图标 - 蓝色
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_view),
                contentDescription = "查看表格",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showDialog) {
        TableDialog(
            tableRows = cachedTableRows,
            textColor = textColor,
            codeBlockBackground = codeBlockBackground,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 代码预览卡片 - 用于长代码（超过50行）
 */
@Composable
fun CodePreviewCard(
    code: String,
    language: String,
    lineCount: Int,
    onCodeBlockCopy: (String) -> Unit
) {
    val cachedCode = remember(code) { code }
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { showDialog = true },
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 代码图标
                Text(
                    text = "💻",
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "点击查看代码",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.Black.copy(alpha = 0.87f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$language · $lineCount 行",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            // 右侧查看图标
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_view),
                contentDescription = "查看代码",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showDialog) {
        CodeDialog(
            code = cachedCode,
            language = language,
            onDismiss = { showDialog = false },
            onCodeBlockCopy = onCodeBlockCopy
        )
    }
}

/**
 * 代码弹窗 - 全屏显示长代码（现代化悬浮样式）
 */
@Composable
fun CodeDialog(
    code: String,
    language: String,
    onDismiss: () -> Unit,
    onCodeBlockCopy: (String) -> Unit
) {
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8F9FA)  // 浅灰背景，与表格统一
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 代码内容（可滚动）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    item {
                        CodeBlockWithCopyButton(
                            code = code,
                            language = language,
                            onCopy = { onCodeBlockCopy(code) },
                            backgroundColor = Color(0xFF1E1E1E),
                            textColor = Color(0xFFD4D4D4)
                        )
                    }
                }

                // 顶部悬浮工具栏 - 紧凑设计（与表格统一）
                Surface(
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 分享按钮
                        IconButton(onClick = {
                            shareCodeAsFile(context, code, language)
                        }) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_share),
                                contentDescription = "分享",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 关闭按钮
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 表格弹窗 - 全屏显示（现代化悬浮样式）
 */
@Composable
fun TableDialog(
    tableRows: List<List<String>>,
    textColor: Color,
    codeBlockBackground: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8F9FA)  // 浅灰背景，更现代
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 表格内容（可横向和纵向滚动）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            RenderTable(tableRows, textColor, codeBlockBackground)
                        }
                    }
                }

                // 顶部悬浮工具栏 - 紧凑设计
                Surface(
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 分享按钮
                        IconButton(onClick = {
                            shareTableAsFile(context, tableRows)
                        }) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_share),
                                contentDescription = "分享",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // 关闭按钮
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
/**
 * 分享表格为 CSV 文件
 */
private fun shareTableAsFile(context: android.content.Context, tableRows: List<List<String>>) {
    try {
        // 生成 CSV 内容
        val csvContent = convertTableToCSV(tableRows)

        // 创建缓存目录
        val cacheDir = java.io.File(context.cacheDir, "shared")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // 创建 CSV 文件
        val fileName = "table_${System.currentTimeMillis()}.csv"
        val file = java.io.File(cacheDir, fileName)
        file.writeText(csvContent, Charsets.UTF_8)

        // 获取文件 URI
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // 创建分享 Intent
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = android.content.Intent.createChooser(intent, "分享表格")
        context.startActivity(chooserIntent)

    } catch (e: Exception) {
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 分享代码为文件
 */
private fun shareCodeAsFile(
    context: android.content.Context,
    code: String,
    language: String
) {
    try {
        // 根据语言确定文件扩展名
        val extension = when (language.lowercase()) {
            "java" -> "java"
            "kotlin", "kt" -> "kt"
            "python", "py" -> "py"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "html" -> "html"
            "css" -> "css"
            "c" -> "c"
            "cpp", "c++" -> "cpp"
            "swift" -> "swift"
            "go" -> "go"
            "rust", "rs" -> "rs"
            else -> "txt"
        }

        // 创建缓存目录
        val cacheDir = java.io.File(context.cacheDir, "shared")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        // 创建代码文件
        val fileName = "code_${System.currentTimeMillis()}.$extension"
        val file = java.io.File(cacheDir, fileName)
        file.writeText(code, Charsets.UTF_8)

        // 获取文件 URI
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // 创建分享 Intent
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = android.content.Intent.createChooser(intent, "分享代码")
        context.startActivity(chooserIntent)

    } catch (e: Exception) {
        Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 将表格转换为 CSV 格式
 */
private fun convertTableToCSV(tableRows: List<List<String>>): String {
    val stringBuilder = StringBuilder()

    tableRows.forEachIndexed { rowIndex, cells ->
        // 跳过 Markdown 分隔行
        if (rowIndex == 1 && cells.all { it.matches(Regex("^:?-+:?$")) }) {
            return@forEachIndexed
        }

        // 处理每个单元格
        val processedCells = cells.map { cell ->
            val cleanCell = cell
                .replace(Regex("<br\\s*/?>"), " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim()

            // CSV 转义规则：包含逗号、引号或换行的字段用引号括起来
            if (cleanCell.contains(",") || cleanCell.contains("\"") || cleanCell.contains("\n")) {
                "\"${cleanCell.replace("\"", "\"\"")}\""
            } else {
                cleanCell
            }
        }

        stringBuilder.append(processedCells.joinToString(","))
        stringBuilder.append("\n")
    }

    return stringBuilder.toString()
}

/**
 * Markdown元素类型
 */
sealed class MarkdownElement {
    data class Text(val content: String) : MarkdownElement()
    data class CodeBlock(val code: String, val language: String) : MarkdownElement()
}

/**
 * 渲染Markdown内容
 */
fun renderMarkdownContent(content: String, isUserMe: Boolean): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val codeBlockRegex = Regex("```(\\w*)\\n([\\s\\S]*?)```")
    var lastIndex = 0

    codeBlockRegex.findAll(content).forEach { match ->
        // 添加代码块之前的文本
        if (match.range.first > lastIndex) {
            val textContent = content.substring(lastIndex, match.range.first).trim()
            if (textContent.isNotEmpty()) {
                elements.add(MarkdownElement.Text(textContent))
            }
        }

        // 添加代码块
        val language = match.groupValues[1].ifEmpty { "code" }
        val code = match.groupValues[2].trim()
        elements.add(MarkdownElement.CodeBlock(code, language))

        lastIndex = match.range.last + 1
    }

    // 添加剩余文本
    if (lastIndex < content.length) {
        val textContent = content.substring(lastIndex).trim()
        if (textContent.isNotEmpty()) {
            elements.add(MarkdownElement.Text(textContent))
        }
    }

    // 如果没有代码块，返回纯文本
    if (elements.isEmpty() && content.isNotEmpty()) {
        elements.add(MarkdownElement.Text(content))
    }

    return elements
}

/**
 * 解析内联Markdown格式 - 完整版
 */
fun parseInlineMarkdown(
    text: String,
    baseColor: Color,
    codeBlockBackground: Color
): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0

        // 定义所有匹配规则（优先级从高到低）
        val patterns = listOf(
            Regex("\\*\\*(.+?)\\*\\*") to "bold",        // **粗体**
            Regex("__(.+?)__") to "bold",                // __粗体__
            Regex("\\*(.+?)\\*") to "italic",            // *斜体*
            Regex("_(.+?)_") to "italic",                // _斜体_
            Regex("~~(.+?)~~") to "strikethrough",       // ~~删除线~~
            Regex("`(.+?)`") to "code",                  // `行内代码`
            Regex("\\[(.+?)\\]\\((.+?)\\)") to "link"    // [链接](url)
        )

        val allMatches = mutableListOf<Triple<IntRange, String, String>>()

        // 收集所有匹配
        patterns.forEach { (regex, type) ->
            regex.findAll(text).forEach { match ->
                val content = if (type == "link") {
                    match.groupValues[1] // 链接文本
                } else {
                    match.groupValues[1]
                }
                allMatches.add(Triple(match.range, type, content))
            }
        }

        // 按位置排序并去重（避免嵌套冲突）
        val sortedMatches = allMatches
            .sortedBy { it.first.first }
            .fold(mutableListOf<Triple<IntRange, String, String>>()) { acc, match ->
                if (acc.isEmpty() || match.first.first >= acc.last().first.last) {
                    acc.add(match)
                }
                acc
            }

        sortedMatches.forEach { (range, type, content) ->
            // 添加普通文本
            if (currentIndex < range.first) {
                append(text.substring(currentIndex, range.first))
            }

            when (type) {
                "bold" -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(content)
                    }
                }
                "italic" -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(content)
                    }
                }
                "strikethrough" -> {
                    withStyle(SpanStyle(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        color = baseColor
                    )) {
                        append(content)
                    }
                }
                "code" -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBlockBackground.copy(alpha = 0.3f),
                            color = baseColor
                        )
                    ) {
                        append(content)
                    }
                }
                "link" -> {
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF2196F3), // 蓝色链接
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    ) {
                        append(content)
                    }
                }
            }

            currentIndex = range.last + 1
        }

        // 添加剩余文本
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

/**
 * 判断消息内容是否为纯文本
 */
fun isPureTextContent(content: String): Boolean {
    if (content.isEmpty()) return false

    val codeBlockRegex = Regex("```[\\s\\S]*?```")
    if (codeBlockRegex.containsMatchIn(content)) return false

    val tableRegex = Regex("\\|.+\\|")
    if (tableRegex.containsMatchIn(content)) return false

    val imageRegex = Regex("!\\[.*?\\]\\(.*?\\)")
    if (imageRegex.containsMatchIn(content)) return false

    return true
}

/**
 * 代码语法高亮 - VS Code Dark+ 配色
 */
fun highlightCode(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        when (language) {
            "java", "kotlin" -> highlightJavaKotlin(code)
            "python", "py" -> highlightPython(code)
            "javascript", "js", "typescript", "ts" -> highlightJavaScript(code)
            else -> {
                withStyle(SpanStyle(color = Color(0xFFD4D4D4))) {
                    append(code)
                }
            }
        }
    }
}

/**
 * Java/Kotlin 语法高亮
 */
private fun AnnotatedString.Builder.highlightJavaKotlin(code: String) {
    val keywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while", "true", "false", "null",
        // Kotlin 关键字
        "fun", "val", "var", "when", "is", "in", "object", "companion", "data",
        "sealed", "open", "internal", "inline", "suspend", "lateinit", "by"
    )

    val lines = code.split("\n")
    lines.forEachIndexed { index, line ->
        var currentIndex = 0

        // 注释检测
        val commentIndex = line.indexOf("//")
        if (commentIndex >= 0) {
            // 处理注释前的内容
            if (commentIndex > 0) {
                highlightLine(line.substring(0, commentIndex), keywords)
            }
            // 注释部分
            withStyle(SpanStyle(color = Color(0xFF6A9955))) {
                append(line.substring(commentIndex))
            }
        } else {
            highlightLine(line, keywords)
        }

        if (index < lines.size - 1) append("\n")
    }
}

/**
 * Python 语法高亮
 */
private fun AnnotatedString.Builder.highlightPython(code: String) {
    val keywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield", "self", "print"
    )

    val lines = code.split("\n")
    lines.forEachIndexed { index, line ->
        // 注释检测
        val commentIndex = line.indexOf("#")
        if (commentIndex >= 0) {
            if (commentIndex > 0) {
                highlightLine(line.substring(0, commentIndex), keywords)
            }
            withStyle(SpanStyle(color = Color(0xFF6A9955))) {
                append(line.substring(commentIndex))
            }
        } else {
            highlightLine(line, keywords)
        }

        if (index < lines.size - 1) append("\n")
    }
}

/**
 * JavaScript/TypeScript 语法高亮
 */
private fun AnnotatedString.Builder.highlightJavaScript(code: String) {
    val keywords = setOf(
        "abstract", "arguments", "await", "boolean", "break", "byte", "case",
        "catch", "char", "class", "const", "continue", "debugger", "default",
        "delete", "do", "double", "else", "enum", "eval", "export", "extends",
        "false", "final", "finally", "float", "for", "function", "goto", "if",
        "implements", "import", "in", "instanceof", "int", "interface", "let",
        "long", "native", "new", "null", "package", "private", "protected",
        "public", "return", "short", "static", "super", "switch", "synchronized",
        "this", "throw", "throws", "transient", "true", "try", "typeof", "var",
        "void", "volatile", "while", "with", "yield", "async"
    )

    val lines = code.split("\n")
    lines.forEachIndexed { index, line ->
        val commentIndex = line.indexOf("//")
        if (commentIndex >= 0) {
            if (commentIndex > 0) {
                highlightLine(line.substring(0, commentIndex), keywords)
            }
            withStyle(SpanStyle(color = Color(0xFF6A9955))) {
                append(line.substring(commentIndex))
            }
        } else {
            highlightLine(line, keywords)
        }

        if (index < lines.size - 1) append("\n")
    }
}

/**
 * 高亮单行代码
 */
private fun AnnotatedString.Builder.highlightLine(line: String, keywords: Set<String>) {
    val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
    val wordRegex = Regex("\\b\\w+\\b")

    var currentIndex = 0
    val matches = mutableListOf<Triple<IntRange, String, String>>()

    // 收集所有匹配
    stringRegex.findAll(line).forEach { match ->
        matches.add(Triple(match.range, "string", match.value))
    }
    numberRegex.findAll(line).forEach { match ->
        matches.add(Triple(match.range, "number", match.value))
    }
    wordRegex.findAll(line).forEach { match ->
        if (match.value in keywords) {
            matches.add(Triple(match.range, "keyword", match.value))
        }
    }

    // 按位置排序并渲染
    matches.sortedBy { it.first.first }.forEach { (range, type, value) ->
        // 添加前面的普通文本
        if (currentIndex < range.first) {
            withStyle(SpanStyle(color = Color(0xFFD4D4D4))) {
                append(line.substring(currentIndex, range.first))
            }
        }

        // 添加高亮文本
        val color = when (type) {
            "keyword" -> Color(0xFF569CD6)  // 蓝色
            "string" -> Color(0xFFCE9178)   // 橙色
            "number" -> Color(0xFFB5CEA8)   // 浅绿
            else -> Color(0xFFD4D4D4)
        }
        withStyle(SpanStyle(color = color)) {
            append(value)
        }

        currentIndex = range.last + 1
    }

    // 添加剩余文本
    if (currentIndex < line.length) {
        withStyle(SpanStyle(color = Color(0xFFD4D4D4))) {
            append(line.substring(currentIndex))
        }
    }
}

@Preview
@Composable
fun DayHeaderPrev() {
    DayHeader("Aug 6")
}

/**
 * 预览卡片数据类
 */
data class PreviewCard(
    val id: String,
    val type: PreviewCardType,
    val title: String,
    val data: Any,
    val language: String? = null,
    val index: Int
)

enum class PreviewCardType {
    TABLE, CODE
}

/**
 * 右侧预览边栏 - 收集所有表格和代码
 */
@Composable
fun PreviewSidebar(
    previewCards: List<PreviewCard>,
    modifier: Modifier = Modifier,
    uiState: ConversationUiState? = null  // ← 新增参数
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<PreviewCard?>(null) }
    val context = LocalContext.current

    Box(
        modifier = modifier.fillMaxHeight()
    ) {
        if (isExpanded) {
            // 展开状态：显示完整边栏
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 顶部标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "内容",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        IconButton(
                            onClick = { isExpanded = false },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = "收起",
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color.Black.copy(alpha = 0.1f))

                    // 预览卡片列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp)
                    ) {
                        items(previewCards.size) { index ->
                            PreviewCardItem(
                                card = previewCards[index],
                                onClick = {
                                    selectedCard = previewCards[index]
                                    Log.d("PreviewSidebar", "点击卡片: ${previewCards[index].title}")
                                },
                                uiState = uiState  // ← 新增参数
                            )
                        }
                    }
                }
            }
        } else {
            // 收起状态：只显示小按钮
            Surface(
                color = Color.White,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { isExpanded = true }
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_view),
                        contentDescription = "展开预览",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${previewCards.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // 显示选中卡片的弹窗
    selectedCard?.let { card ->
        when (card.type) {
            PreviewCardType.TABLE -> {
                val tableRows = card.data as List<List<String>>
                TableDialog(
                    tableRows = tableRows,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    codeBlockBackground = MaterialTheme.colorScheme.surfaceVariant,
                    onDismiss = { selectedCard = null }
                )
            }
            PreviewCardType.CODE -> {
                val code = card.data as String
                CodeDialog(
                    code = code,
                    language = card.language ?: "text",
                    onDismiss = { selectedCard = null },
                    onCodeBlockCopy = {
                        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("code", it)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/**
 * 单个预览卡片项 - 支持AI生成标题
 */
@Composable
fun PreviewCardItem(
    card: PreviewCard,
    onClick: () -> Unit,
    uiState: ConversationUiState? = null
) {
    // 卡片显示的标题状态（独立状态，每个卡片互不干扰）
    var displayTitle by remember { mutableStateOf(card.title) }

    // ========== 标题生成逻辑 ==========
    LaunchedEffect(card.id) {
        val useCase = uiState?.generateChatNameUseCase
        val provider = uiState?.activeProviderSetting
        val model = uiState?.activeModel

        // 只有在所有必要组件都存在时才生成标题
        if (useCase != null && provider != null && model != null) {
            try {
                // 设置初始状态
                displayTitle = "生成中..."

                // 根据卡片类型提取内容
                val content = when (card.type) {
                    PreviewCardType.TABLE -> {
                        val rows = card.data as List<List<String>>
                        // 提取前3行作为示例
                        val sample = rows.take(3).joinToString("\n") { row ->
                            row.joinToString(" | ")
                        }
                        "请为以下表格生成一个简洁的标题（不超过10个字）：\n$sample"
                    }
                    PreviewCardType.CODE -> {
                        val code = card.data as String
                        val language = card.language ?: "text"
                        // 提取前10行
                        val sample = code.lines().take(10).joinToString("\n")
                        "请为以下${language}代码生成一个简洁的标题（不超过10个字）：\n$sample"
                    }
                }

                Log.d("PreviewCardItem", "开始生成标题，卡片ID: ${card.id}, 类型: ${card.type}")

                // 调用 UseCase 生成标题（完全复用会话标题逻辑）
                val titleFlow = useCase(
                    userMessage = content,
                    providerSetting = provider,
                    model = model,
                    temperature = 0.3f,  // 较低温度，获得稳定标题
                    maxTokens = 30       // 标题不需要太长
                )

                var generatedTitle = StringBuilder()
                titleFlow
                    .onCompletion {
                        val finalTitle = generatedTitle.toString().trim()
                        if (finalTitle.isNotBlank()) {
                            // 限制标题长度为10个字
                            displayTitle = finalTitle.take(10).trim()
                            Log.d("PreviewCardItem", "标题生成完成: $displayTitle")
                        } else {
                            // 生成失败，回退到默认标题
                            displayTitle = card.title
                            Log.d("PreviewCardItem", "标题生成为空，使用默认: ${card.title}")
                        }
                    }
                    .collect { chunk ->
                        // 实时更新标题显示
                        generatedTitle.append(chunk)
                        val currentTitle = generatedTitle.toString().take(10).trim()
                        if (currentTitle.isNotBlank()) {
                            displayTitle = currentTitle
                        }
                    }
            } catch (e: Exception) {
                // 生成失败，回退到默认标题
                displayTitle = card.title
                Log.e("PreviewCardItem", "标题生成失败: ${e.message}", e)
            }
        } else {
            // 没有 UseCase 或 Provider/Model，使用默认标题
            displayTitle = card.title
            Log.d("PreviewCardItem", "缺少生成条件，使用默认标题: ${card.title}")
        }
    }

    // ========== UI 显示 ==========
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color.Black.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            Text(
                text = when (card.type) {
                    PreviewCardType.TABLE -> "📊"
                    PreviewCardType.CODE -> "💻"
                },
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 显示动态生成的标题
            Text(
                text = displayTitle,  // ← 使用动态标题
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // 序号
            Text(
                text = "#${card.index}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = Color.Gray
            )
        }
    }
}
/**
 * 从消息列表中提取预览卡片
 */
private fun extractPreviewCardsFromMessages(messages: List<Message>): List<PreviewCard> {
    val cards = mutableListOf<PreviewCard>()
    var tableIndex = 1
    var codeIndex = 1

    Log.d("PreviewCards", "开始提取，消息数量: ${messages.size}")

    messages.forEach { message ->
        val content = message.content
        Log.d("PreviewCards", "处理消息，内容长度: ${content.length}")

        // 1. 提取代码块
        val codeBlockRegex = Regex("```([\\w]*)?\\n([\\s\\S]*?)```")
        val codeBlocks = codeBlockRegex.findAll(content).toList()
        Log.d("PreviewCards", "找到 ${codeBlocks.size} 个代码块")

        codeBlocks.forEach { match ->
            val language = match.groupValues[1].takeIf { it.isNotEmpty() } ?: "text"
            val code = match.groupValues[2].trim()
            val lineCount = code.lines().size

            if (lineCount >= 50) {
                cards.add(
                    PreviewCard(
                        id = "code_${codeIndex}_${System.currentTimeMillis()}",
                        type = PreviewCardType.CODE,
                        title = "代码",
                        index = codeIndex++,
                        language = language,
                        // ❌ 移除 lineCount 参数
                        data = code
                    )
                )
                Log.d("PreviewCards", "添加代码卡片 #${codeIndex - 1}, 语言: $language, 行数: $lineCount")
            }
        }

        // 2. 提取表格
        val tableLines = content.lines().filter { it.trim().startsWith("|") }
        Log.d("PreviewCards", "找到 ${tableLines.size} 行表格")

        if (tableLines.size >= 2) {
            // 过滤掉分隔符行（如 |---|---|）
            val validTableLines = tableLines.filter { line ->
                !line.replace("|", "").replace("-", "").replace(":", "").trim().isEmpty()
            }

            if (validTableLines.isNotEmpty()) {
                // 解析表格为 List<List<String>>
                val tableRows = validTableLines.map { line ->
                    line.split("|")
                        .filter { it.isNotBlank() }
                        .map { it.trim() }
                }

                cards.add(
                    PreviewCard(
                        id = "table_${tableIndex}_${System.currentTimeMillis()}",
                        type = PreviewCardType.TABLE,
                        title = "表格",
                        index = tableIndex++,
                        data = tableRows
                    )
                )
                Log.d("PreviewCards", "添加表格卡片 #${tableIndex - 1}, 行数: ${tableRows.size}, 列数: ${tableRows.firstOrNull()?.size ?: 0}")
            }
        }
    }

    Log.d("PreviewCards", "最终提取 ${cards.size} 个卡片")
    return cards
}
private val JumpToBottomThreshold = 56.dp