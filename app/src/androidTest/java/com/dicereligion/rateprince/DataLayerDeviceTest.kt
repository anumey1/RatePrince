package com.dicereligion.rateprince

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.domain.MoneyFormatter
import com.dicereligion.rateprince.domain.model.CurrencyCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.Locale

/**
 * Android's java.text is ICU-backed and differs from the JVM the unit tests run on,
 * so the formatting contract is re-checked on a real runtime.
 */
@RunWith(AndroidJUnit4::class)
class DataLayerDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog = CurrencyCatalog.fromAssets(context)
    private val inr = catalog.meta(CurrencyCode("INR"))
    private val jpy = catalog.meta(CurrencyCode("JPY"))
    private val lkr = catalog.meta(CurrencyCode("LKR"))

    @Test
    fun catalogLoadsFromAssets() {
        assertTrue(catalog.all.size in 140..170)
        assertEquals(0, jpy.fractionDigits)
    }

    @Test
    fun formattingMatchesTheJvmContract() {
        val inIndia = MoneyFormatter(Locale.forLanguageTag("en-IN"))
        assertEquals("₹1,23,456.00", inIndia.format(BigDecimal("123456.00"), inr))

        val us = MoneyFormatter(Locale.US)
        assertEquals("¥1,500", us.format(BigDecimal(1500), jpy))
        assertEquals("₹870.00", us.format(BigDecimal("870.00"), inr))
        assertEquals("Rs 1,000.00", us.format(BigDecimal(1000), lkr))
        assertEquals("870.00", us.formatPlain(BigDecimal("870"), inr))
        assertEquals("1.72414", us.formatRate(BigDecimal("1.724137931")))
        assertEquals("1.234.567,50", MoneyFormatter(Locale.GERMANY).formatPlain(BigDecimal("1234567.5"), inr))
    }

    @Test
    fun repositoryRoundTripsThroughRealDataStore() = runBlocking {
        val repository = context.container.rateConfigRepository
        repository.setRate(BigDecimal("0.5832"))
        assertEquals(BigDecimal("0.5832"), repository.current().rate)
    }
}
