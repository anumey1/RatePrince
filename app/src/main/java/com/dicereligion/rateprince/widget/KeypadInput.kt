package com.dicereligion.rateprince.widget

import com.dicereligion.rateprince.domain.AmountParser

/** Pure editing rules for the on-widget keypad (Option B in the design doc). */
object KeypadInput {
    const val DECIMAL = "."

    /**
     * Appends [key] to [current]. Returns null when the result wouldn't be a valid amount
     * (13th integer digit, 7th decimal, second separator), so the keystroke is ignored.
     */
    fun append(current: String, key: String): String? {
        val next = when {
            key == DECIMAL && current.isEmpty() -> "0."
            key == DECIMAL -> current + DECIMAL
            current == "0" -> key                  // no leading zeros: "0" then "5" is "5"
            else -> current + key
        }
        return next.takeIf(AmountParser::isEditable)
    }

    fun backspace(current: String): String = current.dropLast(1)
}
