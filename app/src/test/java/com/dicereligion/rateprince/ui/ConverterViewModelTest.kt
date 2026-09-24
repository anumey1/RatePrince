package com.dicereligion.rateprince.ui

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.data.RateConfigRepository
import com.dicereligion.rateprince.data.StoredRateConfigSerializer
import com.dicereligion.rateprince.domain.ConversionEngine
import com.dicereligion.rateprince.domain.model.CurrencyCode
import com.dicereligion.rateprince.ui.converter.ConverterUiState
import com.dicereligion.rateprince.ui.converter.ConverterViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.math.BigDecimal
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ConverterViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val catalog = CurrencyCatalog.fromJson(File("src/main/assets/currencies.json").readText())
    private lateinit var repository: RateConfigRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = RateConfigRepository(
            store = DataStoreFactory.create(
                serializer = StoredRateConfigSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { StoredRateConfigSerializer.defaultValue },
                scope = storeScope,
                produceFile = { File(tmp.root, "rate_config.json") },
            ),
            onConfigChanged = {},
        )
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel() = ConverterViewModel(repository, ConversionEngine(), catalog, locale = { Locale.US })

    private suspend fun ConverterViewModel.awaitReady(predicate: (ConverterUiState.Ready) -> Boolean = { true }) =
        withTimeout(5_000) {
            state.first { it is ConverterUiState.Ready && predicate(it) } as ConverterUiState.Ready
        }

    @Test
    fun `unconfigured rate shows the first-run state`() = runBlocking {
        val state = withTimeout(5_000) { viewModel().state.first { it !is ConverterUiState.Loading } }
        assertEquals(ConverterUiState.NeedsRate::class, state::class)
    }

    @Test
    fun `ladder matches an independently computed spreadsheet for three pairs`() = runBlocking {
        val expected = javaClass.classLoader!!.getResource("ladder_spreadsheet.csv")!!.readText()
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("local,") }
            .map { line ->
                val (local, home, rate, amount) = line.split(",", limit = 5)
                val label = line.substringAfter("\"").substringBeforeLast("\"")
                listOf(local, home, rate, amount, label)
            }
            .groupBy { Triple(it[0], it[1], it[2]) }
        assertEquals(3, expected.size)

        expected.forEach { (pair, rows) ->
            val (local, home, rate) = pair
            repository.setPair(home = CurrencyCode(home), local = CurrencyCode(local))
            repository.setRate(BigDecimal(rate))
            val ladder = viewModel().awaitReady { it.local.code.value == local && it.home.code.value == home }.ladder

            assertEquals(46, ladder.size)
            rows.zip(ladder).forEach { (row, actual) ->
                assertEquals("$local→$home ${row[3]}", BigDecimal(row[3]), actual.localAmount)
                assertEquals("$local→$home ${row[3]}", row[4], actual.homeLabel)
            }
        }
    }

    @Test
    fun `typed amount converts and highlights the nearest ladder row`() = runBlocking {
        repository.setRate(BigDecimal("0.58"))
        val vm = viewModel()
        assertNull(vm.awaitReady().convertedLabel)

        vm.onAmountChanged("1480")
        val state = vm.awaitReady { it.convertedLabel != null }
        assertEquals("₹858.40", state.convertedLabel)
        assertEquals(BigDecimal(1500), state.ladder[state.highlightedIndex].localAmount)
        assertEquals("0.58", state.rateLabel)
    }

    @Test
    fun `trailing separator still converts`() = runBlocking {
        repository.setRate(BigDecimal("0.58"))
        val vm = viewModel()
        vm.onAmountChanged("12.")
        assertEquals("₹6.96", vm.awaitReady { it.convertedLabel != null }.convertedLabel)
    }

    @Test
    fun `swap flips the pair and the ladder follows`() = runBlocking {
        repository.setRate(BigDecimal("0.58"))
        val vm = viewModel()
        vm.awaitReady()
        vm.swap()
        val swapped = vm.awaitReady { it.local.code.value == "INR" }
        assertEquals("JPY", swapped.home.code.value)
        assertEquals("¥2", swapped.ladder.first { it.localAmount == BigDecimal.ONE }.homeLabel)
    }

    @Test
    fun `saving a rate from the first-run state makes the converter ready`() = runBlocking {
        val vm = viewModel()
        withTimeout(5_000) { vm.state.first { it is ConverterUiState.NeedsRate } }
        vm.saveRate(BigDecimal("0.58"))
        assertEquals("0.58", vm.awaitReady().rateLabel)
    }
}
