package com.dicereligion.rateprince.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.ui.navigation.CurrencySlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerScreen(
    onBack: () -> Unit,
    viewModel: CurrencyPickerViewModel = viewModel(factory = CurrencyPickerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val title = when (state.slot) {
        CurrencySlot.HOME -> R.string.settings_home_currency
        CurrencySlot.LOCAL -> R.string.settings_local_currency
    }
    val onPick = { currency: CurrencyMeta ->
        keyboard?.hide()
        viewModel.pick(currency.code, onDone = onBack)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text(stringResource(R.string.picker_search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.picker_clear_search))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            LazyColumn(Modifier.fillMaxSize()) {
                if (state.recent.isNotEmpty()) {
                    item(key = "header-recent") { SectionHeader(stringResource(R.string.picker_recent)) }
                    items(state.recent, key = { "recent-${it.code.value}" }) {
                        CurrencyRow(it, selected = it.code == state.selected, onClick = { onPick(it) })
                    }
                    item(key = "header-all") { SectionHeader(stringResource(R.string.picker_all)) }
                }
                items(state.results, key = { it.code.value }) {
                    CurrencyRow(it, selected = it.code == state.selected, onClick = { onPick(it) })
                }
                if (state.results.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.picker_no_results, state.query.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CurrencyRow(currency: CurrencyMeta, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Text(currency.flagEmoji, style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(currency.code.value) },
        supportingContent = { Text(currency.displayName) },
        trailingContent = {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.picker_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
