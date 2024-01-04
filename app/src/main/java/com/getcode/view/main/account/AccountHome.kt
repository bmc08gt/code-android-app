package com.getcode.view.main.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AccountDebugOptionsScreen
import com.getcode.navigation.screens.AccountDetailsScreen
import com.getcode.navigation.screens.BuySellScreen
import com.getcode.navigation.screens.DepositKinScreen
import com.getcode.navigation.screens.FaqScreen
import com.getcode.navigation.screens.WithdrawalAmountScreen
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.util.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccountHome(
    viewModel: AccountSheetViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.stateFlow.collectAsState()
    val context = LocalContext.current

    val composeScope = rememberCoroutineScope()
    fun handleItemClicked(item: AccountPage) {
        composeScope.launch {
            delay(25)
            when (item) {
                AccountPage.BUY_AND_SELL_KIN -> navigator.push(BuySellScreen)
                AccountPage.DEPOSIT -> navigator.push(DepositKinScreen)
                AccountPage.WITHDRAW -> navigator.push(WithdrawalAmountScreen)
                AccountPage.FAQ -> navigator.push(FaqScreen)
                AccountPage.ACCOUNT_DETAILS -> navigator.push(AccountDetailsScreen)
                AccountPage.ACCOUNT_DEBUG_OPTIONS -> navigator.push(AccountDebugOptionsScreen)
                AccountPage.LOGOUT -> {
                    BottomBarManager.showMessage(
                        BottomBarManager.BottomBarMessage(
                            title = context.getString(R.string.prompt_title_logout),
                            subtitle = context
                                .getString(R.string.prompt_description_logout),
                            positiveText = context.getString(R.string.action_logout),
                            negativeText = context.getString(R.string.action_cancel),
                            onPositive = {
                                context.getActivity()?.let {
                                    viewModel.logout(it)
                                }
                            }
                        )
                    )
                }

                AccountPage.PHONE -> Unit
                AccountPage.DELETE_ACCOUNT -> Unit
                AccountPage.ACCESS_KEY -> Unit
            }
        }
    }

    Column {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(dataState.items, key = { it.type }, contentType = { it }) { item ->
                ListItem(item = item) {
                    handleItemClicked(item.type)
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier
                            .padding(top = 35.dp)
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        text = "v${BuildConfig.VERSION_NAME}",
                        color = BrandLight,
                        style = CodeTheme.typography.body2.copy(
                            textAlign = TextAlign.Center
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun ListItem(item: AccountMainItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 25.dp, horizontal = 25.dp)
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier
                .padding(end = 20.dp)
                .height(25.dp)
                .width(25.dp),
            painter = painterResource(id = item.icon),
            contentDescription = ""
        )
        Text(
            modifier = Modifier.align(CenterVertically),
            text = stringResource(item.name),
            style = CodeTheme.typography.subtitle1.copy(
                fontWeight = FontWeight.Bold
            ),
        )
        item.isPhoneLinked?.let { isPhoneLinked ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isPhoneLinked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        tint = Color.Green,
                        contentDescription = "Linked",
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(start = 5.dp),
                    text = if (isPhoneLinked) stringResource(id = R.string.title_linked)
                    else stringResource(id = R.string.title_notLinked),
                    color = BrandLight,
                    style = CodeTheme.typography.caption.copy(
                        fontSize = 12.sp
                    ),
                )
            }
        }
    }

    Divider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = White10,
        thickness = 0.5.dp
    )
}