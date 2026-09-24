package com.dicereligion.rateprince.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.ui.common.relativeTime
import com.dicereligion.rateprince.ui.navigation.CurrencySlot
import com.dicereligion.rateprince.ui.rate.RateEditor
import com.dicereligion.rateprince.ui.rate.rememberRateDraft
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPickCurrency: (CurrencySlot) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            when (val s = state) {
                SettingsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is SettingsUiState.Ready -> SettingsContent(
                    state = s,
                    onPickCurrency = onPickCurrency,
                    onSwap = viewModel::swap,
                    onSaveRate = viewModel::saveRate,
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Ready,
    onPickCurrency: (CurrencySlot) -> Unit,
    onSwap: () -> Unit,
    onSaveRate: (BigDecimal) -> Unit,
) {
    var draft by rememberRateDraft(state.rate, state.local, state.home)
    val save = { draft.rate?.let(onSaveRate); Unit }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(R.string.settings_section_currencies))
        CurrencyRow(
            label = stringResource(R.string.settings_home_currency),
            hint = stringResource(R.string.settings_home_hint),
            currency = state.home,
            onClick = { onPickCurrency(CurrencySlot.HOME) },
        )
        CurrencyRow(
            label = stringResource(R.string.settings_local_currency),
            hint = stringResource(R.string.settings_local_hint),
            currency = state.local,
            onClick = { onPickCurrency(CurrencySlot.LOCAL) },
        )
        OutlinedButton(
            onClick = onSwap,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_swap))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionHeader(stringResource(R.string.settings_section_rate))
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RateEditor(
                local = state.local,
                home = state.home,
                draft = draft,
                onDraftChange = { draft = it },
                onSave = save,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val age = relativeTime(state.updatedAtMillis)
                Text(
                    text = if (age != null && state.rate.signum() > 0) {
                        stringResource(R.string.settings_rate_age, age)
                    } else {
                        stringResource(R.string.settings_rate_unset)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = save, enabled = draft.isDirty && draft.canSave) {
                    Text(stringResource(R.string.action_save_rate))
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
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun CurrencyRow(label: String, hint: String, currency: CurrencyMeta, onClick: () -> Unit) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text("${currency.flagEmoji} ${currency.code.value} · ${currency.displayName}".trim()) },
        supportingContent = { Text(hint) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
