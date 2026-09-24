package com.dicereligion.rateprince.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.ui.navigation.CurrencySlot

// Phase 0 placeholder. Phase 2 adds the currency rows and the rate field.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPickCurrency: (CurrencySlot) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TextButton(onClick = { onPickCurrency(CurrencySlot.HOME) }) {
                Text(stringResource(R.string.settings_home_currency))
            }
            TextButton(onClick = { onPickCurrency(CurrencySlot.LOCAL) }) {
                Text(stringResource(R.string.settings_local_currency))
            }
        }
    }
}
