package com.dicereligion.rateprince.ui.rate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import java.math.BigDecimal

/**
 * Holds a [RateDraft] across recomposition and rotation. Resets to the stored rate
 * whenever [rate] or the pair changes, e.g. after a save or a swap.
 */
@Composable
fun rememberRateDraft(rate: BigDecimal, local: CurrencyMeta, home: CurrencyMeta): MutableState<RateDraft> =
    rememberSaveable(rate, local.code.value, home.code.value, saver = RateDraftSaver) {
        mutableStateOf(RateDraft.from(rate))
    }

private val RateDraftSaver = listSaver<MutableState<RateDraft>, String>(
    save = { listOf(it.value.forward, it.value.inverse, it.value.editedField?.name.orEmpty()) },
    restore = { (forward, inverse, field) ->
        mutableStateOf(RateDraft(forward, inverse, RateField.entries.firstOrNull { it.name == field }))
    },
)

/**
 * "1 JPY = [0.58] INR" above a quieter "1 INR = [1.72414] JPY". Either can be edited.
 * The keyboard's Done action saves when the draft is valid.
 */
@Composable
fun RateEditor(
    local: CurrencyMeta,
    home: CurrencyMeta,
    draft: RateDraft,
    onDraftChange: (RateDraft) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RateRow(
            from = local,
            to = home,
            value = draft.forward,
            onValueChange = { raw -> draft.editForward(raw)?.let(onDraftChange) },
            onDone = { if (draft.canSave) onSave() },
            isError = draft.hasError && draft.editedField == RateField.FORWARD,
            emphasized = true,
        )
        RateRow(
            from = home,
            to = local,
            value = draft.inverse,
            onValueChange = { raw -> draft.editInverse(raw)?.let(onDraftChange) },
            onDone = { if (draft.canSave) onSave() },
            isError = draft.hasError && draft.editedField == RateField.INVERSE,
            emphasized = false,
        )
        if (draft.hasError) {
            Text(
                text = stringResource(R.string.rate_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RateRow(
    from: CurrencyMeta,
    to: CurrencyMeta,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    isError: Boolean,
    emphasized: Boolean,
) {
    val textColor =
        if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.rate_editor_description, from.code.value, to.code.value)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.rate_editor_prefix, from.code.value),
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.width(88.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = isError,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
        Text(
            text = to.code.value,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp),
        )
    }
}
