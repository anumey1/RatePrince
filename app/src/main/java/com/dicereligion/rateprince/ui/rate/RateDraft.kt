package com.dicereligion.rateprince.ui.rate

import com.dicereligion.rateprince.domain.RateParser
import com.dicereligion.rateprince.domain.invertRate
import com.dicereligion.rateprince.domain.rateForEditing
import java.math.BigDecimal

enum class RateField { FORWARD, INVERSE }

/**
 * Unsaved state of the two-way rate editor. The user can type either direction —
 * "1 JPY = [0.58] INR" (forward) or "1 INR = [1.72] JPY" (inverse) — and the other
 * field follows. [rate] is always in the canonical home-per-local direction, computed
 * from whichever field was typed in, so the inverse field never loses precision to the
 * six-digit display of the forward one.
 */
data class RateDraft(
    val forward: String,
    val inverse: String,
    /** Which field the user typed in; null means the draft still matches the store. */
    val editedField: RateField? = null,
) {
    val isDirty: Boolean get() = editedField != null

    /** The rate to save (home per one local), or null if the typed text isn't a valid rate. */
    val rate: BigDecimal?
        get() = when (editedField) {
            null -> null
            RateField.FORWARD -> RateParser.parse(forward)
            RateField.INVERSE -> RateParser.parse(inverse)?.let(::invertRate)
        }

    val canSave: Boolean get() = rate != null

    /** Show an error only once the user has typed something that isn't a usable rate. */
    val hasError: Boolean
        get() {
            val typed = when (editedField) {
                null -> return false
                RateField.FORWARD -> forward
                RateField.INVERSE -> inverse
            }
            return typed.isNotBlank() && rate == null
        }

    /** Returns null when [raw] is not an acceptable intermediate state, so the edit is ignored. */
    fun editForward(raw: String): RateDraft? {
        if (!RateParser.isEditable(raw)) return null
        val parsed = RateParser.parse(raw)
        return RateDraft(raw, parsed?.let { rateForEditing(invertRate(it)) }.orEmpty(), RateField.FORWARD)
    }

    fun editInverse(raw: String): RateDraft? {
        if (!RateParser.isEditable(raw)) return null
        val parsed = RateParser.parse(raw)
        return RateDraft(parsed?.let { rateForEditing(invertRate(it)) }.orEmpty(), raw, RateField.INVERSE)
    }

    companion object {
        /** A clean draft showing the stored rate; empty when the rate isn't set yet. */
        fun from(rate: BigDecimal): RateDraft =
            if (rate > BigDecimal.ZERO) {
                RateDraft(rateForEditing(rate), rateForEditing(invertRate(rate)))
            } else {
                RateDraft("", "")
            }
    }
}
