package com.getcode.view.main.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.Brand
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White50
import com.getcode.theme.inputColors
import com.getcode.util.RepeatOnLifecycle
import com.getcode.view.components.CodeCircularProgressIndicator
import com.getcode.view.components.SwipeableView
import com.getcode.view.main.giveKin.CurrencyListItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CurrencySelectionSheet(
    viewModel: CurrencyViewModel,
) {
    Timber.d("currency screen")
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsState()

    var searchQuery by remember {
        mutableStateOf(TextFieldValue())
    }

    LaunchedEffect(searchQuery, state.currencySearchText) {
        if (searchQuery.text != state.currencySearchText) {
            viewModel.dispatchEvent(CurrencyViewModel.Event.OnSearchQueryChanged(searchQuery.text))
        }
    }

    RepeatOnLifecycle(targetState = Lifecycle.State.RESUMED) {
        viewModel.eventFlow
            .filterIsInstance<CurrencyViewModel.Event.OnSelectedCurrencyChanged>()
            .filter { it.fromUser }
            .map { it.currency }
            .distinctUntilChanged()
            .onEach {
                navigator.hideWithResult(it)
            }.launchIn(this)
    }

    Column(
        modifier = Modifier.imePadding()
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 10.dp)
                .padding(horizontal = 15.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = White50,
                ) },
            placeholder = { Text(
                stringResource(id = R.string.subtitle_searchCurrencies),
                style = CodeTheme.typography.subtitle1.copy(
                    fontSize = 16.sp,
                )
            ) },
            trailingIcon = {
                if (searchQuery.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchQuery = TextFieldValue()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = White50,
                        )
                    }
                }
            },
            value = searchQuery,
            onValueChange = { searchQuery = it },
            textStyle = CodeTheme.typography.subtitle1.copy(
                fontSize = 16.sp,
            ),
            singleLine = true,
            colors = inputColors(),
            shape = RoundedCornerShape(size = 5.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand)
                .wrapContentHeight()
        ) {
            if (state.loading) {
                item {
                    Box(Modifier.fillParentMaxSize()) {
                        CodeCircularProgressIndicator(Modifier.align(Alignment.TopCenter))
                    }
                }
            }

            items(state.listItems) { listItem ->
                val isDisabled = listItem is CurrencyListItem.RegionCurrencyItem && listItem.currency.rate <= 0
                val currencyCode = when (listItem) {
                    is CurrencyListItem.RegionCurrencyItem -> listItem.currency.code
                    else -> ""
                }

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(if (listItem !is CurrencyListItem.TitleItem) 70.dp else 60.dp)
                ) {
                    Divider(
                        color = White05,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .align(Alignment.BottomCenter)
                    )

                    when (listItem) {
                        is CurrencyListItem.TitleItem -> {
                            Row(modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    modifier = Modifier.padding(bottom = 10.dp),
                                    style = CodeTheme.typography.caption.copy(
                                        fontSize = 14.sp,
                                    ),
                                    color = BrandLight,
                                    text = listItem.text
                                )
                            }
                        }
                        is CurrencyListItem.RegionCurrencyItem -> {
                            SwipeableView(
                                isSwipeEnabled = listItem.isRecent,
                                leftSwiped = {
                                    viewModel.dispatchEvent(CurrencyViewModel.Event.OnRecentCurrencyRemoved(listItem.currency))
                                },
                                leftSwipeCard = {
                                    if (listItem.isRecent) ListSwipeDeleteCard()
                                }
                            ) {
                                ListRowItem(
                                    listItem.currency.resId,
                                    listItem.currency.name,
                                    true,
                                    state.selectedCurrencyCode.orEmpty()  == currencyCode,
                                    isDisabled,
                                ) {
                                    viewModel.dispatchEvent(CurrencyViewModel.Event.OnSelectedCurrencyChanged(listItem.currency))
                                }

                                Divider(
                                    color = White05,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}