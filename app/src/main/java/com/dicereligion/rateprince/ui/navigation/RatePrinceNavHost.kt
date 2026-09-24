package com.dicereligion.rateprince.ui.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dicereligion.rateprince.ui.converter.ConverterScreen
import com.dicereligion.rateprince.ui.picker.CurrencyPickerScreen
import com.dicereligion.rateprince.ui.settings.SettingsScreen
import com.dicereligion.rateprince.widget.WidgetIntents

@Composable
fun RatePrinceNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    // Tapping the widget while the app is open on another screen returns to the Converter.
    // MainActivity is singleTop, so that tap arrives here as a new intent.
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent ->
            if (intent.data == WidgetIntents.CONVERTER_URI) {
                navController.popBackStack(ConverterRoute, inclusive = false)
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = ConverterRoute,
        modifier = modifier,
    ) {
        // Every navigation callback only fires while its screen is resumed, so a double tap
        // during a transition can't push a duplicate screen or pop twice.
        composable<ConverterRoute> {
            ConverterScreen(
                onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> { entry ->
            SettingsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onPickCurrency = { slot ->
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        navController.navigate(CurrencyPickerRoute(slot))
                    }
                },
            )
        }
        composable<CurrencyPickerRoute> {
            // The slot reaches the ViewModel through the route's SavedStateHandle.
            CurrencyPickerScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}
