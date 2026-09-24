package com.dicereligion.rateprince

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.dicereligion.rateprince.data.CurrencyCatalog
import com.dicereligion.rateprince.data.RateConfigRepository
import com.dicereligion.rateprince.data.RecentCurrenciesRepository
import com.dicereligion.rateprince.data.rateConfigStore
import com.dicereligion.rateprince.data.recentCurrenciesStore
import com.dicereligion.rateprince.domain.ConversionEngine
import com.dicereligion.rateprince.widget.RatePrinceWidget
import com.dicereligion.rateprince.widget.WidgetPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RatePrinceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep the Android 15+ picker preview current with the user's own pair and rate.
        container.applicationScope.launch { container.widgetPlacement.publishPreviews() }
    }
}

/**
 * Hand-rolled DI root. The widget receiver has no Hilt entry point, so every
 * process-wide singleton lives here and is reached through [Context.container].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** For work that must outlive the screen that started it, e.g. an overlay's final write. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val widgetPlacement = WidgetPlacement(appContext)

    val rateConfigRepository = RateConfigRepository(
        store = appContext.rateConfigStore,
        // Every config change re-renders every widget instance and refreshes the picker preview.
        onConfigChanged = {
            RatePrinceWidget().updateAll(appContext)
            widgetPlacement.publishPreviews()
        },
    )

    val recentCurrencies = RecentCurrenciesRepository(appContext.recentCurrenciesStore)

    val conversionEngine = ConversionEngine()

    /** Parsed from assets on first use. */
    val currencyCatalog: CurrencyCatalog by lazy { CurrencyCatalog.fromAssets(appContext) }
}

val Context.container: AppContainer
    get() = (applicationContext as RatePrinceApplication).container
