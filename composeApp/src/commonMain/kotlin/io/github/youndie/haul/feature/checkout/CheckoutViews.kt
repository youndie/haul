package io.github.youndie.haul.feature.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.theme.HaulColors
import io.github.youndie.haul.theme.HaulType
import io.github.youndie.haul.theme.HaulType.browserLeading
import io.github.youndie.haul.theme.LocalHaulCompact
import io.github.youndie.haul.ui.CheckoutAddress
import io.github.youndie.haul.ui.CheckoutBody
import io.github.youndie.haul.ui.CheckoutHeader
import io.github.youndie.haul.ui.CheckoutNotice
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.DeliveryMethods
import io.github.youndie.haul.ui.DeliverySlots
import io.github.youndie.haul.ui.FormField
import io.github.youndie.haul.ui.HaulIcons
import io.github.youndie.haul.ui.Icon
import io.github.youndie.haul.ui.Logo
import io.github.youndie.haul.ui.PaymentMethods
import io.github.youndie.haul.ui.PickupPoints
import io.github.youndie.haul.ui.PointsToggle
import io.github.youndie.haul.ui.SummaryRow
import io.github.youndie.haul.ui.Text
import io.github.youndie.haul.ui.follows
import io.github.youndie.haul.ui.gutter
import io.github.youndie.haul.ui.normal
import io.github.youndie.haul.ui.pressable
import io.github.youndie.haul.ui.toneColor

// The Checkout screen (screen-checkout): the checkout's own header, then the numbered sections in one
// column with the order beside them at 1440 and under them on a phone. The numbers are the artboards'
// (Checkout_Content, _PickupPoint, _ParcelLocker, _Validation, _PlaceError, _Placing, _PointsApplied and
// their _Phone twins). Every press is a [CheckoutCommand] for the renderer to send; the order is placed
// through [onPlace], and while it is being placed the sections are drawn at half strength and do nothing.

/**
 * The checkout's header: the logo, the steps — those done ticked in acid, the current one in cobalt — and
 * «Secure checkout».
 */
@Composable
public fun CheckoutHeaderView(
    header: CheckoutHeader,
    onHome: (() -> Unit)? = null,
) {
    val compact = LocalHaulCompact.current
    val logo = Modifier.pressable(onHome)
    Column(Modifier.fillMaxWidth().background(HaulColors.surfaceContainerLowest)) {
        if (compact) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo(size = 34f, dot = 9.dp, dotMargin = 3.dp, modifier = logo)
                Spacer(Modifier.weight(1f))
                Secure(header.secureLabel)
            }
            Hairline()
            // `height: 52px` and a top border outside it: the hairline above, then 52 px of steps.
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Steps(header)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo(size = 44f, dot = 11.dp, dotMargin = 3.dp, modifier = logo)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Steps(header) }
                Secure(header.secureLabel)
            }
        }
        Hairline()
    }
}

@Composable
private fun Steps(header: CheckoutHeader) {
    val compact = LocalHaulCompact.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        header.steps.forEachIndexed { index, step ->
            if (index > 0) {
                Box(Modifier.size(if (compact) 20.dp else 56.dp, 2.dp).background(HaulColors.outlineVariant))
            }
            val current = index == header.current
            // Only before a step that is one of them: a `current` outside the steps marks none done either.
            val done = index < header.current && header.current < header.steps.size
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val circle = if (compact) 24.dp else 30.dp
                Box(
                    Modifier
                        .size(circle)
                        .testTag(stepTag(index))
                        .semantics { selected = current }
                        .then(
                            when {
                                current -> Modifier.background(HaulColors.primary, CircleShape)
                                done -> Modifier.background(HaulColors.secondaryContainer, CircleShape)
                                else -> Modifier.border(2.dp, HaulColors.outlineControl, CircleShape)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(HaulIcons.checkBold, if (compact) 12.dp else 14.dp, HaulColors.onSecondaryContainer)
                    } else {
                        Text(
                            "${index + 1}",
                            HaulType
                                .label(if (compact) 11f else 13f, 600, 0f)
                                .copy(color = if (current) HaulColors.onPrimary else HaulColors.outline),
                            softWrap = false,
                        )
                    }
                }
                Text(
                    step,
                    normal(if (compact) 13f else 15f, 600)
                        .copy(color = if (current || done) HaulColors.onSurface else HaulColors.outline),
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun Secure(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(HaulIcons.lock, 16.dp, HaulColors.onSurface)
        Text(label.uppercase(), HaulType.label(11f, 600, 0.06f), softWrap = false)
    }
}

/**
 * The checkout under its header: the title, the notices and the numbered sections, and the order.
 * [placing] is the order on its way (`Checkout_Placing`): the sections at half strength and inert, the
 * button saying so. [onPlace] places the order the summary quotes.
 */
@Composable
public fun CheckoutBodyView(
    body: CheckoutBody,
    placing: Boolean = false,
    onCommand: (CheckoutCommand) -> Unit = {},
    onPlace: () -> Unit = {},
) {
    val compact = LocalHaulCompact.current
    val send: (CheckoutCommand) -> Unit = { if (!placing) onCommand(it) }
    Column(
        Modifier.fillMaxWidth().padding(
            start = gutter(),
            end = gutter(),
            top = if (compact) 24.dp else 40.dp,
            bottom = if (compact) 64.dp else 96.dp,
        ),
    ) {
        Text(
            body.title,
            HaulType
                .display(
                    if (compact) 56f else 112f,
                    800,
                    letterSpacing = if (compact) -0.01f else -0.03f,
                    lineHeight = 0.9f,
                ).copy(lineBreak = LineBreak.Simple),
            Modifier.padding(bottom = if (compact) 24.dp else 40.dp),
        )
        val sections: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp)) {
                body.notices.forEach { Notice(it) }
                var number = 0
                val dim = if (placing) Modifier.alpha(0.5f) else Modifier
                Section(++number, body.methods.title, dim) { Methods(body.methods, send) }
                body.address?.let { address ->
                    Section(++number, address.title, dim) { AddressForm(address, enabled = !placing, send) }
                }
                body.slots?.let { slots -> Section(++number, slots.title, dim) { Slots(slots, send) } }
                body.points?.let { points -> Section(++number, points.title, dim) { Points(points, send) } }
                Section(++number, body.payment.title, dim) { Payment(body.payment, send) }
            }
        }
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                sections(Modifier.fillMaxWidth())
                Summary(body.summary, placing, onPlace, Modifier.fillMaxWidth())
            }
        } else {
            Row {
                sections(Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
                Summary(body.summary, placing, onPlace, Modifier.width(420.dp))
            }
        }
    }
}

/** A banner in the sale red's container: why the order cannot go as it was (Checkout_PlaceError). */
@Composable
private fun Notice(notice: CheckoutNotice) {
    val compact = LocalHaulCompact.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(HaulColors.errorContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 16.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(HaulIcons.alert, 22.dp, HaulColors.error)
        Text(notice.text, normal(16f, 700), Modifier.weight(1f))
    }
}

/** A white card with its number in mono and its title in Bodoni, the content under them. */
@Composable
private fun Section(
    number: Int,
    title: String,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val compact = LocalHaulCompact.current
    Column(
        modifier
            .fillMaxWidth()
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(24.dp))
            .padding(if (compact) 20.dp else 32.dp),
    ) {
        Row(Modifier.padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                number.toString().padStart(2, '0'),
                HaulType.label(13f, 600, 0f).copy(color = HaulColors.primary),
                Modifier.alignByBaseline(),
                softWrap = false,
            )
            Text(
                title,
                HaulType.display(if (compact) 26f else 32f, 800, letterSpacing = -0.01f),
                Modifier.alignByBaseline(),
            )
        }
        content()
    }
}

/** «How to receive»: one card per method, three across at 1440, stacked on a phone. */
@Composable
private fun Methods(
    methods: DeliveryMethods,
    send: (CheckoutCommand) -> Unit,
) {
    val compact = LocalHaulCompact.current
    SpanGrid(columns = if (compact) 1 else 3, gap = 12.dp, rowGap = 12.dp) {
        methods.options.forEach { option ->
            OptionCard(
                option.label,
                option.detail,
                option.price,
                option.selected,
                Modifier.testTag(methodTag(option.method)).pressable(
                    if (option.selected) {
                        null
                    } else {
                        (
                            {
                                send(
                                    CheckoutCommand.Choose(methods.url, CheckoutChoice(method = option.method)),
                                )
                            }
                        )
                    },
                ),
            )
        }
    }
}

/** A choice drawn as a card: outlined in cobalt with the acid tick when chosen. */
@Composable
private fun OptionCard(
    label: String,
    detail: String?,
    price: String?,
    selected: Boolean,
    modifier: Modifier,
) {
    val compact = LocalHaulCompact.current
    val border = if (selected) 2.dp else 1.dp
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .border(border, if (selected) HaulColors.primary else HaulColors.outlineVariant, shape)
            .padding(border),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // CSS's border-box: the padding is inside the border, which the outer box has taken.
                .padding(horizontal = if (compact) 18.dp else 20.dp, vertical = if (compact) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, normal(17f, 700), softWrap = false)
            detail?.let { Text(it, normal(14f).copy(color = HaulColors.outline)) }
            price?.let { Text(it, normal(14f, 600), Modifier.padding(top = 6.dp), softWrap = false) }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    // Placed from the padding box, inside the border, as `position: absolute` is.
                    .padding(top = if (compact) 14.dp else 16.dp, end = 16.dp)
                    .size(24.dp)
                    .background(HaulColors.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(HaulIcons.checkBold, 14.dp, HaulColors.onSecondaryContainer) }
        }
    }
}

/**
 * The address form: the fields hold the address delivered to, and what the shopper changes is sent as
 * an [AddressEntry] once they leave the form (or press Enter in a field) — the server keeps a refused
 * form and draws it again with an error under each field at fault, and the button says what to fill in.
 */
@Composable
private fun AddressForm(
    address: CheckoutAddress,
    enabled: Boolean,
    send: (CheckoutCommand) -> Unit,
) {
    val compact = LocalHaulCompact.current
    val values = remember(address.form) { mutableStateMapOf(*address.form.map { it.name to it.value }.toTypedArray()) }
    var focused by remember { mutableStateOf<String?>(null) }
    val latest by rememberUpdatedState(address)
    val focus = LocalFocusManager.current
    // Focus moving from one field to the next passes through no `null`: the form is sent only once the
    // shopper has left it, and only when something in it changed.
    LaunchedEffect(focused) {
        if (focused != null) return@LaunchedEffect
        val form = latest.form
        if (form.any { values[it.name].orEmpty() != it.value }) {
            send(CheckoutCommand.SaveAddress(latest.url, entry(values)))
        }
    }
    val spans = address.form.map { (if (compact) PHONE_SPANS else WIDE_SPANS)[it.name] ?: if (compact) 2 else 4 }
    SpanGrid(columns = if (compact) 2 else 4, gap = 16.dp, rowGap = 16.dp, spans = spans) {
        address.form.forEach { field ->
            FieldCell(
                field,
                values[field.name].orEmpty(),
                enabled,
                onValue = { values[field.name] = it },
                onFocus = { has -> focused = if (has) field.name else focused.takeUnless { it == field.name } },
                onDone = { focus.clearFocus() },
            )
        }
    }
}

private fun entry(values: Map<String, String>): AddressEntry =
    AddressEntry(
        street = values["street"].orEmpty(),
        apt = values["apt"].orEmpty(),
        city = values["city"].orEmpty(),
        zip = values["zip"].orEmpty(),
        doorCode = values["doorCode"].orEmpty(),
        courierNote = values["courierNote"].orEmpty(),
    )

/** How many of the four columns (two on a phone) each field takes (Checkout_Content). */
private val WIDE_SPANS = mapOf("street" to 3, "apt" to 1, "city" to 2, "zip" to 1, "doorCode" to 1, "courierNote" to 4)
private val PHONE_SPANS = mapOf("street" to 2, "apt" to 1, "city" to 1, "zip" to 1, "doorCode" to 1, "courierNote" to 2)

/** A field: its label in mono capitals, the box — ink when focused, sale red at fault — and the error under it. */
@Composable
private fun FieldCell(
    field: FormField,
    value: String,
    enabled: Boolean,
    onValue: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val error = field.error
    val border =
        when {
            error != null -> 2.dp to HaulColors.error
            hasFocus -> 2.dp to HaulColors.onSurface
            else -> 1.dp to HaulColors.outlineVariant
        }
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            field.label.uppercase(),
            HaulType.label(11f, 600, 0.08f).copy(color = HaulColors.outline),
            softWrap = false,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(HaulColors.surfaceContainerLowest, shape)
                .border(border.first, border.second, shape)
                .padding(horizontal = 16.dp + border.first),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                field.placeholder?.let { Text(it, normal(16f).copy(color = HaulColors.outlineMuted), softWrap = false) }
            }
            BasicTextField(
                value,
                onValue,
                Modifier
                    .fillMaxWidth()
                    .testTag(fieldTag(field.name))
                    .onFocusChanged {
                        hasFocus = it.isFocused
                        onFocus(it.isFocused)
                    },
                enabled = enabled,
                textStyle = HaulType.text(16f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                cursorBrush = SolidColor(HaulColors.primary),
            )
        }
        error?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(HaulIcons.alert, 14.dp, HaulColors.error)
                Text(it, normal(13f, 600).copy(color = HaulColors.error))
            }
        }
    }
}

/**
 * «Delivery time»: the five days as tiles — the one shown in ink — and that day's windows as pills, the
 * chosen one in acid, a full one struck through. Showing another day is the page's own; choosing a
 * window is a command.
 */
@Composable
private fun Slots(
    slots: DeliverySlots,
    send: (CheckoutCommand) -> Unit,
) {
    val compact = LocalHaulCompact.current
    val initial = slots.days.indexOfFirst { it.selected }.coerceAtLeast(0)
    var shown by remember(slots.days) { mutableStateOf(initial) }
    slots.notice?.let { notice ->
        Row(
            Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HaulIcons.alert, 16.dp, HaulColors.error)
            Text(notice, normal(14f, 600).copy(color = HaulColors.error))
        }
    }
    SpanGrid(
        columns = 5,
        gap = if (compact) 6.dp else 10.dp,
        rowGap = 0.dp,
        modifier = Modifier.padding(bottom = 14.dp),
    ) {
        slots.days.forEachIndexed { index, day ->
            val selected = index == shown
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .testTag(dayTag(index))
                    .pressable { shown = index }
                    .then(
                        if (selected) {
                            Modifier.background(HaulColors.inverseSurface, shape)
                        } else {
                            Modifier.border(1.dp, HaulColors.outlineVariant, shape).padding(1.dp)
                        },
                    ).padding(horizontal = if (compact) 4.dp else 14.dp, vertical = if (compact) 12.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    day.weekday.uppercase(),
                    HaulType
                        .label(11f, 600, 0.06f)
                        .copy(color = if (selected) HaulColors.secondaryContainer else HaulColors.outline),
                    softWrap = false,
                )
                Text(
                    day.date,
                    HaulType
                        .display(if (compact) 26f else 32f, 800, letterSpacing = -0.01f)
                        .copy(color = if (selected) HaulColors.onPrimary else HaulColors.onSurface),
                    Modifier.padding(top = 8.dp),
                    softWrap = false,
                )
            }
        }
    }
    val windows =
        slots.days
            .getOrNull(shown)
            ?.slots
            .orEmpty()
    val pill: @Composable (Int, Modifier) -> Unit = { index, modifier ->
        val slot = windows[index]
        val shape = RoundedCornerShape(999.dp)
        val style =
            when {
                slot.selected -> {
                    normal(15f, 700)
                }

                !slot.available -> {
                    normal(15f, 500).copy(color = HaulColors.outlineMuted, textDecoration = TextDecoration.LineThrough)
                }

                else -> {
                    normal(15f, 500)
                }
            }
        Box(
            modifier
                .testTag(slotTag(slot.id))
                .pressable(
                    if (slot.available &&
                        !slot.selected
                    ) {
                        ({ send(CheckoutCommand.Choose(slots.url, CheckoutChoice(slotId = slot.id))) })
                    } else {
                        null
                    },
                ).then(
                    when {
                        slot.selected -> Modifier.background(HaulColors.secondaryContainer, shape)
                        !slot.available -> Modifier.background(HaulColors.background, shape)
                        else -> Modifier.border(1.dp, HaulColors.outlineVariant, shape).padding(1.dp)
                    },
                ).padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(slot.label, style.copy(textAlign = TextAlign.Center), softWrap = false)
        }
    }
    if (compact) {
        SpanGrid(columns = 2, gap = 10.dp, rowGap = 10.dp) { windows.indices.forEach { pill(it, Modifier) } }
    } else {
        // A flex row stretches its items: a pill without a border is as tall as one with.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            windows.indices.forEach { pill(it, Modifier.fillMaxHeight()) }
        }
    }
}

/** The points or the lockers, one row each with a radio, the place, its hours and day, and how far it is. */
@Composable
private fun Points(
    points: PickupPoints,
    send: (CheckoutCommand) -> Unit,
) {
    val compact = LocalHaulCompact.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, HaulColors.outlineVariant, shape)
            .padding(1.dp)
            .clip(shape),
    ) {
        points.points.forEachIndexed { index, point ->
            if (index > 0) Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag(pointTag(point.id))
                    .pressable(
                        if (point.selected) {
                            null
                        } else {
                            (
                                {
                                    send(
                                        CheckoutCommand.Choose(points.url, CheckoutChoice(pointId = point.id)),
                                    )
                                }
                            )
                        },
                    ).then(if (point.selected) Modifier.background(HaulColors.background) else Modifier)
                    .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 16.dp else 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Radio(point.selected)
                Column(Modifier.weight(1f)) {
                    Text(point.name, normal(16f, 700))
                    Text(point.detail, normal(14f).copy(color = HaulColors.outline), Modifier.padding(top = 4.dp))
                }
                point.distance?.let {
                    Text(it, HaulType.label(12f, 600, 0f).copy(color = HaulColors.outline), softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Radio(selected: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .background(HaulColors.surfaceContainerLowest, CircleShape)
            .border(
                if (selected) 6.dp else 2.dp,
                if (selected) HaulColors.primary else HaulColors.outlineControl,
                CircleShape,
            ),
    )
}

/** «Payment»: one card per way to pay, and the points toggle under them. */
@Composable
private fun Payment(
    payment: PaymentMethods,
    send: (CheckoutCommand) -> Unit,
) {
    val compact = LocalHaulCompact.current
    SpanGrid(columns = if (compact) 1 else 3, gap = 12.dp, rowGap = 12.dp) {
        payment.options.forEach { option ->
            OptionCard(
                option.label,
                option.detail,
                null,
                option.selected,
                Modifier.testTag(paymentTag(option.id)).pressable(
                    if (option.selected) {
                        null
                    } else {
                        (
                            {
                                send(
                                    CheckoutCommand.Choose(payment.url, CheckoutChoice(payment = option.id)),
                                )
                            }
                        )
                    },
                ),
            )
        }
    }
    payment.points?.let { toggle ->
        val url = toggle.url
        PointsSwitch(
            toggle,
            if (url == null) null else ({ send(CheckoutCommand.Choose(url, CheckoutChoice(usePoints = !toggle.on))) }),
        )
    }
}

/**
 * «Use 2,480 points (−$24.80)», off on Paper or on in acid (Checkout_PointsApplied). A press turns it the
 * other way ([onToggle], `CheckoutChoice.usePoints` to the toggle's `url`, B-23); without a `url` it is
 * drawn and does nothing.
 */
@Composable
private fun PointsSwitch(
    toggle: PointsToggle,
    onToggle: (() -> Unit)?,
) {
    val compact = LocalHaulCompact.current
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .testTag(POINTS_TAG)
            .pressable(onToggle)
            .background(
                if (toggle.on) HaulColors.secondaryContainer else HaulColors.background,
                RoundedCornerShape(18.dp),
            ).padding(horizontal = if (compact) 16.dp else 20.dp, vertical = if (compact) 16.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp, 26.dp)
                .background(
                    if (toggle.on) HaulColors.primary else HaulColors.surfaceContainerHighest,
                    RoundedCornerShape(13.dp),
                ).padding(3.dp),
            contentAlignment = if (toggle.on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).background(HaulColors.surfaceContainerLowest, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(toggle.label, normal(16f, 700))
            Text(
                toggle.detail,
                normal(14f).copy(color = if (toggle.on) HaulColors.onSurface else HaulColors.outline),
                Modifier.padding(top = 3.dp),
            )
        }
    }
}

/**
 * «Your order» and «Back to cart» beside it, the items' tiles — each opens its product — the rows, the total,
 * «Place order» and what placing it means. While the order is being placed the links do nothing.
 */
@Composable
private fun Summary(
    summary: CheckoutSummary,
    placing: Boolean,
    onPlace: () -> Unit,
    modifier: Modifier,
) {
    val compact = LocalHaulCompact.current
    Column(
        modifier
            .background(HaulColors.surfaceContainerLowest, RoundedCornerShape(28.dp))
            .padding(if (compact) 24.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                summary.title,
                HaulType.display(32f, 800, letterSpacing = -0.01f),
                Modifier.alignByBaseline(),
                softWrap = false,
            )
            Spacer(Modifier.weight(1f))
            summary.back?.let { back ->
                Text(
                    back.label,
                    HaulType.text(15f, 700).copy(color = HaulColors.primary),
                    Modifier.alignByBaseline().testTag(BACK_TO_CART_TAG).follows(back.action.takeUnless { placing }),
                    softWrap = false,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            summary.items.forEachIndexed { index, item ->
                Box(
                    Modifier
                        .size(72.dp)
                        .testTag(summaryItemTag(index))
                        .follows(item.action.takeUnless { placing })
                        .background(toneColor(item.tone), RoundedCornerShape(14.dp)),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { summary.rows.forEach { SummaryLine(it) } }
        Hairline()
        Row(Modifier.fillMaxWidth()) {
            Text(summary.totalLabel, normal(17f, 700), Modifier.alignByBaseline(), softWrap = false)
            Spacer(Modifier.weight(1f))
            Text(
                summary.total,
                HaulType.display(56f, 800, letterSpacing = -0.01f),
                Modifier.alignByBaseline(),
                softWrap = false,
            )
        }
        PlaceButton(summary, placing, onPlace)
        summary.placeHint?.let {
            Text(
                it,
                normal(13f, 600).copy(color = HaulColors.error, textAlign = TextAlign.Center),
                Modifier.fillMaxWidth(),
            )
        }
        summary.note?.let {
            Text(it, HaulType.text(13f, lineHeight = 1.5f).browserLeading().copy(color = HaulColors.outline))
        }
    }
}

/** Cobalt while the order can be placed, Paper's grey while it cannot, the spinner while it is being placed. */
@Composable
private fun PlaceButton(
    summary: CheckoutSummary,
    placing: Boolean,
    onPlace: () -> Unit,
) {
    val enabled = summary.placeEnabled || placing
    val content = if (enabled) HaulColors.onPrimary else HaulColors.outlineMuted
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(PLACE_TAG)
            .pressable(if (summary.placeEnabled && !placing) onPlace else null)
            .height(64.dp)
            .background(if (enabled) HaulColors.primary else HaulColors.outlineVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (placing) {
            Box {
                Icon(HaulIcons.spinnerTrack, 22.dp, HaulColors.onPrimary.copy(alpha = 0.3f))
                Icon(HaulIcons.spinnerArc, 22.dp, HaulColors.onPrimary)
            }
        }
        Text(
            if (placing) summary.placingLabel else summary.placeLabel,
            normal(18f, 700).copy(color = content),
            softWrap = false,
        )
    }
}

@Composable
private fun SummaryLine(row: SummaryRow) {
    val style = normal(15f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(row.label, style.copy(color = HaulColors.outline), Modifier.weight(1f))
        Text(
            row.value,
            if (row.saving) normal(15f, 600).copy(color = HaulColors.error) else style,
            softWrap = false,
        )
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HaulColors.outlineVariant))
}

/**
 * CSS's grid of [columns] equal tracks [gap] apart, each child taking [spans] of them (one each by
 * default), rows [rowGap] apart; the cells of a row are as tall as its tallest, as a grid stretches them.
 */
@Composable
internal fun SpanGrid(
    columns: Int,
    gap: Dp,
    rowGap: Dp,
    modifier: Modifier = Modifier,
    spans: List<Int> = emptyList(),
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val rowGapPx = rowGap.roundToPx()
        val width = constraints.maxWidth
        val track = (width - gapPx * (columns - 1)).toFloat() / columns

        // Place each child in a row: a child that does not fit in what is left starts the next one.
        val rows = mutableListOf<MutableList<Pair<Int, Int>>>()
        var used = columns
        measurables.indices.forEach { index ->
            val span = (spans.getOrNull(index) ?: 1).coerceIn(1, columns)
            if (used + span > columns) {
                rows += mutableListOf<Pair<Int, Int>>()
                used = 0
            }
            rows.last() += index to used
            used += span
        }

        fun cellWidth(index: Int): Int {
            val span = (spans.getOrNull(index) ?: 1).coerceIn(1, columns)
            return (track * span + gapPx * (span - 1)).toInt()
        }
        val placed =
            rows.map { row ->
                val height = row.maxOf { (index, _) -> measurables[index].minIntrinsicHeight(cellWidth(index)) }
                row.map { (index, start) ->
                    val w = cellWidth(index)
                    Triple(
                        measurables[index].measure(Constraints.fixed(w, height)),
                        (track * start + gapPx * start).toInt(),
                        height,
                    )
                }
            }
        val total = placed.sumOf { it.first().third } + rowGapPx * (placed.size - 1).coerceAtLeast(0)
        layout(width, total) {
            var y = 0
            placed.forEach { row ->
                row.forEach { (placeable, x, _) -> placeable.place(x, y) }
                y += row.first().third + rowGapPx
            }
        }
    }
}

internal fun methodTag(method: DeliveryMethod): String = "checkout-method:${method.name}"

internal fun fieldTag(name: String): String = "checkout-field:$name"

internal fun dayTag(index: Int): String = "checkout-day:$index"

internal fun slotTag(id: String): String = "checkout-slot:$id"

internal fun pointTag(id: String): String = "checkout-point:$id"

internal fun paymentTag(id: String): String = "checkout-payment:$id"

internal fun summaryItemTag(index: Int): String = "checkout-item:$index"

internal fun stepTag(index: Int): String = "checkout-step:$index"

internal const val PLACE_TAG: String = "checkout-place"
internal const val BACK_TO_CART_TAG: String = "checkout-back-to-cart"
internal const val POINTS_TAG: String = "checkout-points"
