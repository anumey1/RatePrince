package com.dicereligion.rateprince.ui.quick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.domain.AmountParser
import com.dicereligion.rateprince.domain.ConversionEngine
import com.dicereligion.rateprince.domain.MoneyFormatter
import com.dicereligion.rateprince.domain.model.RateConfig
import com.dicereligion.rateprince.ui.common.AmountInputTransformation

/** Bottom sheet over a dimmed home screen. Tapping the scrim is the same as Done. */
@Composable
fun QuickConvertSheet(
    amount: TextFieldState,
    config: RateConfig?,
    catalog: CurrencyCatalog,
    engine: ConversionEngine,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            if (config != null) {
                SheetContent(amount, config, catalog, engine, onDone)
            }
        }
    }
}

@Composable
private fun SheetContent(
    amount: TextFieldState,
    config: RateConfig,
    catalog: CurrencyCatalog,
    engine: ConversionEngine,
    onDone: () -> Unit,
) {
    // Requested here, not in the parent: the field only exists once the config has loaded,
    // and focusing before it is attached silently does nothing (no keyboard).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val local = catalog.meta(config.localCurrency)
    val home = catalog.meta(config.homeCurrency)
    val formatter = remember(config) { MoneyFormatter() }
    val raw = amount.text.toString()
    // Same engine call as the app and the widget, so all three always agree.
    val converted = remember(raw, config) {
        AmountParser.parse(raw)?.takeIf { config.isUsable }?.let {
            formatter.format(engine.convert(it, config, home.fractionDigits), home)
        }
    }

    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(local.code.value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(local.symbol, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            val description = stringResource(R.string.converter_amount_description, local.displayName)
            BasicTextField(
                state = amount,
                inputTransformation = AmountInputTransformation,
                textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                onKeyboardAction = { onDone() },
                decorator = { field ->
                    Box {
                        if (amount.text.isEmpty()) {
                            Text(
                                stringResource(R.string.converter_amount_placeholder),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        field()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = description },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(home.code.value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = converted ?: stringResource(R.string.converter_result_empty),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (config.isUsable) {
            Text(
                text = stringResource(R.string.rate_caption, local.code.value, formatter.formatRate(config.rate), home.code.value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { amount.clearText() }) { Text(stringResource(R.string.quick_clear)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDone) { Text(stringResource(R.string.quick_done)) }
        }
    }
}
