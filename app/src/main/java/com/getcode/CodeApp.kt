package com.getcode

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.navigation.core.BottomSheetNavigator
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.CombinedNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.screens.MainRoot
import com.getcode.navigation.transitions.SheetSlideTransition
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.theme.LocalCodeColors
import com.getcode.util.LocalDeeplinks
import com.getcode.view.components.AuthCheck
import com.getcode.view.components.BottomBarContainer
import com.getcode.view.components.CodeScaffold
import com.getcode.view.components.TitleBar
import com.getcode.view.components.TopBarContainer

@Composable
fun CodeApp() {
    CodeTheme {
        val appState = rememberCodeAppState()
        AppNavHost {
            CodeScaffold(
                backgroundColor = Brand,
                scaffoldState = appState.scaffoldState
            ) { innerPaddingModifier ->
                Navigator(
                    screen = MainRoot,
                ) { navigator ->
                    val codeNavigator = LocalCodeNavigator.current
                    LaunchedEffect(navigator.lastItem) {
                        // update global navigator for platform access to support push/pop from a single
                        // navigator current
                        codeNavigator.screensNavigator = navigator
                    }

                    val (isVisibleTopBar, isVisibleBackButton) = appState.isVisibleTopBar
                    if (isVisibleTopBar && appState.currentTitle.isNotBlank()) {
                        TitleBar(
                            title = appState.currentTitle,
                            backButton = isVisibleBackButton,
                            onBackIconClicked = appState::upPress
                        )
                    }

                    Box(modifier = Modifier.padding(innerPaddingModifier)) {
                        if (navigator.lastItem is LoginScreen) {
                            CrossfadeTransition(navigator = navigator)
                        } else {
                            SlideTransition(navigator = navigator)
                        }
                    }

                    //Listen for authentication changes here
                    AuthCheck(
                        navigator = appState.navigator,
                        onNavigate = {
                            codeNavigator.replaceAll(it, inSheet = false)
                        }
                    )
                }
            }
        }

        TopBarContainer(appState)
        BottomBarContainer(appState)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppNavHost(content: @Composable () -> Unit) {
    var combinedNavigator by remember {
        mutableStateOf<CombinedNavigator?>(null)
    }
    BottomSheetNavigator(
        modifier = Modifier.fillMaxSize(),
        sheetBackgroundColor = LocalCodeColors.current.background,
        sheetContentColor = LocalCodeColors.current.onBackground,
        sheetContent = { sheetNav ->
            combinedNavigator = combinedNavigator?.apply { sheetNavigator = sheetNav }
                ?: CombinedNavigator(sheetNav)
            combinedNavigator?.let {
                CompositionLocalProvider(LocalCodeNavigator provides it) {
                    if (sheetNav.isVisible) {
                        SheetSlideTransition(navigator = it.sheetNavigator)
                    } else {
                        CurrentScreen()
                    }
                }
            }

        }
    ) { sheetNav ->
        combinedNavigator =
            combinedNavigator?.apply { sheetNavigator = sheetNav } ?: CombinedNavigator(sheetNav)
        combinedNavigator?.let {
            CompositionLocalProvider(LocalCodeNavigator provides it) {
                content()
            }
        }
    }
}

@Composable
private fun CrossfadeTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() }
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        content = content,
        transition = { fadeIn() togetherWith fadeOut() }
    )
}