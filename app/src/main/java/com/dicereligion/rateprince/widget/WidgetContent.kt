package com.dicereligion.rateprince.widget

import android.os.Build
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
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
import com.dicereligion.rateprince.R

/** Click targets. Glance click handling is innermost-wins. */
class WidgetActions(
    /** Whole surface, result row and caption: opens the app on the Converter. */
    val openApp: Action,
    /** Amount row: opens the quick-convert overlay bound to this widget instance. */
    val editAmount: Action,
    /** Wide layout's swap button. */
    val swap: Action,
    /** Opens/closes the keypad at large sizes. */
    val toggleKeypad: Action,
    val clear: Action,
    val backspace: Action,
    /** A digit or decimal key; the key string is passed through to the callback. */
    val key: (String) -> Action,
)

/**
 * Root of the widget UI. Picks a layout from [LocalSize], which under
 * `SizeMode.Responsive` is one of the declared buckets in [RatePrinceWidget].
 * Keep the tree shallow: RemoteViews has a hard nesting limit.
 */
@Composable
fun RatePrinceWidgetContent(model: WidgetModel, actions: WidgetActions) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .then(widgetBackground())
            .padding(12.dp)
            .clickable(actions.openApp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val keypadEligible = size.width >= KEYPAD_MIN_WIDTH && size.height >= KEYPAD_MIN_HEIGHT
        when {
            model is WidgetModel.Unconfigured -> UnconfiguredLayout(model)
            model !is WidgetModel.Ready -> Unit
            size.height < STACKED_MIN_HEIGHT -> SingleLineLayout(model)
            keypadEligible && model.keypadOpen -> KeypadLayout(model, actions)
            size.width >= WIDE_MIN_WIDTH -> WideLayout(model, actions)
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

/**
 * 3x2 and 4x4: amount row / result row / rate caption. The canonical widget.
 * At keypad-eligible sizes the caption row also carries the keypad toggle.
 */
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
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.rateCaption,
                style = captionStyle(),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (showKeypadToggle) {
                IconButton(R.drawable.ic_dialpad, model.showKeypadDescription, actions.toggleKeypad)
            }
        }
    }
}

/**
 * 4x4 with the keypad open (Option B): a compact amount/result header, then a 4x3 key
 * grid. Three Rows in one Column keeps nesting shallow; every key is at least 48dp.
 */
@Composable
private fun KeypadLayout(model: WidgetModel.Ready, actions: WidgetActions) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "${model.localSymbol} ${model.amountText ?: "0"}",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp),
                    maxLines = 1,
                    modifier = GlanceModifier.semantics { contentDescription = model.amountDescription },
                )
                Text(
                    text = "${model.homeSymbol} ${model.resultText}",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = valueTextSize(model.resultText.length),
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.semantics { contentDescription = model.resultDescription },
                )
            }
            Box(
                modifier = GlanceModifier
                    .size(48.dp)
                    .cornerRadius(24.dp)
                    .clickable(actions.clear)
                    .semantics { contentDescription = model.clearDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = CLEAR_LABEL,
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
            }
            IconButton(R.drawable.ic_close, model.hideKeypadDescription, actions.toggleKeypad)
        }
        Spacer(GlanceModifier.height(4.dp))
        KeyRow(listOf("1", "2", "3"), actions)
        KeyRow(listOf("4", "5", "6"), actions)
        KeyRow(listOf("7", "8", "9"), actions)
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Key(label = model.decimalSeparator, action = actions.key(KeypadInput.DECIMAL))
            Key(label = "0", action = actions.key("0"))
            Key(
                label = null,
                action = actions.backspace,
                icon = R.drawable.ic_backspace,
                description = model.backspaceDescription,
            )
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(keys: List<String>, actions: WidgetActions) {
    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        keys.forEach { Key(label = it, action = actions.key(it)) }
    }
}

@Composable
private fun RowScope.Key(
    label: String?,
    action: Action,
    icon: Int? = null,
    description: String? = null,
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .padding(2.dp)
            .cornerRadius(12.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(action)
            .semantics { contentDescription = description ?: label.orEmpty() },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(20.dp),
            )
        } else {
            Text(
                text = label.orEmpty(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun IconButton(icon: Int, description: String, action: Action) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        modifier = GlanceModifier
            .size(48.dp)
            .padding(12.dp)
            .cornerRadius(24.dp)
            .clickable(action),
    )
}

/** 5x2: stacked plus a swap button and the date the rate was set. */
@Composable
private fun WideLayout(model: WidgetModel.Ready, actions: WidgetActions) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                AmountRow(model, actions, large = false)
                Spacer(GlanceModifier.height(4.dp))
                ResultRow(model, large = false)
            }
            IconButton(R.drawable.ic_swap_vert, model.swapDescription, actions.swap)
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = listOfNotNull(model.rateCaption, model.editedText).joinToString("  ·  "),
            style = captionStyle(),
            maxLines = 1,
        )
    }
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

private val STACKED_MIN_HEIGHT = 100.dp
private val WIDE_MIN_WIDTH = 300.dp
private val LARGE_MIN_HEIGHT = 200.dp

/** Calculator convention; the button's content description says "Clear". */
private const val CLEAR_LABEL = "C"

/** Keypad gate from the design doc: large enough for 48dp keys plus the header. */
private val KEYPAD_MIN_WIDTH = 240.dp
private val KEYPAD_MIN_HEIGHT = 200.dp
