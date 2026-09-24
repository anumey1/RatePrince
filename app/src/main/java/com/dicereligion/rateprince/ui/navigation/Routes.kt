package com.dicereligion.rateprince.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object ConverterRoute

@Serializable
data object SettingsRoute

@Serializable
data class CurrencyPickerRoute(val slot: CurrencySlot)

/** Which side of the pair the picker is choosing for. */
enum class CurrencySlot { HOME, LOCAL }
