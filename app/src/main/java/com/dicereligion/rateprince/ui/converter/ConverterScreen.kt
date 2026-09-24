package com.dicereligion.rateprince.ui.converter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.domain.AmountParser
import com.dicereligion.rateprince.domain.LadderRow
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.ui.common.relativeTime
import com.dicereligion.rateprince.ui.rate.RateEditor
import com.dicereligion.rateprince.ui.rate.rememberRateDraft
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    onOpenSettings: () -> Unit,
    viewModel: ConverterViewModel = viewModel(factory = ConverterViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings))
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
                ConverterUiState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is ConverterUiState.NeedsRate -> NeedsRateContent(
                    state = s,
                    onSaveRate = viewModel::saveRate,
                    onChangeCurrencies = onOpenSettings,
                )

                is ConverterUiState.Ready -> ReadyContent(
                    state = s,
                    onAmountChanged = viewModel::onAmountChanged,
                    onSwap = viewModel::swap,
                )
            }
        }
    }
}

@Composable
private fun NeedsRateContent(
    state: ConverterUiState.NeedsRate,
    onSaveRate: (BigDecimal) -> Unit,
    onChangeCurrencies: () -> Unit,
) {
    var draft by rememberRateDraft(BigDecimal.ZERO, state.local, state.home)
    val save = { draft.rate?.let(onSaveRate); Unit }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.needs_rate_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.needs_rate_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RateEditor(
                local = state.local,
                home = state.home,
                draft = draft,
                onDraftChange = { draft = it },
                onSave = save,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChangeCurrencies) {
                    Text(stringResource(R.string.action_change_currencies))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = save, enabled = draft.canSave) {
                    Text(stringResource(R.string.action_save_rate))
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: ConverterUiState.Ready,
    onAmountChanged: (String) -> Unit,
    onSwap: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        AmountCard(state, onAmountChanged, onSwap)
        RateCaption(state)
        HorizontalDivider()
        Ladder(state.ladder, state.highlightedIndex)
    }
}

/** Rejects keystrokes that would make the amount unparseable, so the field is always valid. */
private object AmountInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (!AmountParser.isEditable(asCharSequence().toString())) revertAllChanges()
    }
}

@Composable
private fun AmountCard(
    state: ConverterUiState.Ready,
    onAmountChanged: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val amount = rememberTextFieldState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(amount) {
        snapshotFlow { amount.text.toString() }.collect(onAmountChanged)
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            CurrencyLabel(state.local)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.local.symbol,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                val amountDescription = stringResource(R.string.converter_amount_description, state.local.displayName)
                BasicTextField(
                    state = amount,
                    inputTransformation = AmountInputTransformation,
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimaryContainer),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    onKeyboardAction = { keyboard?.hide() },
                    decorator = { field ->
                        Box {
                            if (amount.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.converter_amount_placeholder),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
                                )
                            }
                            field()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = amountDescription },
                )
                IconButton(onClick = onSwap) {
                    Icon(
                        painterResource(R.drawable.ic_swap_vert),
                        stringResource(R.string.action_swap),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )

            CurrencyLabel(state.home)
            val resultDescription = stringResource(R.string.converter_result_description, state.home.displayName)
            SelectionContainer {
                Text(
                    text = state.convertedLabel ?: stringResource(R.string.converter_result_empty),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { contentDescription = resultDescription },
                )
            }
        }
    }
}

@Composable
private fun CurrencyLabel(currency: CurrencyMeta) {
    Text(
        text = currency.code.value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
    )
}

@Composable
private fun RateCaption(state: ConverterUiState.Ready) {
    val rate = stringResource(R.string.rate_caption, state.local.code.value, state.rateLabel, state.home.code.value)
    val age = relativeTime(state.updatedAtMillis)
    Text(
        text = if (age != null) stringResource(R.string.rate_caption_edited, rate, age) else rate,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}

@Composable
private fun Ladder(rows: List<LadderRow>, highlightedIndex: Int) {
    val listState = rememberLazyListState()
    // Keep the row nearest the typed amount in view, with one row of context above it.
    LaunchedEffect(highlightedIndex) {
        if (highlightedIndex >= 0) listState.animateScrollToItem((highlightedIndex - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(rows, key = { _, row -> row.localAmount.toPlainString() }) { index, row ->
            LadderRowItem(row, highlighted = index == highlightedIndex)
        }
    }
}

@Composable
private fun LadderRowItem(row: LadderRow, highlighted: Boolean) {
    val description = stringResource(R.string.ladder_row_description, row.localLabel, row.homeLabel)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (highlighted) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                else Modifier
            )
            .padding(horizontal = 16.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = row.localLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = row.homeLabel,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
