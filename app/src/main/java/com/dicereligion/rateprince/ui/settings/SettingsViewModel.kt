package com.dicereligion.rateprince.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dicereligion.rateprince.container
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.data.RateConfigRepository
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val home: CurrencyMeta,
        val local: CurrencyMeta,
        /** Canonical home-per-local rate; zero when not set. */
        val rate: BigDecimal,
        val updatedAtMillis: Long,
    ) : SettingsUiState
}

class SettingsViewModel(
    private val repository: RateConfigRepository,
    private val catalog: CurrencyCatalog,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = repository.config
        .map { config ->
            SettingsUiState.Ready(
                home = catalog.meta(config.homeCurrency),
                local = catalog.meta(config.localCurrency),
                rate = config.rate,
                updatedAtMillis = config.updatedAtEpochMillis,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun saveRate(rate: BigDecimal) {
        viewModelScope.launch { repository.setRate(rate) }
    }

    fun swap() {
        viewModelScope.launch { repository.swapPair() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.container
                SettingsViewModel(container.rateConfigRepository, container.currencyCatalog)
            }
        }
    }
}
