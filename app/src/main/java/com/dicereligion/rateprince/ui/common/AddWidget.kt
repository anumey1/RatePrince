package com.dicereligion.rateprince.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.container
import com.dicereligion.rateprince.widget.WidgetPlacement
import kotlinx.coroutines.launch

/**
 * Current widget status, re-checked every time the screen resumes: after the pin dialog
 * closes, or after the user adds or removes a widget from the launcher. Null until known.
 */
@Composable
fun rememberWidgetStatus(): WidgetPlacement.Status? {
    val placement = LocalContext.current.container.widgetPlacement
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<WidgetPlacement.Status?>(null) }
    LifecycleResumeEffect(placement) {
        val job = scope.launch { status = placement.status() }
        onPauseOrDispose { job.cancel() }
    }
    return status
}

/** Launches the launcher's "Add to home screen?" dialog. */
@Composable
fun rememberRequestPin(): () -> Unit {
    val placement = LocalContext.current.container.widgetPlacement
    val scope = rememberCoroutineScope()
    return remember(placement) { { scope.launch { placement.requestPin() } } }
}

/**
 * "Put RatePrince on your home screen". One-tap add where the launcher supports it,
 * otherwise the manual steps — never a button that silently does nothing.
 */
@Composable
fun AddWidgetCard(status: WidgetPlacement.Status, modifier: Modifier = Modifier) {
    val requestPin = rememberRequestPin()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.add_widget_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            AddWidgetAction(status, requestPin, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/** The button, or the manual instructions when the launcher can't pin. */
@Composable
fun AddWidgetAction(
    status: WidgetPlacement.Status,
    requestPin: () -> Unit = rememberRequestPin(),
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (status.canPin) {
        Text(stringResource(R.string.add_widget_body), style = MaterialTheme.typography.bodyMedium, color = color)
        Button(onClick = requestPin) { Text(stringResource(R.string.action_add_widget)) }
    } else {
        Text(stringResource(R.string.add_widget_manual), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
