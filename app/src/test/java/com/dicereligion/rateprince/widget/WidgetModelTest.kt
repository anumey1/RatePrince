package com.dicereligion.rateprince.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {

    private fun ready(raw: String, rate: String = "0.58") = jpyToInrModel(raw, rate) as WidgetModel.Ready

    @Test
    fun `zero rate renders the unconfigured prompt`() {
        val model = jpyToInrModel("1500", rate = "0")
        assertTrue(model is WidgetModel.Unconfigured)
        model as WidgetModel.Unconfigured
        assertEquals("Tap to set your rate", model.title)
        assertEquals("JPY → INR", model.pair)
    }

    @Test
    fun `amount converts with the shared engine`() {
        val model = ready("1500")
        assertEquals("1,500", model.amountText)
        assertEquals("870.00", model.resultText)
        assertEquals("¥1,500 → ₹870.00", model.singleLineText)
        assertEquals("1 JPY = 0.58 INR", model.rateCaption)
        assertEquals("Edited 24 Sep", model.editedText)
    }

    @Test
    fun `empty amount shows placeholder, dash, and the rate on the single line`() {
        val model = ready("")
        assertNull(model.amountText)
        assertEquals("Tap to enter", model.amountPlaceholder)
        assertEquals("—", model.resultText)
        assertEquals("1 JPY = 0.58 INR", model.singleLineText)
        assertEquals("Amount in JPY, tap to enter", model.amountDescription)
    }

    @Test
    fun `trailing separator from the overlay still converts and stays visible`() {
        val model = ready("12.")
        assertEquals("6.96", model.resultText)
        assertEquals("12.", model.amountText)
    }

    @Test
    fun `keypad state comes through`() {
        val model = jpyToInrModel("1", keypadOpen = true) as WidgetModel.Ready
        assertTrue(model.keypadOpen)
    }

    @Test
    fun `tiny rates keep their precision in the caption`() {
        // Bug #1 from the design review: the doc's caption would have shown "0.01".
        assertEquals("1 JPY = 0.0058 INR", ready("", rate = "0.0058").rateCaption)
    }

    @Test
    fun `accessibility descriptions read naturally`() {
        val model = ready("1500")
        assertEquals("Amount, 1,500 JPY, tap to edit", model.amountDescription)
        assertEquals("870.00 INR", model.resultDescription)
    }

    @Test
    fun `result text size shrinks with length`() {
        assertTrue(valueTextSize(8).value > valueTextSize(12).value)
        assertTrue(valueTextSize(12).value > valueTextSize(16).value)
        assertTrue(valueTextSize(8, large = true).value > valueTextSize(8).value)
    }
}
