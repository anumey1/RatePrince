package com.dicereligion.rateprince.widget

import com.dicereligion.rateprince.domain.ConversionEngine
import com.dicereligion.rateprince.domain.MoneyFormatter
import com.dicereligion.rateprince.domain.TestCurrencies
import java.util.Locale

/** Mirrors values/strings.xml so tests read like the real widget. */
val TEST_STRINGS = WidgetStrings(
    tapToEnter = "Tap to enter",
    setRateTitle = "Tap to set your rate",
    emptyResult = "—",
    pairFormat = "%1\$s → %2\$s",
    singleLineFormat = "%1\$s → %2\$s",
    rateCaptionFormat = "1 %1\$s = %2\$s %3\$s",
    editedFormat = "Edited %1\$s",
    amountDescriptionFormat = "Amount, %1\$s %2\$s, tap to edit",
    amountEmptyDescriptionFormat = "Amount in %1\$s, tap to enter",
    resultDescriptionFormat = "%1\$s %2\$s",
    swapDescription = "Swap currencies",
    showKeypadDescription = "Show keypad",
    hideKeypadDescription = "Hide keypad",
    clearDescription = "Clear",
    backspaceDescription = "Delete",
)

fun jpyToInrModel(
    rawAmount: String,
    rate: String = "0.58",
    editedOn: String? = "24 Sep",
    keypadOpen: Boolean = false,
): WidgetModel =
    WidgetModel.build(
        config = TestCurrencies.config(TestCurrencies.JPY, TestCurrencies.INR, rate),
        rawAmount = rawAmount,
        keypadOpen = keypadOpen,
        local = TestCurrencies.JPY,
        home = TestCurrencies.INR,
        engine = ConversionEngine(),
        formatter = MoneyFormatter(Locale.US),
        strings = TEST_STRINGS,
        formatDate = { editedOn },
    )
