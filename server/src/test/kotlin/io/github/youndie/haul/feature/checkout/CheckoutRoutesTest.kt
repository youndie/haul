package io.github.youndie.haul.feature.checkout

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.FieldError
import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.cart.CartPaths
import io.github.youndie.haul.feature.cart.LineChange
import io.github.youndie.haul.feature.checkout.data.AddressesTable
import io.github.youndie.haul.feature.checkout.data.DeliverySlotsTable
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.identity.GUEST_HEADER
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.CatalogSeed
import io.github.youndie.haul.seed.SampleCatalog.DUVET_COVER
import io.github.youndie.haul.seed.SampleCatalog.SONY_HEADPHONES
import io.github.youndie.haul.seed.SampleCatalog.STONEWARE_MUG
import io.github.youndie.haul.seed.SampleCheckout
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.testing.Ledger
import io.github.youndie.haul.testing.SeededDatabase
import io.github.youndie.haul.testing.ShildikHarness
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CheckoutAddress
import io.github.youndie.haul.ui.CheckoutBody
import io.github.youndie.haul.ui.CheckoutNotice
import io.github.youndie.haul.ui.CheckoutSummary
import io.github.youndie.haul.ui.DeliveryMethods
import io.github.youndie.haul.ui.DeliverySlots
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.haul.ui.PaymentMethods
import io.github.youndie.haul.ui.PickupPoints
import io.github.youndie.haul.ui.SummaryRow
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * feature-checkout's quote over HTTP, against a running shildik ([ShildikHarness]) and the seeded
 * catalog in PostgreSQL, at the canvas's «now» (Tue 2025-10-07 19:47, so the windows run Wed 8 … Sun
 * 12). Every test signs in a person of its own — Maya herself where the canvas draws her — and a test
 * that fills a window works over a database of its own, because windows are shared by every customer.
 */
class CheckoutRoutesTest {
    private val headphones = "$SONY_HEADPHONES-0"
    private val duvet = "$DUVET_COVER-0"
    private val mug = "$STONEWARE_MUG-0"

    private fun signedIn(
        name: String,
        id: String? = null,
        dataSource: DataSource = SeededDatabase.dataSource,
        block: suspend HttpClient.(token: String) -> Unit,
    ) {
        val sub = if (id == null) ShildikHarness.person(name) else ShildikHarness.person(name, id)
        val token = ShildikHarness.accessToken(sub)
        haulTest(dataSource, signIn = ShildikHarness.signIn) { block(token) }
    }

    private suspend fun HttpClient.checkout(token: String): KompotComponent {
        val response = get(CheckoutPaths.SCREEN) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return haulWireJson.decodeKompotComponent(response.bodyAsText())
    }

    private suspend fun HttpClient.putLine(
        token: String,
        skuId: String,
        change: LineChange,
    ) = put(CartPaths.line(skuId)) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(haulWireJson.encodeToString(LineChange.serializer(), change))
    }.assertRefresh()

    /** Maya's three lines (research §6), in a cart of the caller's own. */
    private suspend fun HttpClient.mayasLines(token: String) =
        listOf(headphones, duvet, mug).forEach { putLine(token, it, LineChange(quantity = 1)) }

    private suspend fun HttpClient.choose(
        token: String,
        choice: CheckoutChoice,
    ): HttpResponse =
        put(CheckoutPaths.CHOICE) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(CheckoutChoice.serializer(), choice))
        }

    private suspend fun HttpClient.saveAddress(
        token: String,
        entry: AddressEntry,
    ): HttpResponse =
        post(CheckoutPaths.ADDRESSES) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(haulWireJson.encodeToString(AddressEntry.serializer(), entry))
        }

    /** Every place in [slotId]'s window taken, as twenty orders would leave it. */
    private fun DataSource.fill(slotId: String) {
        val (day, hour) = slotId.split('T')
        transaction(Databases.connect(this)) {
            DeliverySlotsTable.upsert {
                it[DeliverySlotsTable.day] = LocalDate.parse(day)
                it[startHour] = hour.toInt()
                it[capacity] = 20
                it[taken] = 20
            }
        }
    }

    private val ledger = Ledger(SeededDatabase.dataSource)

    /** An address of [customerId]'s saved a day before any the test saves, as a save before B-40 left it; its id. */
    private fun DataSource.olderAddress(
        customerId: String,
        entry: AddressEntry,
    ): String {
        val id = "address-older-$customerId"
        transaction(Databases.connect(this)) {
            AddressesTable.insert {
                it[AddressesTable.id] = id
                it[AddressesTable.customerId] = customerId
                it[street] = entry.street
                it[apt] = entry.apt.ifEmpty { null }
                it[city] = entry.city
                it[zip] = entry.zip
                it[createdAt] = CatalogSeed.NOW.minusDays(1)
            }
        }
        return id
    }

    /**
     * A seeded database of the test's own, its pool closed afterwards: every pool holds its connections,
     * and the suite's PostgreSQL ran out of them («too many clients») while these were left open.
     */
    private fun ownDatabase(block: (DataSource) -> Unit) = seededFreshDatabase().use(block)

    private fun KompotComponent.selectedSlot(): String? =
        only<DeliverySlots>()
            .days
            .flatMap { it.slots }
            .singleOrNull { it.selected }
            ?.id

    private fun KompotComponent.rows(): List<SummaryRow> = only<CheckoutSummary>().rows

    /**
     * Scenario «Quote as drawn» (`Checkout_Content`): Maya's cart, courier to 148 Wythe Avenue 4F, Wed 8
     * 15:00–18:00, card ···· 4821 — the totals are the cart's, $512.00, and the quote can be placed.
     */
    @Test
    fun `Maya's quote is the cart's totals by courier to her address`() =
        ownDatabase { database ->
            signedIn("Maya Kowalski", id = SampleCustomers.MAYA, dataSource = database) { token ->
                val before = checkout(token)
                assertEquals(
                    "9:00 – 12:00",
                    before
                        .only<DeliverySlots>()
                        .days
                        .first()
                        .slots
                        .first { it.selected }
                        .label,
                )

                choose(token, CheckoutChoice(slotId = "2025-10-08T15")).assertRefresh()

                val tree = checkout(token)
                assertEquals("Checkout", tree.only<CheckoutBody>().title)
                assertEquals(
                    listOf(
                        listOf(DeliveryMethod.Courier, "Tomorrow, Oct 8", "Free", true),
                        listOf(DeliveryMethod.PickupPoint, "Thu, Oct 9 · 240 m away", "Free", false),
                        listOf(DeliveryMethod.ParcelLocker, "Thu, Oct 9 · 24/7 access", "Free", false),
                    ),
                    tree.only<DeliveryMethods>().options.map { listOf(it.method, it.detail, it.price, it.selected) },
                )
                val address = tree.only<CheckoutAddress>()
                assertEquals(
                    listOf("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211", "", ""),
                    address.form.map { it.value },
                    "the form holds the address the checkout delivers to",
                )
                assertTrue(address.form.all { it.error == null })
                val slots = tree.only<DeliverySlots>()
                assertEquals(listOf("Wed", "Thu", "Fri", "Sat", "Sun"), slots.days.map { it.weekday })
                assertEquals(listOf("8", "9", "10", "11", "12"), slots.days.map { it.date })
                assertEquals(listOf(true, false, false, false, false), slots.days.map { it.selected })
                assertEquals(
                    listOf("9:00 – 12:00", "12:00 – 15:00", "15:00 – 18:00", "18:00 – 21:00"),
                    slots.days
                        .first()
                        .slots
                        .map { it.label },
                )
                assertEquals("2025-10-08T15", tree.selectedSlot())
                val payment = tree.only<PaymentMethods>()
                assertEquals(
                    listOf("card-4821", "haul_pay", "pay_on_delivery"),
                    payment.options.map { it.id },
                    "the test card ···· 0002 is not listed",
                )
                assertEquals("card-4821", payment.options.single { it.selected }.id)
                assertEquals("4 payments of $128", payment.options.single { it.id == "haul_pay" }.detail)
                assertEquals(null, payment.points, "points are B-23's: no balance is stored")

                val summary = tree.only<CheckoutSummary>()
                assertEquals(
                    listOf(
                        SummaryRow("Items (3)", "$652.00"),
                        SummaryRow("Discount", "−$140.00", saving = true),
                        SummaryRow("Delivery · Wed, Oct 8", "Free"),
                    ),
                    summary.rows,
                )
                assertEquals("$512", summary.total)
                assertEquals("Place order · $512.00", summary.placeLabel)
                assertEquals(
                    "By placing the order you agree to the Terms of Sale. Your card is charged when the order ships.",
                    summary.note,
                )
                assertEquals(3, summary.items.size)
                assertTrue(summary.placeEnabled, "a complete quote cannot be placed")
                assertEquals(null, summary.placeHint)
                assertNotEquals(
                    before.only<CheckoutSummary>().quote,
                    summary.quote,
                    "the quote's fingerprint did not change with the window",
                )
            }
        }

    /**
     * «Checkout takes the selected lines only» (feature-cart, B-11's finding for B-14): a line unticked
     * in the cart is not quoted, and the quote's totals are the cart's own for the same selection.
     */
    @Test
    fun `checkout quotes only the lines selected in the cart`() =
        signedIn("Sam Ortiz") { token ->
            mayasLines(token)
            putLine(token, duvet, LineChange(selected = false))

            val summary = checkout(token).only<CheckoutSummary>()
            val cart = get(CartPaths.SCREEN) { bearerAuth(token) }.bodyAsText()
            val cartSummary = haulWireJson.decodeKompotComponent(cart).only<OrderSummary>()

            assertEquals(2, summary.items.size)
            assertTrue(summary.items.none { it.title.startsWith("Linen") }, "the unticked duvet was quoted")
            assertEquals(cartSummary.total, summary.total)
            // The same rows; checkout names the delivery day in its delivery row (Checkout_Content).
            assertEquals(cartSummary.rows.dropLast(1), summary.rows.dropLast(1))
            assertEquals(cartSummary.rows.last().value, summary.rows.last().value)
            assertEquals("Delivery · Wed, Oct 8", summary.rows.last().label)

            listOf(headphones, mug).forEach { putLine(token, it, LineChange(selected = false)) }
            get(CheckoutPaths.SCREEN) { bearerAuth(token) }.assertError(HttpStatusCode.Conflict, ErrorCode.CartEmpty)
            choose(token, CheckoutChoice(method = DeliveryMethod.PickupPoint))
                .assertError(HttpStatusCode.Conflict, ErrorCode.CartEmpty)
        }

    /** «Shown when: signed in; a guest is sent to sign-in» (screen-checkout): a guest's id is not a customer. */
    @Test
    fun `checkout needs a sign-in`() =
        signedIn("Sam Ortiz") { _ ->
            val guest = guest()
            get(CheckoutPaths.SCREEN).assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            get(CheckoutPaths.SCREEN) { header(GUEST_HEADER, guest) }
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            put(CheckoutPaths.CHOICE) { header(GUEST_HEADER, guest) }
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
            post(CheckoutPaths.ADDRESSES) { header(GUEST_HEADER, guest) }
                .assertError(HttpStatusCode.Unauthorized, ErrorCode.Unauthenticated)
        }

    /**
     * «A slot at capacity is shown and not selectable» (feature-checkout): the full window is drawn
     * unavailable, the default skips it, and choosing it anyway is `409 slot_unavailable`. A window
     * checkout does not offer is `404 slot_not_found`.
     */
    @Test
    fun `a full window is drawn unavailable and refused`() =
        ownDatabase { database ->
            database.fill("2025-10-08T09")
            database.fill("2025-10-08T15")
            signedIn("Sam Ortiz", dataSource = database) { token ->
                mayasLines(token)
                val tree = checkout(token)
                val wednesday =
                    tree
                        .only<DeliverySlots>()
                        .days
                        .first()
                        .slots
                assertEquals(listOf(false, true, false, true), wednesday.map { it.available })
                assertEquals("2025-10-08T12", tree.selectedSlot(), "the default is the first window with room")

                choose(token, CheckoutChoice(slotId = "2025-10-08T15"))
                    .assertError(HttpStatusCode.Conflict, ErrorCode.SlotUnavailable)
                assertEquals("2025-10-08T12", checkout(token).selectedSlot(), "a refused window was stored")

                choose(token, CheckoutChoice(slotId = "2025-10-13T09"))
                    .assertError(HttpStatusCode.NotFound, ErrorCode.SlotNotFound)
                choose(token, CheckoutChoice(slotId = "2025-10-07T18"))
                    .assertError(HttpStatusCode.NotFound, ErrorCode.SlotNotFound)
                choose(token, CheckoutChoice(slotId = "2025-10-08T10"))
                    .assertError(HttpStatusCode.NotFound, ErrorCode.SlotNotFound)
                choose(token, CheckoutChoice(slotId = "2025-10-09T12")).assertRefresh()
                assertEquals("2025-10-09T12", checkout(token).selectedSlot())
            }
        }

    /**
     * The quote's half of «Slot filled meanwhile» (`Checkout_PlaceError`): a window that fills after it
     * was chosen is cleared — not swapped for another behind the shopper's back — the shopper is told,
     * and the quote cannot be placed until they pick another.
     */
    @Test
    fun `a window that filled after it was chosen is cleared and the shopper told`() =
        ownDatabase { database ->
            signedIn("Sam Ortiz", dataSource = database) { token ->
                mayasLines(token)
                saveAddress(token, AddressEntry("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211")).assertRefresh()
                choose(token, CheckoutChoice(slotId = "2025-10-09T12")).assertRefresh()
                assertTrue(checkout(token).only<CheckoutSummary>().placeEnabled)

                database.fill("2025-10-09T12")

                val tree = checkout(token)
                assertEquals(CheckoutError.SLOT_FILLED, tree.only<CheckoutNotice>().text)
                assertEquals(null, tree.selectedSlot())
                val slots = tree.only<DeliverySlots>()
                assertEquals("Pick another window for Thu, Oct 9", slots.notice)
                assertEquals("Thu", slots.days.single { it.selected }.weekday, "the day shown is the one that filled")
                assertEquals(
                    "12:00 – 15:00 · Full",
                    slots.days
                        .flatMap { it.slots }
                        .single { it.id == "2025-10-09T12" }
                        .label,
                )
                val summary = tree.only<CheckoutSummary>()
                assertFalse(summary.placeEnabled, "a quote with no window can be placed")
                assertEquals("Pick a delivery window", summary.placeHint)
                assertEquals("Delivery · Thu, Oct 9", summary.rows.last().label)

                choose(token, CheckoutChoice(slotId = "2025-10-09T15")).assertRefresh()
                val again = checkout(token)
                assertTrue(again.all().none { it is CheckoutNotice }, "the notice outlived the new window")
                assertTrue(again.only<CheckoutSummary>().placeEnabled)
            }
        }

    /**
     * `Checkout_PickupPoint`: the three points near Wythe Avenue with distance, hours and day, the nearest
     * chosen, no address and no window — a pickup needs neither — and pay on delivery still offered.
     */
    @Test
    fun `a pickup point needs no address and no window`() =
        signedIn("Sam Ortiz") { token ->
            mayasLines(token)
            choose(token, CheckoutChoice(method = DeliveryMethod.PickupPoint)).assertRefresh()

            val tree = checkout(token)
            val points = tree.only<PickupPoints>().points
            assertEquals(
                listOf(
                    listOf("214 Bedford Ave", "240 m", "Pickup point · open until 21:00 · Thu, Oct 9", true),
                    listOf("96 N 6th St", "650 m", "Pickup point · open until 22:00 · Thu, Oct 9", false),
                    listOf("315 Grand St", "900 m", "Pickup point · open until 20:00 · Thu, Oct 9", false),
                ),
                points.map { listOf(it.name, it.distance, it.detail, it.selected) },
            )
            assertEquals("Pickup point", tree.only<PickupPoints>().title)
            assertEquals(
                "Delivery · Thu, Oct 9",
                tree
                    .only<CheckoutSummary>()
                    .rows
                    .last()
                    .label,
            )
            assertTrue(
                tree.all().none { it is DeliverySlots || it is CheckoutAddress },
                "a pickup drew courier sections",
            )
            assertTrue(tree.only<CheckoutSummary>().placeEnabled, "a pickup without an address cannot be placed")
            assertTrue("pay_on_delivery" in tree.only<PaymentMethods>().options.map { it.id })

            choose(token, CheckoutChoice(pointId = SampleCheckout.NORTH_6TH)).assertRefresh()
            assertEquals(
                "96 N 6th St",
                checkout(token)
                    .only<PickupPoints>()
                    .points
                    .single { it.selected }
                    .name,
            )
            choose(token, CheckoutChoice(pointId = "nowhere"))
                .assertError(HttpStatusCode.NotFound, ErrorCode.PickupPointNotFound)
        }

    /**
     * `Checkout_ParcelLocker` and «pay on delivery is not offered for parcel lockers»: the lockers are
     * listed, pay on delivery is not, choosing it is `422 payment_method_not_allowed`, and a shopper
     * who had chosen it by courier is moved back to the card.
     */
    @Test
    fun `a parcel locker is not paid on delivery`() =
        signedIn("Sam Ortiz") { token ->
            mayasLines(token)
            choose(token, CheckoutChoice(payment = "pay_on_delivery")).assertRefresh()
            assertEquals(
                "pay_on_delivery",
                checkout(token)
                    .only<PaymentMethods>()
                    .options
                    .single { it.selected }
                    .id,
            )

            choose(token, CheckoutChoice(method = DeliveryMethod.ParcelLocker)).assertRefresh()

            val tree = checkout(token)
            assertEquals(
                listOf(
                    listOf("Wythe & N 7th", "180 m", "Parcel locker · 24/7 · Thu, Oct 9", true),
                    listOf("Bedford Ave station", "700 m", "Parcel locker · 24/7 · Thu, Oct 9", false),
                ),
                tree.only<PickupPoints>().points.map { listOf(it.name, it.distance, it.detail, it.selected) },
            )
            val payment = tree.only<PaymentMethods>()
            assertEquals(listOf("card-4821", "haul_pay"), payment.options.map { it.id })
            assertEquals("card-4821", payment.options.single { it.selected }.id)
            choose(token, CheckoutChoice(payment = "pay_on_delivery"))
                .assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PaymentMethodNotAllowed)
        }

    /** «Haul Pay is offered for totals between $50 and $2,000» (feature-checkout): a $24 order is not. */
    @Test
    fun `Haul Pay is refused for a total under fifty dollars`() =
        signedIn("Sam Ortiz") { token ->
            putLine(token, mug, LineChange(quantity = 1))
            assertTrue("haul_pay" !in checkout(token).only<PaymentMethods>().options.map { it.id })
            choose(token, CheckoutChoice(payment = "haul_pay"))
                .assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PaymentMethodNotAllowed)
            choose(token, CheckoutChoice(payment = "bitcoin"))
                .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            choose(token, CheckoutChoice()).assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
        }

    /**
     * `Checkout_Validation`: a customer with no address sees the form empty; one sent with the street and
     * the ZIP empty is `400 validation_failed` naming both fields, and the tree draws it again with what
     * was typed, an error under each, and the button held with what to fill in; once an address is saved
     * the form holds it and the quote can be placed.
     */
    @Test
    fun `the address form is refused field by field and drawn again`() =
        signedIn("Sam Ortiz") { token ->
            mayasLines(token)
            val empty = checkout(token)
            assertTrue(empty.only<CheckoutAddress>().form.all { it.value.isEmpty() }, "a form without an address")
            val unplaced = empty.only<CheckoutSummary>()
            assertFalse(unplaced.placeEnabled, "a courier quote with no address can be placed")
            assertEquals("Fill in the delivery address", unplaced.placeHint)

            val refused =
                saveAddress(token, AddressEntry(street = " ", apt = "4F", city = "Brooklyn, NY"))
                    .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
            assertEquals(
                listOf(
                    FieldError("street", ErrorCode.FieldRequired, "Enter the street address"),
                    FieldError("zip", ErrorCode.FieldRequired, "Enter a 5-digit ZIP"),
                ),
                refused.fields,
            )
            assertEquals("street", refused.field)

            val drawn = checkout(token)
            assertEquals(
                mapOf(
                    "street" to ("" to "Enter the street address"),
                    "apt" to ("4F" to null),
                    "city" to ("Brooklyn, NY" to null),
                    "zip" to ("" to "Enter a 5-digit ZIP"),
                ),
                drawn
                    .only<CheckoutAddress>()
                    .form
                    .filter {
                        it.name in
                            setOf(
                                "street",
                                "apt",
                                "city",
                                "zip",
                            )
                    }.associate {
                        it.name to (it.value.trim() to it.error)
                    },
            )
            assertEquals("Fill in the street address and ZIP", drawn.only<CheckoutSummary>().placeHint)

            saveAddress(token, AddressEntry("148 Wythe Avenue", "4F", "Brooklyn, NY", "112ll"))
                .assertError(HttpStatusCode.BadRequest, ErrorCode.ValidationFailed)
                .also {
                    assertEquals(
                        listOf(FieldError("zip", ErrorCode.FieldInvalid, "Enter a 5-digit ZIP")),
                        it.fields,
                    )
                }
            assertEquals("Fill in the ZIP", checkout(token).only<CheckoutSummary>().placeHint)

            saveAddress(token, AddressEntry(" 148 Wythe Avenue ", "4F", "Brooklyn, NY", "11211")).assertRefresh()

            val saved = checkout(token)
            val address = saved.only<CheckoutAddress>()
            assertEquals(
                listOf("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211", "", ""),
                address.form.map { it.value },
                "the form does not hold the address saved",
            )
            assertTrue(address.form.all { it.error == null }, "the refused form outlived the save")
            assertTrue(saved.only<CheckoutSummary>().placeEnabled)
            assertEquals(null, saved.only<CheckoutSummary>().placeHint)
        }

    /**
     * B-40, scenario «Editing the address»: the form holds the address delivered to, and a save edits
     * that one in place. Every save used to add a row, so «4F» changed to «5B» left «4F» stored and never
     * shown again. The customer's addresses are read from the table after both saves: one, under the id
     * the first save gave it, at «5B». Its id stays, so the quote's fingerprint must change with its
     * fields — a page still drawn with «4F» is not the quote placement would place.
     */
    @Test
    fun `saving the address form twice edits the one address in place`() {
        val customer = ShildikHarness.person("Lena Novak")
        signedIn("Lena Novak", id = customer) { token ->
            mayasLines(token)
            saveAddress(token, AddressEntry("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211")).assertRefresh()
            val first = ledger.addresses(customer)
            assertEquals(listOf("4F"), first.map { it.second })
            val before = checkout(token).only<CheckoutSummary>().quote

            saveAddress(token, AddressEntry("148 Wythe Avenue", "5B", "Brooklyn, NY", "11211")).assertRefresh()

            assertEquals(
                listOf(first.single().first to "5B"),
                ledger.addresses(customer),
                "the second save did not edit the first address in place",
            )
            val after = checkout(token)
            assertEquals(
                listOf("148 Wythe Avenue", "5B", "Brooklyn, NY", "11211", "", ""),
                after.only<CheckoutAddress>().form.map { it.value },
            )
            assertNotEquals(
                before,
                after.only<CheckoutSummary>().quote,
                "the address was edited and the quote's fingerprint stayed the same",
            )
        }
    }

    /**
     * B-40: an address equal to one the customer already has is not stored twice — saving it chooses
     * that one and writes no address. A customer who used the form before B-40 has a row per save; edited
     * in place, the chosen row would have become a second copy of the older one. Leading and trailing
     * blanks do not count, as everywhere in the form.
     */
    @Test
    fun `an address equal to a saved one is chosen and not stored again`() {
        val customer = ShildikHarness.person("Omar Haddad")
        signedIn("Omar Haddad", id = customer) { token ->
            mayasLines(token)
            saveAddress(token, AddressEntry("148 Wythe Avenue", "4F", "Brooklyn, NY", "11211")).assertRefresh()
            val wythe = ledger.addresses(customer).single().first
            val bond =
                SeededDatabase.dataSource.olderAddress(
                    customer,
                    AddressEntry("31 Bond Street", "", "New York, NY", "10012"),
                )

            saveAddress(token, AddressEntry(" 31 Bond Street ", "", "New York, NY", "10012")).assertRefresh()
            saveAddress(token, AddressEntry("31 Bond Street", "", "New York, NY", "10012")).assertRefresh()

            assertEquals(
                listOf(wythe to "4F", bond to null),
                ledger.addresses(customer),
                "an address equal to a saved one was stored again or written over another",
            )
            assertEquals(
                listOf("31 Bond Street", "", "New York, NY", "10012", "", ""),
                checkout(token).only<CheckoutAddress>().form.map { it.value },
                "the saved address equal to the form is not the one delivered to",
            )
        }
    }

    /** «Not yours» is «does not exist» (research §5): Maya's seeded address is no one else's to choose. */
    @Test
    fun `another customer's address is not found`() =
        signedIn("Sam Ortiz") { token ->
            mayasLines(token)
            choose(token, CheckoutChoice(addressId = SampleCheckout.MAYA_ADDRESS))
                .assertError(HttpStatusCode.NotFound, ErrorCode.AddressNotFound)
        }
}
