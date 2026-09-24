package com.dicereligion.rateprince.data

import com.dicereligion.rateprince.domain.model.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CurrencyCatalogTest {

    // Unit tests run with the module directory as the working directory.
    private val catalog = CurrencyCatalog.fromJson(File("src/main/assets/currencies.json").readText())

    @Test
    fun `bundled catalog is well formed`() {
        val codes = catalog.all.map { it.code.value }
        assertTrue("expected ~150 currencies, got ${codes.size}", codes.size in 140..170)
        assertEquals("codes must be unique", codes.size, codes.toSet().size)
        assertEquals("codes must be sorted", codes.sorted(), codes)
        catalog.all.forEach {
            assertTrue("${it.code} digits", it.fractionDigits in setOf(0, 2, 3))
            assertTrue("${it.code} symbol", it.symbol.isNotBlank())
            assertTrue("${it.code} name", it.displayName.isNotBlank())
        }
    }

    @Test
    fun `fraction digits match ISO 4217 for the reference currencies`() {
        assertEquals(0, catalog.meta(CurrencyCode("JPY")).fractionDigits)
        assertEquals(2, catalog.meta(CurrencyCode("INR")).fractionDigits)
        assertEquals(3, catalog.meta(CurrencyCode("KWD")).fractionDigits)
        assertEquals(2, catalog.meta(CurrencyCode("IDR")).fractionDigits)
    }

    @Test
    fun `flags derive from the country prefix`() {
        assertEquals("🇯🇵", catalog.meta(CurrencyCode("JPY")).flagEmoji)
        assertEquals("🇮🇳", catalog.meta(CurrencyCode("INR")).flagEmoji)
        assertEquals("🇪🇺", catalog.meta(CurrencyCode("EUR")).flagEmoji)
        assertEquals("", catalog.meta(CurrencyCode("XOF")).flagEmoji)
    }

    @Test
    fun `search ranks code prefix before name match`() {
        val results = catalog.search("in").map { it.code.value }
        assertEquals("INR", results.first())
        // "in" also appears in names like "Argentine Peso".
        assertTrue(results.contains("ARS"))
        assertTrue(results.indexOf("INR") < results.indexOf("ARS"))
    }

    @Test
    fun `search is case-insensitive and matches names`() {
        assertEquals("JPY", catalog.search("jp").first().code.value)
        val rupees = catalog.search("rupee").map { it.code.value }
        assertTrue(rupees.containsAll(listOf("INR", "LKR", "NPR", "PKR")))
        assertEquals(catalog.all, catalog.search("  "))
    }

    @Test
    fun `unknown code falls back to the JDK`() {
        val bgn = catalog.meta(CurrencyCode("BGN"))   // withdrawn, not in the catalog
        assertEquals(2, bgn.fractionDigits)
        assertEquals("BGN", bgn.symbol)
    }
}
