package com.dicereligion.rateprince.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.dicereligion.rateprince.MainActivity
import com.dicereligion.rateprince.ui.quick.QuickConvertActivity

object WidgetIntents {
    /** Tells MainActivity to show the Converter even if another screen is on top. */
    val CONVERTER_URI: Uri = "rateprince://converter".toUri()

    private const val QUICK_AMOUNT_PARAM = "amount"

    fun openConverter(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(CONVERTER_URI)

    /**
     * The `data` URI is not optional. PendingIntents are deduplicated by everything
     * except extras, so without a per-widget URI every instance would open the overlay
     * bound to whichever widget was created first. The amount rides in the URI too,
     * so the overlay never opens with a stale value from an older PendingIntent.
     */
    fun quickConvert(context: Context, appWidgetId: Int, amount: String): Intent =
        Intent(context, QuickConvertActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(
                Uri.Builder()
                    .scheme("rateprince")
                    .authority("quick")
                    .appendPath(appWidgetId.toString())
                    .appendQueryParameter(QUICK_AMOUNT_PARAM, amount)
                    .build()
            )

    fun amountFrom(intent: Intent): String = intent.data?.getQueryParameter(QUICK_AMOUNT_PARAM).orEmpty()
}
