package com.dicereligion.rateprince.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.preferencesOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.dicereligion.rateprince.MainActivity

/**
 * Getting the widget onto the home screen from inside the app (design doc §6.2), plus
 * the API 35+ generated picker previews.
 */
class WidgetPlacement(private val context: Context) {

    data class Status(
        /** False on launchers that don't implement pinning: show instructions instead of a button. */
        val canPin: Boolean,
        val widgetCount: Int,
    ) {
        val hasWidget: Boolean get() = widgetCount > 0
    }

    suspend fun status(): Status = Status(
        canPin = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported,
        widgetCount = GlanceAppWidgetManager(context).getGlanceIds(RatePrinceWidget::class.java).size,
    )

    /**
     * Asks the launcher to show its "Add to home screen?" dialog, with a populated preview
     * rather than "Tap to enter". Returns false if the launcher refused outright. There is
     * no callback for "user cancelled", so callers must not wait for one.
     */
    suspend fun requestPin(): Boolean {
        if (!AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported) return false
        return GlanceAppWidgetManager(context).requestPinGlanceAppWidget(
            receiver = RatePrinceWidgetReceiver::class.java,
            preview = RatePrinceWidget(),
            previewState = preferencesOf(WidgetStateKeys.AMOUNT_INPUT to RatePrinceWidget.PREVIEW_AMOUNT),
            // Fires only when the user confirms and the pin succeeds. Bringing MainActivity
            // back to the front re-runs its on-resume widget check, which hides the add card.
            successCallback = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }

    /**
     * API 35+: pushes a live preview (the user's own pair and rate) to the widget picker.
     * The system rate-limits this, so a skipped call is normal; the next rate change retries.
     */
    suspend fun publishPreviews() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(RatePrinceWidgetReceiver::class) }
    }
}
