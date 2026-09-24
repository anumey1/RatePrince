package com.dicereligion.rateprince.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
        composable<ConverterRoute> {
            ConverterScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onPickCurrency = { slot -> navController.navigate(CurrencyPickerRoute(slot)) },
            )
        }
        composable<CurrencyPickerRoute> { entry ->
            CurrencyPickerScreen(
                slot = entry.toRoute<CurrencyPickerRoute>().slot,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
