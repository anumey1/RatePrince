package com.dicereligion.rateprince.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dicereligion.rateprince.ui.converter.ConverterScreen
import com.dicereligion.rateprince.ui.picker.CurrencyPickerScreen
import com.dicereligion.rateprince.ui.settings.SettingsScreen

@Composable
fun RatePrinceNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

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
