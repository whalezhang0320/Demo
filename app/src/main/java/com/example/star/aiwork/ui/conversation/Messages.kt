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
    isGenerating: Boolean = false
) {
    val coroutineScope = scope ?: rememberCoroutineScope()
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
        // 用户消息：右对齐，无头像
        if (!isUserMe) {
            // AI消息：左对齐，显示头像
            if (isLastMessageByAuthor) {
                Image(
                    modifier = Modifier
                        .clickable(onClick = { onAuthorClick(msg.author) })
                        .padding(horizontal = 16.dp)
                        .size(42.dp)
                        .border(1.5.dp, borderColor, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clip(CircleShape)
                        .align(Alignment.Top),
                    painter = painterResource(id = msg.authorImage),
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                )
            } else {
                Spacer(modifier = Modifier.width(74.dp))
            }
        }

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
                .padding(end = if (isUserMe) 16.dp else 16.dp)
                .widthIn(max = 300.dp)
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
            AuthorNameTimestamp(msg)
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
 * 作者名和时间戳
 */
@Composable
private fun AuthorNameTimestamp(msg: Message) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = msg.author,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .alignBy(LastBaseline)
                .paddingFrom(LastBaseline, after = 8.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = msg.timestamp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alignBy(LastBaseline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ChatBubbleShape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)

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
 * 消息气泡 - 支持Markdown渲染、智能复制、加载动画
 */
@Composable
fun ChatItemBubble(
    message: Message,
    isUserMe: Boolean,
    authorClicked: (String) -> Unit
) {
    val isSystemMessage = message.author == "System"

    val backgroundBubbleColor = when {
        isSystemMessage -> MaterialTheme.colorScheme.errorContainer // 淡红色背景
        isUserMe -> MaterialTheme.colorScheme.primaryContainer // 淡蓝色背景
        else -> MaterialTheme.colorScheme.surfaceContainer // 淡灰色背景
    }

    Column {
        Surface(
            color = backgroundBubbleColor,
            shape = ChatBubbleShape,
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
                shape = ChatBubbleShape,
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
                shape = ChatBubbleShape,
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
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
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
 * 代码块组件 - 带复制按钮
 */
@Composable
fun CodeBlockWithCopyButton(
    code: String,
    language: String,
    onCopy: () -> Unit,
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // 代码块顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制代码",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = textColor.copy(alpha = 0.1f),
                    thickness = 1.dp
                )

                // 代码内容
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = textColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        }
    }
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

                Spacer(modifier = Modifier.height(8.dp))
                CodeBlockWithCopyButton(
                    code = code,
                    language = language,
                    onCopy = { onCodeBlockCopy(code) },
                    backgroundColor = codeBlockBackground,
                    textColor = codeTextColor
                )
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
                // 表格结束，渲染表格
                if (tableRows.size >= 2) {
                    RenderTable(tableRows, textColor, codeBlockBackground)
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
            RenderTable(tableRows, textColor, codeBlockBackground)
        }
    }
}

/**
 * 渲染Markdown表格
 */
@Composable
fun RenderTable(
    rows: List<List<String>>,
    textColor: Color,
    codeBlockBackground: Color
) {
    Surface(
        color = codeBlockBackground.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            rows.forEachIndexed { rowIndex, cells ->
                // 跳过分隔行（第二行通常是 |---|---|）
                if (rowIndex == 1 && cells.all { it.matches(Regex("^:?-+:?$")) }) {
                    HorizontalDivider(color = textColor.copy(alpha = 0.3f))
                    return@forEachIndexed
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = parseInlineMarkdown(cell, textColor, codeBlockBackground),
                            style = if (rowIndex == 0) {
                                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
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

@Preview
@Composable
fun DayHeaderPrev() {
    DayHeader("Aug 6")
}

private val JumpToBottomThreshold = 56.dp
