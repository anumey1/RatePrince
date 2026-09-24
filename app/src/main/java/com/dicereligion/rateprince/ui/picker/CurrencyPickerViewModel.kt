package com.dicereligion.rateprince.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.dicereligion.rateprince.container
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.data.RateConfigRepository
import com.dicereligion.rateprince.data.RecentCurrenciesRepository
import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.domain.model.RateConfig
import com.dicereligion.rateprince.ui.navigation.CurrencyPickerRoute
import com.dicereligion.rateprince.ui.navigation.CurrencySlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickerUiState(
    val slot: CurrencySlot,
    val query: String = "",
    /** Current currency in this slot; null until the config loads. */
    val selected: CurrencyCode? = null,
    /** Shown only while the query is blank. */
    val recent: List<CurrencyMeta> = emptyList(),
    val results: List<CurrencyMeta> = emptyList(),
)

/** What picking a currency does to the stored pair. */
sealed interface PickAction {
    data object None : PickAction
    data object Swap : PickAction
    data class SetPair(val home: CurrencyCode, val local: CurrencyCode) : PickAction
}

/**
 * Picking the currency already in this slot changes nothing. Picking the one in the
 * *other* slot swaps the pair (and inverts the rate) instead of producing an invalid
 * INR/INR pair. Anything else sets the new pair, which clears the rate.
 */
fun resolvePick(slot: CurrencySlot, picked: CurrencyCode, config: RateConfig): PickAction {
    val (current, other) = when (slot) {
        CurrencySlot.HOME -> config.homeCurrency to config.localCurrency
        CurrencySlot.LOCAL -> config.localCurrency to config.homeCurrency
    }
    return when (picked) {
        current -> PickAction.None
        other -> PickAction.Swap
        else -> when (slot) {
            CurrencySlot.HOME -> PickAction.SetPair(home = picked, local = config.localCurrency)
            CurrencySlot.LOCAL -> PickAction.SetPair(home = config.homeCurrency, local = picked)
        }
    }
}

class CurrencyPickerViewModel(
    private val slot: CurrencySlot,
    private val repository: RateConfigRepository,
    private val recents: RecentCurrenciesRepository,
    private val catalog: CurrencyCatalog,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private var picking = false

    val state: StateFlow<PickerUiState> =
        combine(query, repository.config, recents.recent) { q, config, recent ->
            PickerUiState(
                slot = slot,
                query = q,
                selected = if (slot == CurrencySlot.HOME) config.homeCurrency else config.localCurrency,
                recent = if (q.isBlank()) recent.map(catalog::meta) else emptyList(),
                results = catalog.search(q),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState(slot))

    fun onQueryChanged(value: String) {
        query.value = value
    }

    /** Applies the pick, then calls [onDone] (navigate back) once the write has landed. */
    fun pick(code: CurrencyCode, onDone: () -> Unit) {
        if (picking) return          // ignore double taps while the write is in flight
        picking = true
        viewModelScope.launch {
            recents.add(code)
            when (val action = resolvePick(slot, code, repository.current())) {
                PickAction.None -> Unit
                PickAction.Swap -> repository.swapPair()
                is PickAction.SetPair -> repository.setPair(action.home, action.local)
            }
            onDone()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                CurrencyPickerViewModel(
                    slot = createSavedStateHandle().toRoute<CurrencyPickerRoute>().slot,
                    repository = container.rateConfigRepository,
                    recents = container.recentCurrencies,
                    catalog = container.currencyCatalog,
                )
            }
        }
    }
}
