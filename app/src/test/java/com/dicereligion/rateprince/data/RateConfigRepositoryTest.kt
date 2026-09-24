package com.dicereligion.rateprince.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.domain.model.RateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.math.MathContext

class RateConfigRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var changes = 0
    private val clock = 1_790_000_000_000L

    private val INR = CurrencyCode("INR")
    private val JPY = CurrencyCode("JPY")
    private val USD = CurrencyCode("USD")

    private fun repository(file: File = tmp.newFile("rate_config.json").also { it.delete() }) =
        RateConfigRepository(
            store = DataStoreFactory.create(
                serializer = StoredRateConfigSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { StoredRateConfigSerializer.defaultValue },
                scope = scope,
                produceFile = { file },
            ),
            onConfigChanged = { changes++ },
            now = { clock },
        )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `fresh install is INR home, JPY local, unconfigured`() = runBlocking {
        val config = repository().current()
        assertEquals(INR, config.homeCurrency)
        assertEquals(JPY, config.localCurrency)
        assertEquals(BigDecimal.ZERO, config.rate)
        assertFalse(config.isUsable)
    }

    @Test
    fun `setRate persists, stamps time and invalidates widgets`() = runBlocking {
        val repo = repository()
        repo.setRate(BigDecimal("0.5800"))
        val config = repo.current()
        assertEquals(BigDecimal("0.58"), config.rate)
        assertEquals(clock, config.updatedAtEpochMillis)
        assertEquals(RateSource.MANUAL, config.source)
        assertTrue(config.isUsable)
        assertEquals(1, changes)
    }

    @Test
    fun `setRate rejects zero, negative and oversize rates without writing`() = runBlocking {
        val repo = repository()
        listOf("0", "-0.58", "1000000000000").forEach { bad ->
            assertThrows(bad, IllegalArgumentException::class.java) {
                runBlocking { repo.setRate(BigDecimal(bad)) }
            }
        }
        assertEquals(BigDecimal.ZERO, repo.current().rate)
        assertEquals(0, changes)
    }

    @Test
    fun `large whole rates are stored without exponent notation`() = runBlocking {
        val repo = repository()
        repo.setRate(BigDecimal("100"))
        assertEquals(BigDecimal("100"), repo.current().rate)
    }

    @Test
    fun `swapPair swaps currencies and inverts the rate`() = runBlocking {
        val repo = repository()
        repo.setRate(BigDecimal("0.58"))
        repo.swapPair()
        val swapped = repo.current()
        assertEquals(JPY, swapped.homeCurrency)
        assertEquals(INR, swapped.localCurrency)
        assertEquals(BigDecimal("1.724137931"), swapped.rate)
        assertEquals(2, changes)
    }

    @Test
    fun `inverse of inverse returns original within tolerance`() = runBlocking {
        val repo = repository()
        listOf("0.58", "0.5832", "0.005812", "0.000058", "92.5", "485.123456", "0.00001892").forEach { r ->
            val original = BigDecimal(r)
            repo.setRate(original)
            repo.swapPair()
            repo.swapPair()
            val back = repo.current().rate
            val relativeError = (back - original).abs().divide(original, MathContext.DECIMAL64)
            assertTrue("$r came back as $back", relativeError < BigDecimal("1e-9"))
        }
    }

    @Test
    fun `swapping an unconfigured pair keeps it unconfigured`() = runBlocking {
        val repo = repository()
        repo.swapPair()
        assertEquals(BigDecimal.ZERO, repo.current().rate)
        assertEquals(JPY, repo.current().homeCurrency)
    }

    @Test
    fun `setPair to a new pair clears the rate`() = runBlocking {
        val repo = repository()
        repo.setRate(BigDecimal("0.58"))
        repo.setPair(home = INR, local = USD)
        val config = repo.current()
        assertEquals(USD, config.localCurrency)
        assertFalse(config.isUsable)
    }

    @Test
    fun `setPair to the reverse pair is a swap`() = runBlocking {
        val repo = repository()
        repo.setRate(BigDecimal("0.58"))
        repo.setPair(home = JPY, local = INR)
        assertEquals(BigDecimal("1.724137931"), repo.current().rate)
    }

    @Test
    fun `setPair to the same pair is a no-op`() = runBlocking {
        val repo = repository()
        repo.setRate(BigDecimal("0.58"))
        repo.setPair(home = INR, local = JPY)
        assertEquals(BigDecimal("0.58"), repo.current().rate)
        assertEquals(1, changes)
    }

    @Test
    fun `setPair rejects identical currencies`() {
        val repo = repository()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.setPair(home = INR, local = INR) }
        }
    }

    @Test
    fun `corrupt file recovers to defaults instead of crashing`() = runBlocking {
        val file = tmp.newFile("corrupt.json").apply { writeText("{not json") }
        val config = repository(file).current()
        assertFalse(config.isUsable)
        assertEquals(INR, config.homeCurrency)
    }

    @Test
    fun `invalid stored fields fall back to defaults`() {
        val config = StoredRateConfig(
            homeCurrency = "rupee", localCurrency = "", rate = "abc", source = "NOPE",
        ).toDomain()
        assertEquals(INR, config.homeCurrency)
        assertEquals(JPY, config.localCurrency)
        assertEquals(BigDecimal.ZERO, config.rate)
        assertEquals(RateSource.MANUAL, config.source)
    }

    @Test
    fun `unknown keys from a newer schema are ignored`() = runBlocking {
        val file = tmp.newFile("future.json").apply {
            writeText("""{"homeCurrency":"USD","localCurrency":"EUR","rate":"0.9","futureField":true}""")
        }
        val config = repository(file).current()
        assertEquals(USD, config.homeCurrency)
        assertEquals(BigDecimal("0.9"), config.rate)
    }
}
