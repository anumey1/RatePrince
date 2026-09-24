package com.dicereligion.rateprince.ui.picker

import androidx.compose.foundation.layout.Box
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

// Phase 0 placeholder. Phase 2 adds search, recents and the catalog list.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerScreen(
    slot: CurrencySlot,
    onBack: () -> Unit,
) {
    val title = when (slot) {
        CurrencySlot.HOME -> R.string.settings_home_currency
        CurrencySlot.LOCAL -> R.string.settings_local_currency
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}
