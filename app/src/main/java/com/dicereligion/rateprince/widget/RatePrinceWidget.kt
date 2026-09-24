package com.dicereligion.rateprince.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.currentState
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.dicereligion.rateprince.R
import com.dicereligion.rateprince.container
import com.dicereligion.rateprince.domain.MoneyFormatter
import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.RateConfig
import com.dicereligion.rateprince.domain.model.RateSource
import com.dicereligion.rateprince.ui.theme.DarkColorScheme
import com.dicereligion.rateprince.ui.theme.LightColorScheme
import java.math.BigDecimal
import java.util.Date
import java.util.Locale

/** Per-instance state, keyed by GlanceId and deleted with the widget. */
object WidgetStateKeys {
    /** The raw typed string, e.g. "1250." — stored raw so intermediate states round-trip. */
    val AMOUNT_INPUT = stringPreferencesKey("amount_input")

    /** Whether this instance shows the on-widget keypad (large sizes only). */
    val KEYPAD_OPEN = booleanPreferencesKey("keypad_open")
}

class RatePrinceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(SIZE_NARROW, SIZE_MEDIUM, SIZE_WIDE, SIZE_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.container.rateConfigRepository
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        // Read once before composing so the first frame is never empty.
        val initial = repository.current()

        provideContent {
            val config by repository.config.collectAsState(initial = initial)
            WidgetRoot(
                context = context,
                config = config,
                rawAmount = currentState(WidgetStateKeys.AMOUNT_INPUT).orEmpty(),
                keypadOpen = currentState(WidgetStateKeys.KEYPAD_OPEN) ?: false,
                appWidgetId = appWidgetId,
            )
        }
    }

    /** Sizes the Android 15+ picker renders the generated preview at. */
    override val previewSizeMode = SizeMode.Responsive(setOf(SIZE_NARROW, SIZE_MEDIUM, SIZE_WIDE))

    /**
     * API 35+ generated preview: the widget picker shows the user's own currency pair and
     * rate. Before a rate exists it shows the sample conversion instead of the set-rate
     * prompt, because the picker is where the widget has to sell itself.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val config = context.container.rateConfigRepository.current().takeIf { it.isUsable } ?: SAMPLE_CONFIG
        provideContent {
            WidgetRoot(
                context = context,
                config = config,
                rawAmount = PREVIEW_AMOUNT,
                keypadOpen = false,
                appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        }
    }

    companion object {
        val SIZE_NARROW = DpSize(110.dp, 40.dp)    // 2x1
        val SIZE_MEDIUM = DpSize(180.dp, 110.dp)   // 3x2 — default
        val SIZE_WIDE = DpSize(320.dp, 110.dp)     // 5x2
        val SIZE_TALL = DpSize(250.dp, 250.dp)     // 4x4 — keypad-eligible

        /** Shown in pin dialogs and generated previews. */
        const val PREVIEW_AMOUNT = "1500"

        private val SAMPLE_CONFIG = RateConfig(
            homeCurrency = CurrencyCode("INR"),
            localCurrency = CurrencyCode("JPY"),
            rate = BigDecimal("0.58"),
            updatedAtEpochMillis = 0L,
            source = RateSource.MANUAL,
        )
    }
}

/** Shared by the live widget and the generated preview. */
@Composable
private fun WidgetRoot(
    context: Context,
    config: RateConfig,
    rawAmount: String,
    keypadOpen: Boolean,
    appWidgetId: Int,
) {
    val container = context.container
    // Rebuilt only when an input changes; strings and formatting follow the current locale.
    val model = remember(config, rawAmount, keypadOpen) {
        WidgetModel.build(
            config = config,
            rawAmount = rawAmount,
            keypadOpen = keypadOpen,
            local = container.currencyCatalog.meta(config.localCurrency),
            home = container.currencyCatalog.meta(config.homeCurrency),
            engine = container.conversionEngine,
            formatter = MoneyFormatter(),
            strings = widgetStrings(context),
            formatDate = { millis -> shortDate(context, millis) },
        )
    }
    val actions = remember(appWidgetId, rawAmount) {
        WidgetActions(
            openApp = actionStartActivity(WidgetIntents.openConverter(context)),
            editAmount = actionStartActivity(WidgetIntents.quickConvert(context, appWidgetId, rawAmount)),
            swap = actionRunCallback<SwapPairAction>(),
            toggleKeypad = actionRunCallback<ToggleKeypadAction>(),
            clear = actionRunCallback<ClearAmountAction>(),
            backspace = actionRunCallback<BackspaceAction>(),
            key = { key -> actionRunCallback<AppendDigitAction>(actionParametersOf(AppendDigitAction.KEY to key)) },
        )
    }
    GlanceTheme(colors = glanceColors()) {
        RatePrinceWidgetContent(model, actions)
    }
}

/**
 * That is the whole receiver. GlanceAppWidgetReceiver maps appWidgetId → GlanceId and
 * deletes per-instance state on removal; don't override its lifecycle methods.
 * Never set android:process on it — DataStore is single-process only.
 */
class RatePrinceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RatePrinceWidget()
}

/** Wide layout's swap button. The pair is global, so every widget instance flips. */
class SwapPairAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.container.rateConfigRepository.swapPair()
    }
}

/**
 * Writes a new amount into one widget instance and re-renders it. No-op if the widget
 * was removed meanwhile (e.g. while its overlay was open).
 */
suspend fun setWidgetAmount(context: Context, appWidgetId: Int, raw: String) {
    val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull() ?: return
    updateAppWidgetState(context, glanceId) { it[WidgetStateKeys.AMOUNT_INPUT] = raw }
    RatePrinceWidget().update(context, glanceId)
}

private fun glanceColors(): ColorProviders =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DynamicThemeColorProviders
    } else {
        ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
    }

private fun widgetStrings(context: Context) = WidgetStrings(
    tapToEnter = context.getString(R.string.widget_tap_to_enter),
    setRateTitle = context.getString(R.string.widget_set_rate_title),
    emptyResult = context.getString(R.string.converter_result_empty),
    pairFormat = context.getString(R.string.widget_pair),
    singleLineFormat = context.getString(R.string.widget_single_line),
    rateCaptionFormat = context.getString(R.string.rate_caption),
    editedFormat = context.getString(R.string.widget_edited),
    amountDescriptionFormat = context.getString(R.string.widget_amount_description),
    amountEmptyDescriptionFormat = context.getString(R.string.widget_amount_empty_description),
    resultDescriptionFormat = context.getString(R.string.widget_result_description),
    swapDescription = context.getString(R.string.action_swap),
    showKeypadDescription = context.getString(R.string.widget_keypad_show),
    hideKeypadDescription = context.getString(R.string.widget_keypad_hide),
    clearDescription = context.getString(R.string.widget_clear),
    backspaceDescription = context.getString(R.string.widget_backspace),
)

/**
 * "24 Sep". An absolute date, not "2 days ago": the widget never refreshes on a timer
 * (updatePeriodMillis = 0), so a relative time would go stale.
 */
private fun shortDate(context: Context, millis: Long): String? {
    if (millis <= 0L) return null
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    return DateFormat.format(DateFormat.getBestDateTimePattern(locale, "dMMM"), Date(millis)).toString()
}
