package com.getcode.ui.components.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.rememberTextFieldState
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getcode.theme.ChatOutgoing
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.theme.inputColors
import com.getcode.ui.components.TextInput
import com.getcode.ui.utils.withTopBorder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatInput(
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    onSend: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .withTopBorder()
            .padding(CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.Bottom
    ) {
        TextInput(
            modifier = Modifier
                .weight(1f),
            minHeight = 40.dp,
            state = state,
            shape = CodeTheme.shapes.extraLarge,
            contentPadding = PaddingValues(8.dp),
            colors = inputColors(
                backgroundColor = Color.White,
                textColor = CodeTheme.colors.background,
                cursorColor = CodeTheme.colors.brand,
            )
        )
        AnimatedContent(
            targetState = state.text.isNotEmpty(),
            label = "show/hide send button",
            transitionSpec = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start
                ) togetherWith slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End
                )
            }
        ) { show ->
            if (show) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .background(ChatOutgoing, shape = CircleShape)
                        .clip(CircleShape)
                        .clickable { onSend() }
                        .size(ChatInput_Size)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier
                            .size(CodeTheme.dimens.staticGrid.x6),
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        tint = Color.White,
                        contentDescription = "Send message"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
private fun Preview_ChatInput() {
    CodeTheme {
        ChatInput {

        }
    }
}

private val ChatInput_Size
    @Composable get() = CodeTheme.dimens.grid.x8