package com.dicereligion.rateprince.ui.converter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.dicereligion.rateprince.R

// Phase 0 placeholder. Phase 2 adds the amount card and the ladder.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding))
    }
}
