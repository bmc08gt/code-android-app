package com.getcode.view.main.getKin

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.CurrencyCode
import com.getcode.model.Fiat
import com.getcode.model.KinAmount
import com.getcode.model.Limit
import com.getcode.model.Rate
import com.getcode.network.client.Client
import com.getcode.network.client.declareFiatPurchase
import com.getcode.network.client.linkAdditionalAccount
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.PhoneRepository
import com.getcode.network.repository.PrefRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.AccountType
import com.getcode.util.CurrencyUtils
import com.getcode.util.locale.LocaleHelper
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils
import com.getcode.utils.blockchainMemo
import com.getcode.utils.makeE164
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.view.main.giveKin.AmountAnimatedInputUiModel
import com.getcode.view.main.giveKin.AmountUiModel
import com.getcode.view.main.giveKin.BaseAmountCurrencyViewModel
import com.getcode.view.main.giveKin.CurrencyUiModel
import com.getcode.view.main.giveKin.FlowType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BuyKinViewModel @Inject constructor(
    client: Client,
    exchange: Exchange,
    prefsRepository: PrefRepository,
    balanceRepository: BalanceRepository,
    transactionRepository: TransactionRepository,
    localeHelper: LocaleHelper,
    currencyUtils: CurrencyUtils,
    networkObserver: NetworkConnectivityListener,
    resources: ResourceHelper,
    private val phoneRepository: PhoneRepository,
) : BaseAmountCurrencyViewModel(
    client,
    prefsRepository,
    exchange,
    balanceRepository,
    transactionRepository,
    localeHelper,
    currencyUtils,
    resources,
    networkObserver
) {
    data class State(
        val currencyModel: CurrencyUiModel = CurrencyUiModel(),
        val amountAnimatedModel: AmountAnimatedInputUiModel = AmountAnimatedInputUiModel(),
        val amountModel: AmountUiModel = AmountUiModel(),
        val continueEnabled: Boolean = false,
        val relationshipEstablished: Boolean = false,
    )

    val state = MutableStateFlow(State())

    override fun canChangeCurrency(): Boolean {
        return false
    }

    init {
        init()

        viewModelScope.launch {
            establishSwapRelationship()
        }
    }

    override val flowType: FlowType = FlowType.Buy

    override fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel) {
        // force currency to be local to device
        with (localeHelper.getDefaultCurrency()) {
            state.update {
                it.copy(
                    currencyModel = currencyUiModel.copy(
                        selectedCurrencyCode = this?.code,
                        selectedCurrencyResId = this?.resId,
                    )
                )
            }
        }
    }

    override fun setAmountUiModel(amountUiModel: AmountUiModel) {
        with (localeHelper.getDefaultCurrency()) {
            state.update { s ->
                s.copy(
                    amountModel = amountUiModel.copy(
                        amountPrefix = formatPrefix(this)
                            .takeIf { it != this?.code }.orEmpty()
                    )
                )
            }
        }
    }

    override fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel) {
        state.update {
            it.copy(amountAnimatedModel = amountAnimatedInputUiModel)
        }
    }

    override fun getCurrencyUiModel(): CurrencyUiModel {
        // force currency to be local to device
        return with (localeHelper.getDefaultCurrency()) {
            state.value.currencyModel.copy(
                selectedCurrencyCode = this?.code,
                selectedCurrencyResId = this?.resId,
            )
        }
    }

    override fun getAmountUiModel(): AmountUiModel {
        // force currency to be local to device
        return with (localeHelper.getDefaultCurrency()) {
            state.value.amountModel.copy(
                amountPrefix = formatPrefix(this)
                    .takeIf { it != this?.code }.orEmpty()
            )
        }
    }

    override fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel {
        return state.value.amountAnimatedModel.copy()
    }

    override fun reset() {
        numberInputHelper.reset()
        onAmountChanged(true)
        state.update {
            it.copy(continueEnabled = false)
        }
    }

    override fun onAmountChanged(lastPressedBackspace: Boolean) {
        super.onAmountChanged(lastPressedBackspace)
        state.update {
            it.copy(continueEnabled = numberInputHelper.amount > 0.0 && BuildConfig.KADO_API_KEY.isNotEmpty())
        }
    }

    private suspend fun establishSwapRelationship() {
        val organizer = SessionManager.getOrganizer() ?: return
        if (organizer.info(AccountType.Swap) != null) {
            Timber.d("USDC deposit account established already.")
            state.update {
                it.copy(relationshipEstablished = true)
            }
            return
        }

        client.linkAdditionalAccount(
            owner = organizer.ownerKeyPair,
            linkedAccount = organizer.swapKeyPair
        ).onFailure {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_account_error),
                resources.getString(R.string.error_description_usdc_deposit_failure)
            )
        }.onSuccess {
            state.update { it.copy(relationshipEstablished = true) }
        }
    }

    private val supportedCurrencies = listOf(
        CurrencyCode.USD, CurrencyCode.EUR, CurrencyCode.CAD, CurrencyCode.GBP, CurrencyCode.MXN,
        CurrencyCode.COP, CurrencyCode.INR, CurrencyCode.CHF, CurrencyCode.AUD, CurrencyCode.ARS,
        CurrencyCode.BRL, CurrencyCode.CLP, CurrencyCode.JPY, CurrencyCode.KRW, CurrencyCode.PEN,
        CurrencyCode.PHP, CurrencyCode.SGD, CurrencyCode.TRY, CurrencyCode.UYU, CurrencyCode.TWD,
        CurrencyCode.VND, CurrencyCode.CRC, CurrencyCode.SEK, CurrencyCode.PLN, CurrencyCode.DKK,
        CurrencyCode.NOK, CurrencyCode.NZD
    )

    private fun buildKadoUrl(amount: KinAmount, rate: Rate, nonce: UUID): Uri? {
        val apiKey = BuildConfig.KADO_API_KEY
        if (apiKey.isEmpty()) {
            return null
        }



        return Uri.Builder()
            .scheme("https")
            .authority("app.kado.money")
            .appendQueryParameter("apiKey", apiKey)
            .appendQueryParameter("onPayAmount", amount.fiat.toString())
            .appendQueryParameter("onPayCurrency", rate.currency.name.uppercase())
            .appendQueryParameter("onRevCurrency", "USDC")
            .appendQueryParameter("mode", "minimal")
            .appendQueryParameter("network", "SOLANA")
            .appendQueryParameter("fiatMethodList", "debit_only")
            .appendQueryParameter("phone", phoneRepository.phoneNumber.makeE164())
            .appendQueryParameter("onToAddress", SessionManager.getOrganizer()?.swapDepositAddress)
            .appendQueryParameter("memo", nonce.blockchainMemo)
            .build()
    }

    private val checkMinimumMet: (amount: KinAmount, rate: Rate) -> Boolean = { amount, rate ->
        val threshold = transactionRepository.buyLimitFor(rate.currency) ?: Limit.Zero
        val isUnderMinimum = amount.fiat < threshold.min
        if (isUnderMinimum) {
            val formatted = FormatUtils.formatCurrency(threshold.min, rate.currency)

            TopBarManager.showMessage(
                resources.getString(R.string.error_title_purchaseTooSmall),
                resources.getString(R.string.error_description_buy_kin_too_small, formatted)
            )
        }
        !isUnderMinimum
    }

    private val checkUnderMax: (amount: KinAmount, rate: Rate) -> Boolean = { amount, rate ->
        val threshold = transactionRepository.buyLimitFor(rate.currency) ?: Limit.Zero
        val isOverLimit = amount.fiat > threshold.max
        if (isOverLimit) {
            val formatted = FormatUtils.formatCurrency(threshold.max, rate.currency)
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_purchaseTooLarge),
                resources.getString(R.string.error_description_buy_kin_too_large, formatted)
            )
        }

        !isOverLimit
    }

    suspend fun initiatePurchase(): String? {
        val currencyModel = getCurrencyUiModel()
        val amountAnimatedModel = getAmountAnimatedInputUiModel()
        val currencySymbol = currencyModel
            .currencies.firstOrNull { currencyModel.selectedCurrencyCode == it.code }
            ?.let { CurrencyCode.tryValueOf(it.code) }
            ?.takeIf { supportedCurrencies.contains(it) }
            ?: CurrencyCode.USD

        val rate = exchange.rateFor(currencySymbol) ?: exchange.rateForUsd()!!

        val kadoAmount = Fiat.fromString(
            currencySymbol,
            amountAnimatedModel.amountData.amount
        )?.let { KinAmount.fromFiatAmount(fiat = it, rate = rate) } ?: return null

        if (!checkMinimumMet(kadoAmount, rate)) {
            return null
        }

        if (!checkUnderMax(kadoAmount, rate)) {
            return null
        }

        val nonce = UUID.randomUUID()
        val kadoUrl = buildKadoUrl(kadoAmount, rate, nonce)
        val organizer = SessionManager.getOrganizer() ?: return null

        client.declareFiatPurchase(
            owner = organizer.ownerKeyPair,
            amount = kadoAmount,
            nonce = nonce
        )

        return withContext(Dispatchers.Main) {
            kadoUrl?.toString()
        }
    }
}