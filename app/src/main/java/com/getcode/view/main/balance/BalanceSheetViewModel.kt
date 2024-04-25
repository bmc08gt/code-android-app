package com.getcode.view.main.balance

import androidx.lifecycle.viewModelScope
import com.getcode.model.Chat
import com.getcode.model.PrefsBool
import com.getcode.model.Rate
import com.getcode.network.BalanceController
import com.getcode.network.HistoryController
import com.getcode.network.repository.BetaFlagsRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class BalanceSheetViewModel @Inject constructor(
    balanceController: BalanceController,
    historyController: HistoryController,
    prefsRepository: PrefRepository,
    networkObserver: NetworkConnectivityListener,
    betaFlags: BetaFlagsRepository,
) : BaseViewModel2<BalanceSheetViewModel.State, BalanceSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val amountText: String = "",
        val marketValue: Double = 0.0,
        val selectedRate: Rate? = null,
        val currencyFlag: Int? = null,
        val chatsLoading: Boolean = false,
        val chats: List<Chat> = emptyList(),
        val isBucketDebuggerEnabled: Boolean = false,
        val isBucketDebuggerVisible: Boolean = false,
        val isBuyKinEnabled: Boolean = false,
    )

    sealed interface Event {
        data class OnDebugBucketsEnabled(val enabled: Boolean) : Event
        data class OnDebugBucketsVisible(val show: Boolean) : Event
        data class OnBuyKinEnabled(val enabled: Boolean): Event
        data class OnLatestRateChanged(
            val rate: Rate,
            ) : Event

        data class OnBalanceChanged(
            val flagResId: Int?,
            val marketValue: Double,
            val display: String,
        ) : Event

        data class OnChatsLoading(val loading: Boolean) : Event
        data class OnChatsUpdated(val chats: List<Chat>) : Event
    }

    init {
        betaFlags.observe()
            .distinctUntilChanged()
            .onEach {
                dispatchEvent(Dispatchers.Main, Event.OnBuyKinEnabled(it.buyModuleEnabled))
            }.launchIn(viewModelScope)

        prefsRepository.observeOrDefault(PrefsBool.BUCKET_DEBUGGER_ENABLED, false)
            .distinctUntilChanged()
            .onEach { enabled ->
                dispatchEvent(Dispatchers.Main, Event.OnDebugBucketsEnabled(enabled))
            }.launchIn(viewModelScope)

        balanceController.formattedBalance
            .onEach { Timber.d("b=$it") }
            .filterNotNull()
            .onEach {
                dispatchEvent(
                    Dispatchers.Main,
                    Event.OnBalanceChanged(
                        flagResId = it.flag,
                        marketValue = it.marketValue,
                        display = it.formattedValue
                    )
                )
            }.launchIn(viewModelScope)

        historyController.chats
            .onEach {
                if (it == null || (it.isEmpty() && !networkObserver.isConnected)) {
                    dispatchEvent(Dispatchers.Main, Event.OnChatsLoading(true))
                }
            }
            .map { chats ->
                when {
                    chats == null -> null // await for confirmation it's empty
                    chats.isEmpty() && !networkObserver.isConnected -> null // remain loading while disconnected
                    chats.any { it.messages.isEmpty() } -> null // remain loading while fetching messages
                    else -> chats
                }
            }
            .filterNotNull()
            .onEach { update ->
                dispatchEvent(Dispatchers.Main, Event.OnChatsUpdated(update))
            }.onEach {
                dispatchEvent(Dispatchers.Main, Event.OnChatsLoading(false))
            }.launchIn(viewModelScope)
    }

    companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnDebugBucketsEnabled -> { state ->
                    state.copy(isBucketDebuggerEnabled = event.enabled)
                }

                is Event.OnDebugBucketsVisible -> { state ->
                    state.copy(isBucketDebuggerVisible = event.show)
                }

                is Event.OnBuyKinEnabled -> { state ->
                    state.copy(isBuyKinEnabled = event.enabled)
                }

                is Event.OnLatestRateChanged -> { state ->
                    state.copy(selectedRate = event.rate)
                }

                is Event.OnBalanceChanged -> { state ->
                    state.copy(
                        currencyFlag = event.flagResId,
                        marketValue = event.marketValue,
                        amountText = event.display,
                    )
                }
                is Event.OnChatsLoading -> { state ->
                    state.copy(chatsLoading = event.loading)
                }
                is Event.OnChatsUpdated -> { state ->
                    state.copy(chats = event.chats)
                }
            }
        }
    }
}