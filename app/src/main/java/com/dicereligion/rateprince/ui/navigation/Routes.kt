package com.dicereligion.rateprince.ui.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
data object ConverterRoute

@Serializable
data object SettingsRoute

@Serializable
data class CurrencyPickerRoute(val slot: CurrencySlot)

/** Which side of the pair the picker is choosing for. Kept so R8 can't rename it out of the route. */
@Keep
enum class CurrencySlot { HOME, LOCAL }
