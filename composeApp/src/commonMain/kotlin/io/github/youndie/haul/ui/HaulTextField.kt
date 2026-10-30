package io.github.youndie.haul.ui

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import io.github.youndie.haul.theme.HaulColors
import kotlinx.coroutines.launch

/**
 * A field of the store's: Compose's `BasicTextField` over a [value] and [onValueChange], as the string field draws it,
 * with the right button's menu of a field over it (B-155) — «Cut», «Copy», «Paste» and «Select all», drawn as the
 * store's menus. Compose's own field menu on the web is the old one, whose presses did nothing (B-154), and the page
 * takes the right button before it anyway ([Selectable]).
 *
 * The field keeps its selection itself, as the string field does, which is what the menu reads when it opens: the
 * string field's own is out of reach. A change from outside — a search shown again, a field cleared — puts the text
 * in and keeps the selection inside it, as there.
 */
@Composable
internal fun HaulTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    cursorBrush: Brush = SolidColor(HaulColors.primary),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    // The string field's own bookkeeping (`BasicTextField(value: String, …)`), so that the field behaves as it did.
    var state by remember { mutableStateOf(TextFieldValue(value)) }
    val shown = state.copy(text = value)
    SideEffect {
        if (shown.selection != state.selection || shown.composition != state.composition) state = shown
    }
    var last by remember(value) { mutableStateOf(value) }
    val change: (TextFieldValue) -> Unit = { next ->
        state = next
        val changed = last != next.text
        last = next.text
        if (changed) onValueChange(next.text)
    }
    HaulTextField(
        shown,
        change,
        modifier,
        enabled,
        textStyle,
        singleLine,
        keyboardOptions,
        keyboardActions,
        visualTransformation,
        cursorBrush,
        decorationBox,
    )
}

/** [HaulTextField] over a [value] that holds its own selection — the header's search, which moves the caret itself. */
@Composable
internal fun HaulTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    cursorBrush: Brush = SolidColor(HaulColors.primary),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    val focus = remember { FocusRequester() }
    val copier = LocalLinkCopier.current
    val scope = rememberCoroutineScope()
    var attached by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        attached = true
        onDispose { attached = false }
    }
    // The field takes the focus back after a pick, so that what was cut, pasted or selected is drawn in it. A field
    // gone by then — the menu's pick closed its dialog — has no focus to take.
    val refocus: () -> Unit = {
        scope.launch {
            awaitTypedInput()
            // A requester whose field has left the page throws; the field gone, there is nothing to focus.
            if (attached) focus.requestFocus()
        }
    }
    BasicTextField(
        value,
        onValueChange,
        modifier
            .focusRequester(focus)
            .offersMenu { fieldEntries(value, enabled, copier, onValueChange, refocus) },
        enabled = enabled,
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        cursorBrush = cursorBrush,
        decorationBox = decorationBox,
    )
}

/** What the right button offers over a field holding [field]: each entry only where it can do something. */
private fun fieldEntries(
    field: TextFieldValue,
    enabled: Boolean,
    copier: LinkCopier?,
    change: (TextFieldValue) -> Unit,
    refocus: () -> Unit,
): List<MenuEntry> {
    val selection = field.selection
    val selected = field.text.substring(selection.min, selection.max)
    val without = field.text.removeRange(selection.min, selection.max)
    return listOfNotNull(
        MenuEntry(CUT) {
            copier?.copyText(selected) { }
            change(TextFieldValue(without, TextRange(selection.min)))
            refocus()
        }.takeIf { enabled && selected.isNotEmpty() && copier != null },
        MenuEntry(COPY) {
            copier?.copyText(selected) { }
            // The menu took the focus and the field let go of its selection; a browser's field keeps it after a copy.
            change(field)
            refocus()
        }.takeIf { selected.isNotEmpty() && copier != null },
        MenuEntry(PASTE) {
            copier?.pasteText { pasted ->
                if (pasted != null) {
                    val text = without.substring(0, selection.min) + pasted + without.substring(selection.min)
                    change(TextFieldValue(text, TextRange(selection.min + pasted.length)))
                }
                refocus()
            }
        }.takeIf { enabled && copier != null },
        MenuEntry(SELECT_ALL) {
            change(field.copy(selection = TextRange(0, field.text.length)))
            refocus()
        }.takeIf { field.text.isNotEmpty() && selected.length < field.text.length },
    )
}

internal const val CUT: String = "Cut"
internal const val PASTE: String = "Paste"
