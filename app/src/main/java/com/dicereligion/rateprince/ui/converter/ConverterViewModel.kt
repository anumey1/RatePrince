package com.dicereligion.rateprince.ui.converter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dicereligion.rateprince.container
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.data.RateConfigRepository
import com.dicereligion.rateprince.domain.AmountParser
import com.dicereligion.rateprince.domain.ConversionEngine
import com.dicereligion.rateprince.domain.LadderRow
import com.dicereligion.rateprince.domain.MoneyFormatter
import com.dicereligion.rateprince.domain.buildLadder
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.domain.model.RateConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Locale

sealed interface ConverterUiState {
    data object Loading : ConverterUiState

    /** First run, or the pair just changed: the rate is 0 and must be set before anything converts. */
    data class NeedsRate(val local: CurrencyMeta, val home: CurrencyMeta) : ConverterUiState

    data class Ready(
        val local: CurrencyMeta,
        val home: CurrencyMeta,
        /** Canonical rate at display precision, e.g. "0.58". */
        val rateLabel: String,
        val updatedAtMillis: Long,
        /** Formatted home amount for the typed local amount; null while nothing is typed. */
        val convertedLabel: String?,
        val ladder: List<LadderRow>,
        /** Row nearest the typed amount, or -1. */
        val highlightedIndex: Int,
    ) : ConverterUiState
}

class ConverterViewModel(
    private val repository: RateConfigRepository,
    private val engine: ConversionEngine,
    private val catalog: CurrencyCatalog,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {

    private val amountInput = MutableStateFlow("")

    val state: StateFlow<ConverterUiState> =
        combine(repository.config, amountInput, ::buildState)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConverterUiState.Loading)

    /** The field filters input with [AmountParser.isEditable], so [raw] is always acceptable. */
    fun onAmountChanged(raw: String) {
        amountInput.value = raw
    }

    fun swap() {
        viewModelScope.launch { repository.swapPair() }
    }

    fun saveRate(rate: BigDecimal) {
        viewModelScope.launch { repository.setRate(rate) }
    }

    private fun buildState(config: RateConfig, raw: String): ConverterUiState {
        val local = catalog.meta(config.localCurrency)
        val home = catalog.meta(config.homeCurrency)
        if (!config.isUsable) return ConverterUiState.NeedsRate(local, home)

        // A fresh formatter per emission picks up locale changes, and is never shared across threads.
        val formatter = MoneyFormatter(locale())
        val parsed = AmountParser.parse(raw)
        // The ladder doesn't depend on the amount, but rebuilding it is ~1 ms and keeps one code path.
        val ladder = buildLadder(config, local, home, engine, formatter)
        return ConverterUiState.Ready(
            local = local,
            home = home,
            rateLabel = formatter.formatRate(config.rate),
            updatedAtMillis = config.updatedAtEpochMillis,
            convertedLabel = parsed?.let { formatter.format(engine.convert(it, config, home.fractionDigits), home) },
            ladder = ladder,
            highlightedIndex = parsed?.let { amount ->
                ladder.indices.minByOrNull { (ladder[it].localAmount - amount).abs() }
            } ?: -1,
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                ConverterViewModel(
                    container.rateConfigRepository,
                    container.conversionEngine,
                    container.currencyCatalog,
                )
            }
        }
    }
}
