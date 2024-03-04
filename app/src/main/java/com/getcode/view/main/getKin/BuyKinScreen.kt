package com.getcode.view.main.getKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.LocalNetworkObserver
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.displayLarge
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.CodeKeyPad
import com.getcode.ui.components.Row
import com.getcode.util.showNetworkError
import com.getcode.utils.ErrorUtils
import com.getcode.view.main.giveKin.AmountArea

@Composable
fun BuyKinScreen(
    viewModel: BuyKinViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dataState by viewModel.state.collectAsState()

    val networkObserver = LocalNetworkObserver.current
    val networkState by networkObserver.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = CodeTheme.dimens.grid.x4),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.weight(0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
            ) {
                AmountArea(
                    amountPrefix = dataState.amountModel.amountPrefix,
                    amountSuffix = dataState.amountModel.amountSuffix,
                    amountText = dataState.amountModel.amountText,
                    currencyResId = dataState.currencyModel.selectedCurrencyResId,
                    isAltCaptionKinIcon = false,
                    uiModel = dataState.amountAnimatedModel,
                    isAnimated = true,
                    isClickable = false,
                    networkState = networkState,
                    textStyle = CodeTheme.typography.displayLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        CodeTheme.dimens.grid.x1,
                        Alignment.CenterHorizontally
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.powered_by),
                        style = CodeTheme.typography.body1,
                        color = BrandLight,
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_kado),
                        contentDescription = "Kado"
                    )
                }
            }
        }

        CodeKeyPad(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset)
                .weight(1f),
            onNumber = viewModel::onNumber,
            onClear = viewModel::onBackspace,
            onDecimal = viewModel::onDot,
            isDecimal = false, // no decimal allowed for buys
        )

        val uriHandler = LocalUriHandler.current

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset),
            onClick = {
                if (!networkObserver.isConnected) {
                    ErrorUtils.showNetworkError(context)
                    return@CodeButton
                }

                viewModel.initiatePurchase()?.let {
                    uriHandler.openUri(it)
                }
            },
            enabled = dataState.continueEnabled,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }
}