package com.dicereligion.rateprince.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/*
 * On-widget keypad (Option B). Every tap is a broadcast → state write → re-render round
 * trip through the launcher, so this is only offered at large sizes and is opt-in via a
 * toggle. The quick-convert overlay stays the default way to enter an amount.
 */

class AppendDigitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY] ?: return
        editAmount(context, glanceId) { KeypadInput.append(it, key) }
    }

    companion object {
        val KEY = ActionParameters.Key<String>("key")
    }
}

class BackspaceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        editAmount(context, glanceId) { KeypadInput.backspace(it) }
    }
}

class ClearAmountAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        editAmount(context, glanceId) { "" }
    }
}

class ToggleKeypadAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetStateKeys.KEYPAD_OPEN] = !(prefs[WidgetStateKeys.KEYPAD_OPEN] ?: false)
        }
        // The state write alone doesn't re-render; update() is the required pair.
        RatePrinceWidget().update(context, glanceId)
    }
}

/** Applies [transform] to this instance's amount; a null result means "ignore the keystroke". */
private suspend fun editAmount(context: Context, glanceId: GlanceId, transform: (String) -> String?) {
    var changed = false
    updateAppWidgetState(context, glanceId) { prefs ->
        val current = prefs[WidgetStateKeys.AMOUNT_INPUT].orEmpty()
        val next = transform(current)
        if (next != null && next != current) {
            prefs[WidgetStateKeys.AMOUNT_INPUT] = next
            changed = true
        }
    }
    if (changed) RatePrinceWidget().update(context, glanceId)
}
