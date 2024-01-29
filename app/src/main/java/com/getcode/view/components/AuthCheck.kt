package com.getcode.view.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.LocalDeeplinks
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SessionManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.screens.AccessKeyLoginScreen
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.screens.LoginGraph
import com.getcode.navigation.screens.LoginScreen
import com.getcode.util.DeeplinkHandler
import com.getcode.util.getActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

private const val APP_STARTUP_TAG = "app-startup"
private typealias DeeplinkFlowState = Pair<Pair<DeeplinkHandler.Type, List<Screen>>, SessionManager.SessionState>

@Composable
fun AuthCheck(
    navigator: CodeNavigator,
    onNavigate: (List<Screen>) -> Unit,
    onSwitchAccounts: (String) -> Unit,
) {
    val deeplinkHandler = LocalDeeplinks.current
    val dataState by SessionManager.authState.collectAsState()

    val isAuthenticated = dataState.isAuthenticated
    val currentRoute = navigator.lastItem

    var deeplinkRouted by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(isAuthenticated) {
        isAuthenticated?.let { authenticated ->
            if (!deeplinkRouted) {
                // Allow the seed input screen to complete and avoid
                // premature navigation
                if (currentRoute is AccessKeyLoginScreen) {
                    startupLog("No navigation within seed input")
                    return@LaunchedEffect
                }
                if (currentRoute is LoginGraph) {
                    startupLog("No navigation within account creation and onboarding")
                } else {
                    if (authenticated) {
                        startupLog("Navigating to home")
                        onNavigate(listOf(HomeScreen()))
                    } else {
                        startupLog("Navigating to login")
                        onNavigate(listOf(LoginScreen()))
                    }
                }
            } else {
                deeplinkRouted = false
            }
        }
    }

    val context = LocalContext.current
    deeplinkHandler ?: return

    LaunchedEffect(deeplinkHandler) {
        val scope = this
        deeplinkHandler.intent
            .flatMapLatest { combine(flowOf(deeplinkHandler.handle(it)), SessionManager.authState) { a, b -> a to b } }
            .filter { (result, authState) ->
                startupLog("checking auth state=${authState.isAuthenticated}")
                // wait for authentication
                return@filter result != null && authState.isAuthenticated == true
            }.mapNotNull { (result, state) ->
                result ?: return@mapNotNull null
                result to state
            }
            .mapSeedToHome()
            .map { it.first }
            .onEach { (_, screens) ->
                deeplinkRouted = true
                startupLog("navigating from deep link")
                onNavigate(screens)
                deeplinkHandler.debounceIntent = null
                context.getActivity()?.intent = null
            }
            .showLogoutConfirmationIfNeeded(
                context = context,
                scope = scope,
                onSwitchAccounts = {
                    onSwitchAccounts(it)
                    deeplinkRouted = false
                },
                onCancel = {
                    deeplinkRouted = false
                }
            )
            .launchIn(this)
    }
}

fun startupLog(message: String) = Timber.tag(APP_STARTUP_TAG).d(message)

private fun Flow<DeeplinkFlowState>.mapSeedToHome(): Flow<DeeplinkFlowState> =
    map { (data, auth) ->
        startupLog("checking type")
        val (type, screens) = data
        if (type is DeeplinkHandler.Type.Login && auth.isAuthenticated == true) {
            // send the user to home screen
            val entropy = (screens.first() as? LoginScreen)?.seed
            val updatedData = type to listOf(HomeScreen(seed = entropy))
            updatedData to auth
        } else {
            data to auth
        }
    }


private fun Flow<Pair<DeeplinkHandler.Type, List<Screen>>>.showLogoutConfirmationIfNeeded(
    context: Context,
    scope: CoroutineScope,
    onSwitchAccounts: (String) -> Unit,
    onCancel: () -> Unit
): Flow<Pair<DeeplinkHandler.Type, List<Screen>>> = onEach { (type, screens) ->
    if (type is DeeplinkHandler.Type.Login) {
        val entropy = (screens.first() as? HomeScreen)?.seed
        startupLog("showing logout confirm")
        if (entropy != null) {
            showLogoutMessage(
                context = context,
                entropyB64 = entropy,
                onSwitchAccounts = {
                    scope.launch {
                        delay(300) // wait for dismiss
                        onSwitchAccounts(it)
                    }
                },
                onCancel = onCancel
            )
        }
    }
}

private fun showLogoutMessage(
    context: Context,
    entropyB64: String,
    onSwitchAccounts: (String) -> Unit,
    onCancel: () -> Unit,
) {
    BottomBarManager.showMessage(
        BottomBarManager.BottomBarMessage(
            title = context.getString(R.string.subtitle_logoutAndLoginConfirmation),
            positiveText = context.getString(R.string.action_logIn),
            negativeText = context.getString(R.string.action_cancel),
            isDismissible = false,
            onPositive = {
                onSwitchAccounts(entropyB64)
            },
            onNegative = { onCancel() }
        )
    )
}
