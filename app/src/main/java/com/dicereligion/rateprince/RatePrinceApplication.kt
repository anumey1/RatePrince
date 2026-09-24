package com.dicereligion.rateprince

import android.app.Application
import android.content.Context
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.data.RateConfigRepository
import com.dicereligion.rateprince.data.rateConfigStore
import com.dicereligion.rateprince.domain.ConversionEngine

class RatePrinceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Hand-rolled DI root. The widget receiver has no Hilt entry point, so every
 * process-wide singleton lives here and is reached through [Context.container].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val rateConfigRepository = RateConfigRepository(
        store = appContext.rateConfigStore,
        // Phase 3 wires widget refresh here: RatePrinceWidget().updateAll(appContext)
        onConfigChanged = {},
    )

    val conversionEngine = ConversionEngine()

    /** Parsed from assets on first use. */
    val currencyCatalog: CurrencyCatalog by lazy { CurrencyCatalog.fromAssets(appContext) }
}

val Context.container: AppContainer
    get() = (applicationContext as RatePrinceApplication).container
