package com.getcode.view.main.giveKin

import androidx.lifecycle.viewModelScope
import com.getcode.App
import com.getcode.R
import com.getcode.manager.SessionManager
import com.getcode.model.*
import com.getcode.model.Currency
import com.getcode.network.client.Client
import com.getcode.network.client.fetchTransactionLimits
import com.getcode.network.repository.*
import com.getcode.util.CurrencyUtils
import com.getcode.util.NumberInputHelper
import com.getcode.util.locale.LocaleHelper
import com.getcode.utils.ErrorUtils
import com.getcode.utils.FormatUtils
import com.getcode.utils.LocaleUtils
import com.getcode.view.BaseViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.lang.StringBuilder
import kotlin.math.min

sealed class CurrencyListItem {
    data class TitleItem(val text: String) : CurrencyListItem()
    data class RegionCurrencyItem(val currency: Currency, val isRecent: Boolean) :
        CurrencyListItem()
}

data class CurrencyUiModel(
    val currenciesMap: Map<String, Currency> = mapOf(),
    val currenciesFiltered: List<Currency> = listOf(),
    val currenciesRecent: List<Currency> = listOf(),
    val listItems: List<CurrencyListItem> = listOf(),
    val sendLimitsMap: Map<String, SendLimit> = mapOf(),
    val currencySearchText: String = "",
    val selectedCurrencyCode: String? = null,
    val selectedCurrencyResId: Int? = null,
)

data class AmountUiModel(
    val balanceKin: Double = 0.0,
    val amountText: String = "",
    val amountDouble: Double = 0.0,
    val amountKin: Kin = Kin(0),
    val amountPrefix: String = "",
    val amountSuffix: String = "",
    val captionText: String = "",
    val isCaptionConversion: Boolean = false,
    val isInsufficient: Boolean = false,
    val sendLimit: Double = 0.0
)

abstract class BaseAmountCurrencyViewModel(
    val client: Client,
    private val prefsRepository: PrefRepository,
    val currencyRepository: CurrencyRepository,
    private val balanceRepository: BalanceRepository,
    private val localeHelper: LocaleHelper,
) : BaseViewModel(), AmountInputViewModel {
    protected val numberInputHelper = NumberInputHelper()
    private var searchJob: Job? = null

    abstract fun setCurrencyUiModel(currencyUiModel: CurrencyUiModel)
    abstract fun setAmountUiModel(amountUiModel: AmountUiModel)
    abstract fun setAmountAnimatedInputUiModel(amountAnimatedInputUiModel: AmountAnimatedInputUiModel)
    abstract fun getCurrencyUiModel(): CurrencyUiModel
    abstract fun getAmountUiModel(): AmountUiModel
    abstract fun getAmountAnimatedInputUiModel(): AmountAnimatedInputUiModel

    open fun init() {
        numberInputHelper.reset()

        combine (
            currencyRepository.getRates().flowOn(Dispatchers.IO),
            balanceRepository.balanceFlow,
        ) { rates, balance ->
            setCurrencyUiModel(getCurrenciesUiModelWithRates(getCurrencyUiModel(), rates))
            setAmountUiModel(
                getAmountUiModel().copy(balanceKin = balance)
            )
        }.launchIn(viewModelScope)

        combine(
            currencyRepository.getRates()
                .flowOn(Dispatchers.IO)
                .map { CurrencyUtils.getCurrenciesWithRates(it) },
            prefsRepository
                .observeOrDefault(
                    PrefsString.KEY_CURRENCY_SELECTED, localeHelper.getDefaultCurrencyName()
                ).flowOn(Dispatchers.IO)
                .distinctUntilChanged(),
        ) { currencies, selectedCode ->
            val currencyUiModel = getCurrencyUiModel()
            val currency = currencies.firstOrNull { it.code == selectedCode }
            getModelsWithSelectedCurrency(
                currencyUiModel,
                getAmountUiModel(),
                selectedCode,
                currency?.resId,
                numberInputHelper.amount,
                numberInputHelper.getFormattedString()
            )
        }.filterNotNull().onEach {  (currencyModel, amountModel) ->
            setCurrencyUiModel(currencyModel)
            setAmountUiModel(amountModel)
        }.launchIn(viewModelScope)

        SessionManager.getKeyPair()?.let {
            client.fetchTransactionLimits(it)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { sendLimitsMap ->
                    setCurrencyUiModel(getCurrencyUiModel().copy(sendLimitsMap = sendLimitsMap))
                }
                .subscribe({}, ErrorUtils::handleError)
        }
    }

    private fun onSelectedCurrencyChanged(
        selectedCurrencyCode: String
    ) {
        numberInputHelper.reset()
        numberInputHelper.isDecimalAllowed = selectedCurrencyCode != CurrencyCode.KIN.name

        val currencyUiModel = getCurrencyUiModel()
        val currencies = currencyUiModel.currenciesMap
        val currency = currencies[selectedCurrencyCode] ?: return
        val (currencyModel, amountModel) = getModelsWithSelectedCurrency(
            currencyUiModel,
            getAmountUiModel(),
            selectedCurrencyCode,
            currency.resId,
            numberInputHelper.amount,
            numberInputHelper.getFormattedString()
        ) ?: return

        setCurrencyUiModel(currencyModel)
        setAmountUiModel(amountModel)
        setAmountAnimatedInputUiModel(AmountAnimatedInputUiModel())

        persistSelectedCurrencyChanged(selectedCurrencyCode)
        persistRecentCurrenciesChanged(currencyModel.currenciesRecent)
    }

    protected open fun onAmountChanged(
        lastPressedBackspace: Boolean = false
    ) {
        val currencyUiModel = getCurrencyUiModel()
        val amountAnimatedInputUiModel = getAmountAnimatedInputUiModel()

        val selectedCurrency =
            currencyUiModel.currenciesMap[currencyUiModel.selectedCurrencyCode] ?: return
        val amount = numberInputHelper.amount
        val amountText = numberInputHelper.getFormattedString()

        val amountUiModel =
            getAmountUiFormattedModel(getAmountUiModel(), selectedCurrency, amount, amountText)

        setAmountUiModel(amountUiModel)
        setAmountAnimatedInputUiModel(
            amountAnimatedInputUiModel.copy(
                amountDataLast = amountAnimatedInputUiModel.amountData,
                amountData = numberInputHelper.getFormattedStringForAnimation(),
                lastPressedBackspace = lastPressedBackspace,
            )
        )
    }

    override fun onNumber(number: Int) {
        numberInputHelper.maxLength = 9
        numberInputHelper.onNumber(number)
        onAmountChanged()
    }

    override fun onDot() {
        numberInputHelper.onDot()
        onAmountChanged()
    }

    override fun onBackspace() {
        numberInputHelper.onBackspace()
        onAmountChanged(true)
    }

    protected suspend fun getCurrenciesUiModelWithRates(
        model: CurrencyUiModel?,
        rates: Map<String, Double>
    ): CurrencyUiModel {
        val currenciesLocales = CurrencyUtils.getCurrenciesWithRates(rates)

        return CurrencyUiModel(
            currenciesMap = currenciesLocales.map { it }.associateBy { it.code },
            currenciesFiltered = currenciesLocales,
            listItems =
            getCurrenciesLocalesListItems(
                currencies = currenciesLocales,
                currenciesRecent = model?.currenciesRecent ?: listOf(),
                searchString = ""
            ),
            currencySearchText = "",
            selectedCurrencyCode = model?.selectedCurrencyCode,
            selectedCurrencyResId = CurrencyUtils.getFlagByCurrency(model?.selectedCurrencyCode)
        )
    }

    protected fun getModelsWithSelectedCurrency(
        currencyModel: CurrencyUiModel,
        amountUiModel: AmountUiModel,
        selectedCurrencyCode: String,
        resId: Int? = null,
        amount: Double,
        formattedString: String
    ): Pair<CurrencyUiModel, AmountUiModel>? {
        val selectedCurrency = currencyModel.currenciesMap[selectedCurrencyCode]

        if (selectedCurrencyCode.isEmpty() || selectedCurrency == null) return null
        val currenciesRecentMapped = currencyModel.currenciesRecent
            .toMutableList()
            .let {
                if (!it.contains(selectedCurrency)) it.add(selectedCurrency)
                if (it.size >= 5) it.subList(0, 5) else it
            }
            .sortedBy { it.code }

        val currencyModelN = currencyModel.copy(
            selectedCurrencyCode = selectedCurrency.code,
            selectedCurrencyResId = resId,
            currenciesRecent = currenciesRecentMapped,
            listItems = getCurrenciesLocalesListItems(
                currencyModel.currenciesFiltered,
                currenciesRecentMapped,
                ""
            ),
            currencySearchText = ""
        )

        val amountModelN =
            getAmountUiFormattedModel(amountUiModel, selectedCurrency, amount, formattedString)

        return Pair(currencyModelN, amountModelN)
    }

    protected fun getAmountUiFormattedModel(
        amountUiModel: AmountUiModel,
        selectedCurrency: Currency,
        amount: Double,
        amountText: String
    ): AmountUiModel {
        val currentBalance = balanceRepository.balanceFlow.value
        val amountKin = FormatUtils.getKinValue(amount, selectedCurrency.rate)
            .inflating()

        val fiatValue =
            FormatUtils.getFiatValue(currentBalance, selectedCurrency.rate)

        val sendLimit = getCurrencyUiModel()
            .sendLimitsMap[selectedCurrency.code]?.limit ?: fiatValue
        val amountAvailable = min(sendLimit, fiatValue)
        val isInsufficient = amount > amountAvailable ||
                amountKin.toKinTruncatingLong() > currentBalance

        return amountUiModel.copy(
            amountText = formatAmount(amountText, selectedCurrency),
            amountDouble = amount,
            amountKin = amountKin,
            amountPrefix = formatPrefix(selectedCurrency),
            amountSuffix = formatSuffix(selectedCurrency),
            captionText = formatCaption(
                selectedCurrency,
                amount,
                amountKin.toKinTruncatingLong().toDouble(),
                amountAvailable
            ),
            isCaptionConversion = isCaptionConversion(selectedCurrency, amount),
            isInsufficient = isInsufficient,
            sendLimit = sendLimit
        )
    }

    protected fun getDefaultAndRecentCurrencies(): Single<Pair<String, List<String>>> {
        return Single.zip(
            prefsRepository.getFirstOrDefault(
                PrefsString.KEY_CURRENCY_SELECTED,
                LocaleUtils.getDefaultCurrency(App.getInstance())
            ),
            prefsRepository.getFirstOrDefault(
                PrefsString.KEY_CURRENCIES_RECENT,
                ""
            )
        ) { a, b -> Pair(a, b.split(",")) }
    }

    private fun persistSelectedCurrencyChanged(selectedCurrencyCode: String) {
        prefsRepository.set(PrefsString.KEY_CURRENCY_SELECTED, selectedCurrencyCode)
    }

    private fun persistRecentCurrenciesChanged(currenciesRecent: List<Currency>) {
        prefsRepository.set(
            PrefsString.KEY_CURRENCIES_RECENT,
            currenciesRecent.joinToString(",") { it.code }
        )
    }

    private fun getCurrenciesLocalesListItems(
        currencies: List<Currency>,
        currenciesRecent: List<Currency>,
        searchString: String
    ): MutableList<CurrencyListItem> {
        val currenciesLocalesList = mutableListOf<CurrencyListItem>()

        if (searchString.isBlank()) {
            if (currenciesRecent.isNotEmpty()) {
                currenciesLocalesList.add(
                    CurrencyListItem.TitleItem(
                        App.getInstance().getString(R.string.title_recentCurrencies)
                    )
                )
                currenciesRecent.forEach { currency ->
                    currenciesLocalesList.add(
                        CurrencyListItem.RegionCurrencyItem(
                            currency,
                            isRecent = true
                        )
                    )
                }
            }

            currenciesLocalesList.add(
                CurrencyListItem.TitleItem(
                    App.getInstance().getString(R.string.title_otherCurrencies)
                )
            )
        } else {
            currenciesLocalesList.add(
                CurrencyListItem.TitleItem(
                    App.getInstance().getString(R.string.title_results),
                )
            )
        }

        currencies
            .filter {
                        (searchString.isEmpty() ||
                                it.name.lowercase().contains(searchString.lowercase()) ||
                                it.code.lowercase().contains(searchString.lowercase())
                                )
            }
            .forEach { currency ->
                if (searchString.isNotEmpty() || !currenciesRecent.contains(currency)) {
                    currenciesLocalesList.add(
                        CurrencyListItem.RegionCurrencyItem(
                            currency,
                            isRecent = false
                        )
                    )
                }
            }

        return currenciesLocalesList
    }

    private fun formatPrefix(selectedCurrency: Currency): String {
        return if (!isKin(selectedCurrency)) selectedCurrency.symbol else ""
    }

    private fun formatSuffix(selectedCurrency: Currency): String {
        return if (!isKin(selectedCurrency)) " ${
            App.getInstance().getString(R.string.core_ofKin)
        }" else ""
    }

    private fun formatAmount(amountText: String, selectedCurrency: Currency): String {
        val symbol = selectedCurrency.symbol

        return StringBuilder().apply {
            val isKin = isKin(selectedCurrency)
            if (!isKin) append(symbol)
            append(amountText)
            if (!isKin) {
                append(" ")
                append(App.getInstance().getString(R.string.core_ofKin))
            }
        }.toString()
    }

    private fun formatCaption(
        currency: Currency,
        amountInput: Double, //currency
        amountInputKin: Double, //kin conversion
        amountAvailable: Double, //currency
    ): String {
        val isKin = isKin(currency)

        return if (isKin) {
            val kinAmountFormatted =
                FormatUtils.formatWholeRoundDown(amountAvailable)

            "${getString(R.string.subtitle_enterUpTo).replaceParam(kinAmountFormatted)} " +
                    App.getInstance().getString(R.string.core_kin)
        } else {
            return if (amountInput == 0.0) {
                val currencyValue = FormatUtils.format(amountAvailable)
                val kinValue = "${currency.symbol}$currencyValue"
                "${getString(R.string.subtitle_enterUpTo).replaceParam(kinValue)} " +
                        getString(R.string.core_ofKin)
            } else {
                String.format("%,.0f", amountInputKin)
            }
        }
    }

    protected fun isKin(selectedCurrency: Currency): Boolean = selectedCurrency.code == "KIN"

    private fun isCaptionConversion(selectedCurrency: Currency, amount: Double?): Boolean =
        !isKin(selectedCurrency) && amount != 0.0
}