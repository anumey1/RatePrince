package com.dicereligion.rateprince.widget

import com.dicereligion.rateprince.domain.AmountParser
import com.dicereligion.rateprince.domain.ConversionEngine
import com.dicereligion.rateprince.domain.MoneyFormatter
import com.dicereligion.rateprince.domain.model.CurrencyMeta
import com.dicereligion.rateprince.domain.model.RateConfig

/** Localised strings the widget needs, resolved from resources by the caller. */
data class WidgetStrings(
    val tapToEnter: String,
    val setRateTitle: String,
    val emptyResult: String,
    /** "%1$s → %2$s" */
    val pairFormat: String,
    /** "%1$s → %2$s" */
    val singleLineFormat: String,
    /** "1 %1$s = %2$s %3$s" */
    val rateCaptionFormat: String,
    /** "Edited %1$s" */
    val editedFormat: String,
    /** "Amount, %1$s %2$s, tap to edit" */
    val amountDescriptionFormat: String,
    /** "Amount in %1$s, tap to enter" */
    val amountEmptyDescriptionFormat: String,
    /** "%1$s %2$s" */
    val resultDescriptionFormat: String,
    val swapDescription: String,
    val showKeypadDescription: String,
    val hideKeypadDescription: String,
    val clearDescription: String,
    val backspaceDescription: String,
)

/**
 * Everything the widget renders, fully formatted. Built outside the Glance composition
 * so layouts stay cheap and are testable without a device.
 */
sealed interface WidgetModel {

    data class Unconfigured(val title: String, val pair: String) : WidgetModel

    data class Ready(
        val localCode: String,
        val homeCode: String,
        val localSymbol: String,
        val homeSymbol: String,
        /** The typed amount, grouped, as typed ("1,500", "12."); null when nothing is entered. */
        val amountText: String?,
        val amountPlaceholder: String,
        /** Symbol-free result ("870.00"), or the empty marker. */
        val resultText: String,
        /** 2x1 layout: "¥1,500 → ₹870.00", or the rate caption when no amount is set. */
        val singleLineText: String,
        /** "1 JPY = 0.58 INR" */
        val rateCaption: String,
        /** "Edited 24 Sep", or null if the rate has no timestamp. */
        val editedText: String?,
        val amountDescription: String,
        val resultDescription: String,
        val swapDescription: String,
        /** This instance's keypad toggle; only honoured at keypad-eligible sizes. */
        val keypadOpen: Boolean,
        val showKeypadDescription: String,
        val hideKeypadDescription: String,
        val clearDescription: String,
        val backspaceDescription: String,
    ) : WidgetModel

    companion object {
        fun build(
            config: RateConfig,
            rawAmount: String,
            keypadOpen: Boolean,
            local: CurrencyMeta,
            home: CurrencyMeta,
            engine: ConversionEngine,
            formatter: MoneyFormatter,
            strings: WidgetStrings,
            formatDate: (Long) -> String?,
        ): WidgetModel {
            if (!config.isUsable) {
                return Unconfigured(
                    title = strings.setRateTitle,
                    pair = strings.pairFormat.format(local.code.value, home.code.value),
                )
            }
            val amount = AmountParser.parse(rawAmount)
            val converted = amount?.let { engine.convert(it, config, home.fractionDigits) }
            val rateCaption = strings.rateCaptionFormat.format(
                local.code.value, formatter.formatRate(config.rate), home.code.value,
            )
            val resultText = converted?.let { formatter.formatPlain(it, home) } ?: strings.emptyResult
            return Ready(
                localCode = local.code.value,
                homeCode = home.code.value,
                localSymbol = local.symbol,
                homeSymbol = home.symbol,
                amountText = formatter.formatTyped(rawAmount),
                amountPlaceholder = strings.tapToEnter,
                resultText = resultText,
                singleLineText = if (amount != null && converted != null) {
                    strings.singleLineFormat.format(formatter.format(amount, local), formatter.format(converted, home))
                } else {
                    rateCaption
                },
                rateCaption = rateCaption,
                editedText = formatDate(config.updatedAtEpochMillis)?.let { strings.editedFormat.format(it) },
                amountDescription = if (amount != null) {
                    strings.amountDescriptionFormat.format(formatter.formatAmount(amount), local.displayName)
                } else {
                    strings.amountEmptyDescriptionFormat.format(local.displayName)
                },
                resultDescription = strings.resultDescriptionFormat.format(resultText, home.displayName),
                swapDescription = strings.swapDescription,
                keypadOpen = keypadOpen,
                showKeypadDescription = strings.showKeypadDescription,
                hideKeypadDescription = strings.hideKeypadDescription,
                clearDescription = strings.clearDescription,
                backspaceDescription = strings.backspaceDescription,
            )
        }
    }
}
