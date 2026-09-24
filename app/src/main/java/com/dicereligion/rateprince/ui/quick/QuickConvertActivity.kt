package com.dicereligion.rateprince.ui.quick

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dicereligion.rateprince.container
import com.dicereligion.rateprince.domain.AmountParser
import com.dicereligion.rateprince.ui.theme.RatePrinceTheme
import com.dicereligion.rateprince.widget.WidgetIntents
import com.dicereligion.rateprince.widget.setWidgetAmount
import kotlinx.coroutines.launch

/**
 * The widget's real text input. Widgets can't host an EditText, so tapping the amount
 * opens this translucent sheet over the home screen with the numeric keyboard up.
 * The amount is written back to that one widget instance on Done, on dismiss, and
 * when the user leaves some other way (home button), so nothing is lost.
 */
class QuickConvertActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var amount: TextFieldState
    private lateinit var initialAmount: String
    private var committed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        initialAmount = WidgetIntents.amountFrom(intent).takeIf(AmountParser::isEditable).orEmpty()
        val restored = savedInstanceState?.getString(KEY_AMOUNT)?.takeIf(AmountParser::isEditable)
        val text = restored ?: initialAmount
        // Everything selected, so typing replaces the old amount and backspace clears it.
        amount = TextFieldState(text, initialSelection = TextRange(0, text.length))

        val container = container
        setContent {
            val config by container.rateConfigRepository.config.collectAsStateWithLifecycle(initialValue = null)
            BackHandler { commitAndFinish() }
            RatePrinceTheme {
                QuickConvertSheet(
                    amount = amount,
                    config = config,
                    catalog = container.currencyCatalog,
                    engine = container.conversionEngine,
                    onDone = ::commitAndFinish,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_AMOUNT, amount.text.toString())
    }

    override fun onStop() {
        super.onStop()
        // Home button or another app on top: keep what was typed. Rotation is not an exit.
        if (!isChangingConfigurations) commitAndFinish()
    }

    private fun commitAndFinish() {
        if (!committed) {
            committed = true
            val raw = amount.text.toString()
            if (raw != initialAmount && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appContext = applicationContext
                // Application scope: the write must survive this activity finishing.
                appContext.container.applicationScope.launch { setWidgetAmount(appContext, appWidgetId, raw) }
            }
        }
        if (!isFinishing) finish()
    }

    private companion object {
        const val KEY_AMOUNT = "amount"
    }
}
