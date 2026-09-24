package com.dicereligion.rateprince

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dicereligion.rateprince.ui.navigation.RatePrinceNavHost
import com.dicereligion.rateprince.ui.theme.RatePrinceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RatePrinceTheme {
                RatePrinceNavHost()
            }
        }
    }
}
