package com.dicereligion.rateprince.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dicereligion.rateprince.R

/**
 * Click targets. Glance click handling is innermost-wins.
 *
 * The keypad and its toggle are plain lambdas, not broadcast Actions: they run inside the
 * widget's live session and change in-memory state, so a key press redraws immediately
 * instead of round-tripping through a callback, a storage write and a session restart.
 */
class WidgetActions(
    /** Whole surface, result row and caption: opens the app on the Converter. */
    val openApp: Action,
    /** Amount row: opens the quick-convert overlay bound to this widget instance. */
    val editAmount: Action,
    /** Wide layout's swap button. */
    val swap: Action,
    val onToggleKeypad: () -> Unit,
    /** A digit or [KeypadInput.DECIMAL]. */
    val onKey: (String) -> Unit,
    val onBackspace: () -> Unit,
    val onClear: () -> Unit,
)

/**
 * Root of the widget UI. Picks a layout from [LocalSize] (the widget's real size under
 * `SizeMode.Exact`). Keep the tree shallow: RemoteViews has a hard nesting limit.
 */
@Composable
fun RatePrinceWidgetContent(model: WidgetModel, actions: WidgetActions) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .then(widgetBackground())
            .padding(WIDGET_PADDING)
            .clickable(actions.openApp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val keypadEligible = size.width >= KEYPAD_MIN_WIDTH && size.height >= KEYPAD_MIN_HEIGHT
        when {
            model is WidgetModel.Unconfigured -> UnconfiguredLayout(model)
            model !is WidgetModel.Ready -> Unit
            size.height < STACKED_MIN_HEIGHT -> SingleLineLayout(model)
            keypadEligible && model.keypadOpen -> KeypadLayout(model, actions)
            size.width >= WIDE_MIN_WIDTH -> WideLayout(model, actions, showKeypadToggle = keypadEligible)
            else -> StackedLayout(
                model, actions,
                large = size.height >= LARGE_MIN_HEIGHT,
                showKeypadToggle = keypadEligible,
            )
        }
    }
}

@Composable
private fun widgetBackground(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        // cornerRadius is a no-op below API 31; the drawable carries a 16dp radius instead.
        GlanceModifier.background(ImageProvider(R.drawable.widget_background))
    }

@Composable
private fun UnconfiguredLayout(model: WidgetModel.Unconfigured) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = model.title,
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(text = model.pair, style = captionStyle(), maxLines = 1)
    }
}

/** 2x1: one line, no input affordance; tapping opens the app. */
@Composable
private fun SingleLineLayout(model: WidgetModel.Ready) {
    Text(
        text = model.singleLineText,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

/** 3x2 and up: amount row / result row / rate caption (+ keypad toggle). The canonical widget. */
@Composable
private fun StackedLayout(
    model: WidgetModel.Ready,
    actions: WidgetActions,
    large: Boolean,
    showKeypadToggle: Boolean,
) {
    // Launcher cells vary a lot in height, so centre the amount/result pair in whatever
    // space there is and keep the caption on the bottom edge.
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Spacer(GlanceModifier.defaultWeight())
        AmountRow(model, actions, large)
        Spacer(GlanceModifier.height(4.dp))
        ResultRow(model, large)
        Spacer(GlanceModifier.defaultWeight())
        CaptionRow(model.rateCaption, model, actions, showKeypadToggle)
    }
}

/** 5x2: stacked plus a swap button and the date the rate was set. */
@Composable
private fun WideLayout(model: WidgetModel.Ready, actions: WidgetActions, showKeypadToggle: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                AmountRow(model, actions, large = false)
                Spacer(GlanceModifier.height(4.dp))
                ResultRow(model, large = false)
            }
            SmallIconButton(R.drawable.ic_swap_vert, model.swapDescription, size = 36.dp, action = actions.swap)
        }
        Spacer(GlanceModifier.defaultWeight())
        CaptionRow(
            text = listOfNotNull(model.rateCaption, model.editedText).joinToString("  ·  "),
            model = model,
            actions = actions,
            showKeypadToggle = showKeypadToggle,
        )
    }
}

@Composable
private fun CaptionRow(text: String, model: WidgetModel.Ready, actions: WidgetActions, showKeypadToggle: Boolean) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = captionStyle(), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (showKeypadToggle) {
            SmallIconButton(
                icon = R.drawable.ic_dialpad,
                description = model.showKeypadDescription,
                size = 24.dp,
                onClick = actions.onToggleKeypad,
                key = "keypad-open",
            )
        }
    }
}

/**
 * Keypad open (Option B). Left 34%: the typed amount (top half) and the result (bottom
 * half); tapping the amount opens the overlay, which has the decimal key. Right 66%: a
 * phone-style 3x4 grid (1-9, then C 0 ⌫) that stretches to fill the widget.
 */
@Composable
private fun KeypadLayout(model: WidgetModel.Ready, actions: WidgetActions) {
    // Glance weights only split space equally, so the 34% panel gets an explicit width
    // from the real widget size (SizeMode.Exact) and the keypad takes the rest.
    val panelWidth = (LocalSize.current.width - WIDGET_PADDING * 2) * PANEL_FRACTION
    Row(modifier = GlanceModifier.fillMaxSize()) {
        Column(modifier = GlanceModifier.fillMaxHeight().width(panelWidth)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.localCode,
                    style = captionStyle(),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                SmallIconButton(
                    icon = R.drawable.ic_close,
                    description = model.hideKeypadDescription,
                    size = 24.dp,
                    onClick = actions.onToggleKeypad,
                    key = "keypad-close",
                )
            }
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actions.editAmount)
                    .semantics { contentDescription = model.amountDescription },
                contentAlignment = Alignment.CenterStart,
            ) {
                PanelValue("${model.localSymbol} ${model.amountText ?: "0"}", GlanceTheme.colors.onSurface, bold = false)
            }
            Text(text = model.homeCode, style = captionStyle(), maxLines = 1)
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .semantics { contentDescription = model.resultDescription },
                contentAlignment = Alignment.CenterStart,
            ) {
                PanelValue("${model.homeSymbol} ${model.resultText}", GlanceTheme.colors.primary, bold = true)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.fillMaxHeight().defaultWeight()) {
            KeyRow(listOf("1", "2", "3"), actions)
            KeyRow(listOf("4", "5", "6"), actions)
            KeyRow(listOf("7", "8", "9"), actions)
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                Key(label = CLEAR_LABEL, onClick = actions.onClear, clickKey = "keypad-clear", description = model.clearDescription)
                Key(label = "0", onClick = { actions.onKey("0") }, clickKey = "keypad-0")
                Key(
                    label = null,
                    onClick = actions.onBackspace,
                    clickKey = "keypad-backspace",
                    description = model.backspaceDescription,
                )
            }
        }
    }
}

/** Left-panel value; shrinks with length because widget text can't auto-size. */
@Composable
private fun PanelValue(text: String, color: ColorProvider, bold: Boolean) {
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = when {
                text.length <= 7 -> 18.sp
                text.length <= 10 -> 15.sp
                else -> 12.sp
            },
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        ),
        maxLines = 2,
    )
}

@Composable
private fun ColumnScope.KeyRow(digits: List<String>, actions: WidgetActions) {
    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        digits.forEach { digit -> Key(label = digit, onClick = { actions.onKey(digit) }, clickKey = "keypad-$digit") }
    }
}

/** One key filling its grid cell. [label] null draws the backspace icon. */
@Composable
private fun RowScope.Key(label: String?, onClick: () -> Unit, clickKey: String, description: String? = null) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .padding(2.dp)
            .cornerRadius(10.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            // Explicit keys: these lambdas are created in loops, so each needs a stable identity.
            .clickable(key = clickKey, block = onClick)
            .semantics { contentDescription = description ?: label.orEmpty() },
        contentAlignment = Alignment.Center,
    ) {
        if (label == null) {
            Image(
                provider = ImageProvider(R.drawable.ic_backspace),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(18.dp),
            )
        } else {
            Text(
                text = label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/** Icon button for a broadcast [action] or an in-session [onClick] lambda. */
@Composable
private fun SmallIconButton(
    icon: Int,
    description: String,
    size: Dp,
    action: Action? = null,
    onClick: (() -> Unit)? = null,
    key: String? = null,
) {
    val clickable = when {
        action != null -> GlanceModifier.clickable(action)
        onClick != null -> GlanceModifier.clickable(key = key, block = onClick)
        else -> GlanceModifier
    }
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        modifier = GlanceModifier
            .size(size)
            .padding(size / 4)
            .cornerRadius(size / 2)
            .then(clickable),
    )
}

/** A Text dressed as an input; tapping it opens the quick-convert overlay. */
@Composable
private fun AmountRow(model: WidgetModel.Ready, actions: WidgetActions, large: Boolean) {
    val text = model.amountText ?: model.amountPlaceholder
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actions.editAmount)
            .semantics { contentDescription = model.amountDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = model.localSymbol, style = symbolStyle(large), maxLines = 1)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = text,
            style = if (model.amountText == null) {
                TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = if (large) 20.sp else 16.sp)
            } else {
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = valueTextSize(text.length, large),
                    fontWeight = FontWeight.Medium,
                )
            },
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(text = model.localCode, style = captionStyle(), maxLines = 1)
    }
}

@Composable
private fun ResultRow(model: WidgetModel.Ready, large: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .semantics { contentDescription = model.resultDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = model.homeSymbol, style = symbolStyle(large), maxLines = 1)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = model.resultText,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = valueTextSize(model.resultText.length, large),
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(text = model.homeCode, style = captionStyle(), maxLines = 1)
    }
}

@Composable
private fun captionStyle() = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)

@Composable
private fun symbolStyle(large: Boolean) =
    TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = if (large) 20.sp else 16.sp)

/** Widget text can't auto-shrink through RemoteViews, so size from the string length. */
internal fun valueTextSize(length: Int, large: Boolean = false): TextUnit {
    val base = when {
        length <= 8 -> 22
        length <= 12 -> 18
        else -> 15
    }
    return (if (large) base + 6 else base).sp
}

/** Calculator convention; the button's content description says "Clear". */
private const val CLEAR_LABEL = "C"

private val WIDGET_PADDING = 10.dp
private const val PANEL_FRACTION = 0.34f
private val STACKED_MIN_HEIGHT = 100.dp
private val WIDE_MIN_WIDTH = 320.dp
private val LARGE_MIN_HEIGHT = 200.dp

/** The keypad fits the default 3x2 widget (180x110dp). */
private val KEYPAD_MIN_WIDTH = 170.dp
private val KEYPAD_MIN_HEIGHT = 100.dp
