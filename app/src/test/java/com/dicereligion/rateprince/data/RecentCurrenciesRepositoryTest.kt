package com.dicereligion.rateprince.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.dicereligion.rateprince.domain.model.CurrencyCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecentCurrenciesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val repository by lazy {
        RecentCurrenciesRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "recents.preferences_pb") }
        )
    }

    @After
    fun tearDown() = scope.cancel()

    private fun codes(vararg values: String) = values.map(::CurrencyCode)

    @Test
    fun `starts empty`() = runBlocking {
        assertEquals(emptyList<CurrencyCode>(), repository.recent.first())
    }

    @Test
    fun `newest first, no duplicates`() = runBlocking {
        codes("JPY", "USD", "JPY").forEach { repository.add(it) }
        assertEquals(codes("JPY", "USD"), repository.recent.first())
    }

    @Test
    fun `keeps only the last five`() = runBlocking {
        codes("AAA", "BBB", "CCC", "DDD", "EEE", "FFF").forEach { repository.add(it) }
        assertEquals(codes("FFF", "EEE", "DDD", "CCC", "BBB"), repository.recent.first())
    }
}
