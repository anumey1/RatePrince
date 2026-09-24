package com.dicereligion.rateprince.ui

import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.RateConfig
import com.dicereligion.rateprince.domain.model.RateSource
import com.dicereligion.rateprince.ui.navigation.CurrencySlot
import com.dicereligion.rateprince.ui.picker.PickAction
import com.dicereligion.rateprince.ui.picker.resolvePick
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ResolvePickTest {

    private val inr = CurrencyCode("INR")
    private val jpy = CurrencyCode("JPY")
    private val usd = CurrencyCode("USD")
    private val config = RateConfig(inr, jpy, BigDecimal("0.58"), 0L, RateSource.MANUAL)

    @Test
    fun `picking the current currency does nothing`() {
        assertEquals(PickAction.None, resolvePick(CurrencySlot.HOME, inr, config))
        assertEquals(PickAction.None, resolvePick(CurrencySlot.LOCAL, jpy, config))
    }

    @Test
    fun `picking the other slot's currency swaps`() {
        assertEquals(PickAction.Swap, resolvePick(CurrencySlot.HOME, jpy, config))
        assertEquals(PickAction.Swap, resolvePick(CurrencySlot.LOCAL, inr, config))
    }

    @Test
    fun `picking a new currency sets the pair for that slot only`() {
        assertEquals(PickAction.SetPair(home = usd, local = jpy), resolvePick(CurrencySlot.HOME, usd, config))
        assertEquals(PickAction.SetPair(home = inr, local = usd), resolvePick(CurrencySlot.LOCAL, usd, config))
    }
}
