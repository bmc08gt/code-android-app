package com.getcode.ui.components.conversation.utils

import android.os.Build
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.compose.LazyPagingItems
import com.getcode.model.ConversationMessage
import com.getcode.model.ConversationMessageContent
import com.getcode.ui.utils.isScrolledToTheBeginning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@Composable
internal fun HandleMessageChanges(
    listState: LazyListState,
    items: LazyPagingItems<ConversationMessage>,
) {
    var lastMessageSent by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var lastMessageReceived by rememberSaveable {
        mutableLongStateOf(0L)
    }

    // handle incoming/outgoing messages - scroll to bottom to reset view in the following circumstances:
    // 1) New message is from self (e.g outgoing)
    // 2) New message is from participant and we are already at the bottom (to prevent rug pull)
    LaunchedEffect(Unit) {
        snapshotFlow { items.itemSnapshotList }
            .map { it.firstOrNull() }
            .filterNotNull()
            .distinctUntilChangedBy { it.dateMillis }
            .filter { it.content is ConversationMessageContent.Text }
            .collect { newMessage ->
                val content = newMessage.content as? ConversationMessageContent.Text ?: return@collect
                if (content.isFromSelf && newMessage.dateMillis > lastMessageSent) {
                    listState.handleAndReplayAfter(300) {
                        scrollToItem(0)
                        lastMessageSent = newMessage.dateMillis
                    }
                } else {
                    listState.handleAndReplayAfter(300) {
                        if (listState.isScrolledToTheBeginning() && newMessage.dateMillis > lastMessageReceived) {
                            // Android 10 (specifically the S1?) we have to utilize a mimic for IME nested scrolling
                            // using the [LazyListState#isScrollInProgress] which animateScrollToItem triggers
                            // thus causing the IME to be dismissed when we trigger the sync.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                listState.scrollToItem(0)
                            } else {
                                listState.animateScrollToItem(0)
                            }
                        }
                        lastMessageReceived = newMessage.dateMillis
                    }
                }
            }
    }
}

private suspend fun LazyListState.handleAndReplayAfter(
    delay: Long,
    block: suspend LazyListState.() -> Unit
) {
    block()
    delay(delay)
    block()
}