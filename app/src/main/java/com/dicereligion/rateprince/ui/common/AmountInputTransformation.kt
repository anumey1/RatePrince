package com.dicereligion.rateprince.ui.common

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import com.dicereligion.rateprince.domain.AmountParser

/** Rejects keystrokes that would make the amount unparseable, so the field is always valid. */
object AmountInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (!AmountParser.isEditable(asCharSequence().toString())) revertAllChanges()
    }
}
