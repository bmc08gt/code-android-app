package com.getcode.view.main.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.model.Currency
import com.getcode.model.CurrencyCode
import com.getcode.model.KinAmount
import com.getcode.util.CurrencyUtils
import com.getcode.util.FormatAmountUtils

object PriceWithFlagDefaults {
    @Composable
    fun Text(label: String) {
        Text(
            text = label,
            color = Color.Black,
            style = MaterialTheme.typography.body1
        )
    }
}
@Composable
internal fun PriceWithFlag(
    modifier: Modifier = Modifier,
    currency: CurrencyCode,
    amount: KinAmount,
    iconSize: Dp = 20.dp,
    text: @Composable (String) -> Unit = { PriceWithFlagDefaults.Text(label = it) },
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val currencyCode = currency.name
        val flagResId = CurrencyUtils.getFlagByCurrency(currencyCode)
        if (flagResId != null) {
            Icon(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(iconSize),
                painter = painterResource(id = flagResId),
                tint = Color.Unspecified,
                contentDescription = currencyCode.let { "$it flag" }
            )
            text(FormatAmountUtils.formatAmountString(amount),)
        }
    }
}