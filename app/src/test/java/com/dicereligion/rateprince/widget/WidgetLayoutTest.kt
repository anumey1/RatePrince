package com.dicereligion.rateprince.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
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
        toggleKeypad = actionRunCallback<ToggleKeypadAction>(),
        clear = actionRunCallback<ClearAmountAction>(),
        backspace = actionRunCallback<BackspaceAction>(),
        key = { actionRunCallback<AppendDigitAction>(actionParametersOf(AppendDigitAction.KEY to it)) },
    )

    private fun hasKey(key: String) =
        hasRunCallbackClickAction<AppendDigitAction>(actionParametersOf(AppendDigitAction.KEY to key))

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
    fun tall_size_renders_stacked_with_keypad_toggle() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_TALL)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500"), actions) }

        onNode(hasText("1,500")).assertExists()
        onNode(hasText("870.00")).assertExists()
        onNode(hasRunCallbackClickAction<EditAmount>()).assertExists()
        onNode(hasRunCallbackClickAction<SwapPairAction>()).assertDoesNotExist()
        onNode(hasContentDescription("Show keypad")).assertExists()
        onNode(hasRunCallbackClickAction<ToggleKeypadAction>()).assertExists()
        onNode(hasKey("7")).assertDoesNotExist()
    }

    @Test
    fun tall_size_with_keypad_open_renders_every_key() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(RatePrinceWidget.SIZE_TALL)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("12.", keypadOpen = true), actions) }

        KeypadInput.DIGITS.forEach { onNode(hasKey(it)).assertExists() }
        onNode(hasKey(KeypadInput.DECIMAL)).assertExists()
        onNode(hasRunCallbackClickAction<BackspaceAction>()).assertExists()
        onNode(hasRunCallbackClickAction<ClearAmountAction>()).assertExists()
        onNode(hasContentDescription("Hide keypad")).assertExists()
        // The header shows the half-typed amount with its separator, and the live result.
        onNode(hasText("¥ 12.")).assertExists()
        onNode(hasText("₹ 6.96")).assertExists()
        // Typing happens on the keypad, so no overlay action at this size.
        onNode(hasRunCallbackClickAction<EditAmount>()).assertDoesNotExist()
    }

    @Test
    fun keypad_never_shows_below_the_size_gate() = runGlanceAppWidgetUnitTest {
        // keypad_open is per-instance state that survives resizing; a smaller size must ignore it.
        setAppWidgetSize(RatePrinceWidget.SIZE_MEDIUM)
        provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500", keypadOpen = true), actions) }

        onNode(hasKey("7")).assertDoesNotExist()
        onNode(hasRunCallbackClickAction<ToggleKeypadAction>()).assertDoesNotExist()
        onNode(hasText("870.00")).assertExists()
    }

    @Test
    fun every_declared_size_renders_the_result() {
        listOf(
            RatePrinceWidget.SIZE_MEDIUM,
            RatePrinceWidget.SIZE_WIDE,
            RatePrinceWidget.SIZE_TALL,
            DpSize(RatePrinceWidget.SIZE_WIDE.width, RatePrinceWidget.SIZE_TALL.height),
        ).forEach { size ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(size)
                provideComposable { RatePrinceWidgetContent(jpyToInrModel("1500"), actions) }
                onNode(hasText("870.00")).assertExists()
            }
        }
    }
}
