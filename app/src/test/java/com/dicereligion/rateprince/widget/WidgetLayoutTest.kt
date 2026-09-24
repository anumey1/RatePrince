package com.dicereligion.rateprince.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Size-bucket regressions are the most common widget bug and the cheapest to test.
 * Actions are stand-in callbacks so each click target can be told apart.
 * Runs on API 30 (drawable background fallback) and 36 (dynamic colour, system radius).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 36])
class WidgetLayoutTest {

    /** 4x4. Not a preview size, but the widget renders at any real size (SizeMode.Exact). */
    private val SIZE_TALL = DpSize(250.dp, 250.dp)

    private class OpenApp : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Unit
    }

    private class EditAmount : ActionCallback {
        override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Unit
    }

    private val actions = WidgetActions(
        openApp = actionRunCallback<OpenApp>(),
        editAmount = actionRunCallback<EditAmount>(),
        swap = actionRunCallback<SwapPairAction>(),
        onToggleKeypad = {},
        onKey = {},
        onBackspace = {},
        onClear = {},
    )

    /** Keypad keys are in-session lambdas; they're found by their exact content description. */
    private fun key(description: String) = hasContentDescriptionEqualTo(description)

    @Test
    fun unconfigured_state_prompts_for_rate() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_MEDIUM)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500", rate = "0"), actions) }

        onNode(hasText("Tap to set your rate")).assertExists()
        onNode(hasText("1,500")).assertDoesNotExist()
        onNode(hasRunCallbackClickAction<EditAmount>()).assertDoesNotExist()
        onNode(hasRunCallbackClickAction<OpenApp>()).assertExists()
    }

    @Test
    fun narrow_size_renders_single_line() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_NARROW)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500"), actions) }

        onNode(hasText("¥1,500 → ₹870.00")).assertExists()
        // No input affordance at 2x1: the whole surface opens the app.
        onNode(hasRunCallbackClickAction<EditAmount>()).assertDoesNotExist()
        onNode(hasRunCallbackClickAction<OpenApp>()).assertExists()
    }

    @Test
    fun medium_size_renders_stacked_amount_result_and_caption() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_MEDIUM)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500"), actions) }

        onNode(hasText("1,500")).assertExists()
        onNode(hasText("870.00")).assertExists()
        onNode(hasText("1 JPY = 0.58 INR")).assertExists()
        onNode(hasRunCallbackClickAction<EditAmount>()).assertExists()
        onNode(hasContentDescription("Amount, 1,500 JPY, tap to edit")).assertExists()
        // The swap button and edited date are wide-only.
        onNode(hasRunCallbackClickAction<SwapPairAction>()).assertDoesNotExist()
        onNode(hasText("Edited 24 Sep")).assertDoesNotExist()
    }

    @Test
    fun medium_size_with_no_amount_shows_placeholder() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_MEDIUM)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel(""), actions) }

        onNode(hasText("Tap to enter")).assertExists()
        onNode(hasText("—")).assertExists()
    }

    @Test
    fun wide_size_adds_swap_button_and_edited_date() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_WIDE)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500"), actions) }

        onNode(hasText("870.00")).assertExists()
        onNode(hasText("Edited 24 Sep")).assertExists()
        onNode(hasRunCallbackClickAction<SwapPairAction>()).assertExists()
        onNode(hasContentDescription("Swap currencies")).assertExists()
    }

    @Test
    fun stacked_sizes_offer_the_keypad_toggle() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_MEDIUM)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500"), actions) }

        onNode(hasText("870.00")).assertExists()
        onNode(key("Show keypad")).assertHasClickAction()
        onNode(key("7")).assertDoesNotExist()
    }

    @Test
    fun keypad_fits_the_default_3x2_widget() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_MEDIUM)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("12.", keypadOpen = true), actions) }

        // Phone-style 3x4 grid: 1-9, then C 0 ⌫.
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { onNode(key(it)).assertHasClickAction() }
        onNode(key("Clear")).assertHasClickAction()
        onNode(key("Delete")).assertHasClickAction()
        onNode(key("Hide keypad")).assertHasClickAction()
        onNode(key(".")).assertDoesNotExist()
        // Left panel: half-typed amount (with its separator) over the live result.
        onNode(hasText("¥ 12.")).assertExists()
        onNode(hasText("₹ 6.96")).assertExists()
        // Tapping the amount opens the overlay, which is where the decimal key lives.
        onNode(hasRunCallbackClickAction<EditAmount>()).assertExists()
    }

    @Test
    fun keypad_also_opens_at_wide_and_tall_sizes() {
        listOf(RatePrinceWidget.SIZE_WIDE, SIZE_TALL).forEach { size ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(size)
                provideComposable { RatePrinceWidgetContent(jpyToInrModel("1", keypadOpen = true), actions) }
                onNode(key("7")).assertHasClickAction()
            }
        }
    }

    @Test
    fun keypad_never_shows_at_one_row() = runGlanceAppWidgetUnitTest {
        // keypad_open is per-instance state that survives resizing; 2x1 must ignore it.
        setAppWidgetSize(RatePrinceWidget.SIZE_NARROW)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500", keypadOpen = true), actions) }

        onNode(key("7")).assertDoesNotExist()
        onNode(key("Show keypad")).assertDoesNotExist()
        onNode(hasText("¥1,500 → ₹870.00")).assertExists()
    }

    @Test
    fun every_declared_size_renders_the_result() {
        listOf(
            RatePrinceWidget.SIZE_MEDIUM,
            RatePrinceWidget.SIZE_WIDE,
            SIZE_TALL,
            DpSize(RatePrinceWidget.SIZE_WIDE.width, SIZE_TALL.height),
        ).forEach { size ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(size)
                provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500"), actions) }
                onNode(hasText("870.00")).assertExists()
            }
        }
    }
}
