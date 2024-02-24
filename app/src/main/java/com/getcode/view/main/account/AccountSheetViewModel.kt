package com.getcode.view.main.account

import android.app.Activity
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.getcode.R
import com.getcode.analytics.AnalyticsService
import com.getcode.db.Database
import com.getcode.manager.AuthManager
import com.getcode.model.PrefsBool
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import javax.inject.Inject

data class AccountMainItem(
    val type: AccountPage,
    val name: Int,
    val icon: Int,
    val isPhoneLinked: Boolean? = null,
)

enum class AccountPage {
    BUY_AND_SELL_KIN,
    DEPOSIT,
    WITHDRAW,
    PHONE,
    DELETE_ACCOUNT,
    ACCESS_KEY,
    FAQ,
    ACCOUNT_DETAILS,
    ACCOUNT_DEBUG_OPTIONS,
    LOGOUT
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountSheetViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val prefRepository: PrefRepository,
    private val analytics: AnalyticsService,
    phoneRepository: PhoneRepository,
) : BaseViewModel2<AccountSheetViewModel.State, AccountSheetViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    @Stable
    data class State(
        val logoClickCount: Int = 0,
        val items: List<AccountMainItem> = emptyList(),
        val isHome: Boolean = true,
        val page: AccountPage? = null,
        val isPhoneLinked: Boolean = false,
        val isDebug: Boolean = false
    )

    sealed interface Event {
        data class OnPhoneLinked(val linked: Boolean) : Event
        data class OnDebugChanged(val isDebug: Boolean) : Event
        data object LogoClicked : Event
        data class Navigate(val page: AccountPage) : Event
        data class OnItemsChanged(val items: List<AccountMainItem>) : Event
    }

    // TODO: handle this differently
    fun logout(activity: Activity) {
        authManager.logout(activity, onComplete = {})
    }

    init {
        prefRepository
            .observeOrDefault(PrefsBool.IS_DEBUG_ACTIVE, false)
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
            .onEach {
                dispatchEvent(Dispatchers.Main, Event.OnDebugChanged(it))
            }.launchIn(viewModelScope)

        phoneRepository
            .phoneLinked
            .onEach { dispatchEvent(Event.OnPhoneLinked(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LogoClicked>()
            .map { stateFlow.value.logoClickCount }
            .filter { it >= 10 }
            .map { stateFlow.value.isDebug }
            .onEach {
                prefRepository.set(PrefsBool.IS_DEBUG_ACTIVE, !it)
            }.launchIn(viewModelScope)
    }

    companion object {
        private val fullItemSet = listOf(
            AccountMainItem(
                type = AccountPage.BUY_AND_SELL_KIN,
                name = R.string.title_buyAndSellKin,
                icon = R.drawable.ic_currency_dollar_active
            ),
            AccountMainItem(
                type = AccountPage.DEPOSIT,
                name = R.string.title_depositKin,
                icon = R.drawable.ic_menu_deposit
            ),
            AccountMainItem(
                type = AccountPage.WITHDRAW,
                name = R.string.title_withdrawKin,
                icon = R.drawable.ic_menu_withdraw
            ),
            AccountMainItem(
                type = AccountPage.ACCOUNT_DETAILS,
                name = R.string.title_myAccount,
                icon = R.drawable.ic_menu_account
            ),
            AccountMainItem(
                type = AccountPage.ACCOUNT_DEBUG_OPTIONS,
                name = R.string.title_betaFlags,
                icon = R.drawable.ic_bug,
            ),
            AccountMainItem(
                type = AccountPage.FAQ,
                name = R.string.title_faq,
                icon = R.drawable.ic_faq,
            ),
            AccountMainItem(
                type = AccountPage.LOGOUT,
                name = R.string.action_logout,
                icon = R.drawable.ic_menu_logout
            )
        )

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            Timber.d("event=$event")
            when (event) {
                is Event.OnPhoneLinked -> { state -> state.copy(isPhoneLinked = event.linked) }
                Event.LogoClicked -> { state ->
                    val count = state.logoClickCount + 1
                    state.copy(logoClickCount = count)
                }

                is Event.OnDebugChanged -> { state ->
                    val items = when {
                        event.isDebug -> fullItemSet
                        else -> fullItemSet.filter { it.type != AccountPage.ACCOUNT_DEBUG_OPTIONS }
                    }

                    state.copy(
                        isDebug = event.isDebug,
                        items = items,
                        logoClickCount = 0
                    )
                }

                is Event.OnItemsChanged -> { state -> state.copy(items = event.items) }
                is Event.Navigate -> { state -> state }
            }
        }
    }
}