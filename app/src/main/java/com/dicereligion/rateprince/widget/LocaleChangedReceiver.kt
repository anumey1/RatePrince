package com.dicereligion.rateprince.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.dicereligion.rateprince.container
import kotlinx.coroutines.launch

/** Grouping, separators and strings are locale-dependent, so re-render every widget. */
class LocaleChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED) return
        val pending = goAsync()
        context.container.applicationScope.launch {
            try {
                RatePrinceWidget().updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
