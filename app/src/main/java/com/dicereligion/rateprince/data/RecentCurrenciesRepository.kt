package com.dicereligion.rateprince.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dicereligion.rateprince.domain.model.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.recentCurrenciesStore: DataStore<Preferences> by preferencesDataStore("currency_recents")

/** The picker's "Recent" section: last [MAX_RECENTS] selections, newest first. */
class RecentCurrenciesRepository(private val store: DataStore<Preferences>) {

    val recent: Flow<List<CurrencyCode>> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[KEY]) }

    suspend fun add(code: CurrencyCode) {
        store.edit { prefs ->
            val updated = (listOf(code) + decode(prefs[KEY]).filter { it != code }).take(MAX_RECENTS)
            prefs[KEY] = updated.joinToString(",") { it.value }
        }
    }

    private fun decode(raw: String?): List<CurrencyCode> =
        raw.orEmpty().split(",").mapNotNull { runCatching { CurrencyCode(it) }.getOrNull() }

    companion object {
        const val MAX_RECENTS = 5
        private val KEY = stringPreferencesKey("codes")
    }
}
