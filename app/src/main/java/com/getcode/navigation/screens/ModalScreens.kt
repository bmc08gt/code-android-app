package com.getcode.navigation.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.analytics.AnalyticsScreenWatcher
import com.getcode.manager.AnalyticsManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.util.getActivityScopedViewModel
import com.getcode.util.getStackScopedViewModel
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.login.PhoneVerify
import com.getcode.view.login.PhoneVerifyViewModel
import com.getcode.view.main.account.AccountAccessKey
import com.getcode.view.main.account.AccountDebugOptions
import com.getcode.view.main.account.AccountDeposit
import com.getcode.view.main.account.AccountDetails
import com.getcode.view.main.account.AccountFaq
import com.getcode.view.main.account.AccountPhone
import com.getcode.view.main.account.ConfirmDeleteAccount
import com.getcode.view.main.account.DeleteCodeAccount
import com.getcode.view.main.currency.CurrencySelectionSheet
import com.getcode.view.main.getKin.BuyAndSellKin
import com.getcode.view.main.getKin.ReferFriend
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data object BuySellScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is BuySellScreen }) {
            BuyAndSellKin(getViewModel())
        }
    }
}

@Parcelize
data object DepositKinScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_depositKin)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is DepositKinScreen }) {
            AccountDeposit()
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Deposit
        )
    }
}

@Parcelize
data object FaqScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_faq)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is FaqScreen }) {
            AccountFaq(getViewModel())
        }

        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Faq
        )
    }
}

@Parcelize
data object AccountDebugOptionsScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_myAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is AccountDebugOptionsScreen }) {
            AccountDebugOptions(getViewModel())
        }
    }
}

@Parcelize
data object AccountDetailsScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_myAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is AccountDetailsScreen }) {
            AccountDetails(getActivityScopedViewModel())
        }
    }
}

@Parcelize
data object AccountAccessKeyScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_accessKey)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is AccountAccessKeyScreen }) {
            AccountAccessKey(getViewModel())
        }


        AnalyticsScreenWatcher(
            lifecycleOwner = LocalLifecycleOwner.current,
            event = AnalyticsManager.Screen.Backup
        )
    }
}

@Parcelize
data object PhoneNumberScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_phoneNumber)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is PhoneNumberScreen }) {
            AccountPhone(getViewModel())
        }
    }
}

@Parcelize
data class PhoneVerificationScreen(
    val arguments: LoginArgs = LoginArgs()
) : MainGraph, ModalContent {
    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_enterPhoneNumber)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val viewModel = getStackScopedViewModel<PhoneVerifyViewModel>(key)
        ModalContainer(backButton = { it is PhoneVerificationScreen }) {
            PhoneVerify(viewModel, arguments) {
                navigator.show(PhoneAreaSelectionModal(key))
            }
        }
    }
}

@Parcelize
data class PhoneAreaSelectionModal(val providedKey: String) : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_selectCountry)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val vm = getStackScopedViewModel<PhoneVerifyViewModel>(providedKey)

        ModalContainer(closeButton = { it is PhoneAreaSelectionModal }) {
            PhoneCountrySelection(viewModel = vm) {
                navigator.hide()
            }
        }
    }
}

@Parcelize
data class PhoneConfirmationScreen(
    val arguments: LoginArgs = LoginArgs()
) : MainGraph, ModalContent {
    constructor(
        signInEntropy: String? = null,
        isPhoneLinking: Boolean = false,
        isNewAccount: Boolean = false,
        phoneNumber: String? = null,
    ) : this(LoginArgs(signInEntropy, isPhoneLinking, isNewAccount, phoneNumber))

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_verifyPhoneNumber)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is PhoneConfirmationScreen }) {
            PhoneConfirm(
                getViewModel(),
                arguments = arguments,
            )
        }
    }
}


@Parcelize
data object DeleteCodeScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_deleteAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is DeleteCodeScreen }) {
            DeleteCodeAccount()
        }
    }
}

@Parcelize
data object DeleteConfirmationScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_deleteAccount)

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is DeleteConfirmationScreen }) {
            ConfirmDeleteAccount(getViewModel())
        }
    }
}

@Parcelize
data object ReferFriendScreen : MainGraph, ModalContent {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        ModalContainer(backButton = { it is DeleteConfirmationScreen }) {
            ReferFriend()
        }
    }
}

@Parcelize
data object CurrencySelectionModal: MainGraph, ModalRoot {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    override val name: String
        @Composable get() = stringResource(id = R.string.title_selectCurrency)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        ModalContainer(
            closeButton = {
                if (navigator.isVisible) {
                    it is CurrencySelectionModal
                } else {
                    navigator.progress > 0f
                }
            }
        ) {
            CurrencySelectionSheet(viewModel = getActivityScopedViewModel())
        }
    }
}