package io.github.youndie.haul.feature.cart

import io.github.youndie.haul.ErrorCode
import io.github.youndie.haul.seed.SampleCatalog
import io.github.youndie.haul.testing.acknowledge
import io.github.youndie.haul.testing.all
import io.github.youndie.haul.testing.applyPromo
import io.github.youndie.haul.testing.assertError
import io.github.youndie.haul.testing.assertRefresh
import io.github.youndie.haul.testing.cart
import io.github.youndie.haul.testing.guest
import io.github.youndie.haul.testing.haulTest
import io.github.youndie.haul.testing.only
import io.github.youndie.haul.testing.putLine
import io.github.youndie.haul.testing.seededFreshDatabase
import io.github.youndie.haul.ui.CartLine
import io.github.youndie.haul.ui.CartSelection
import io.github.youndie.haul.ui.OrderSummary
import io.github.youndie.kompot.KompotComponent
import io.ktor.http.HttpStatusCode
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `Cart_ItemChanged` (feature-cart): a line whose SKU changed price or went out of stock since it was
 * added is marked, left out of the selection and the totals, and counts again once acknowledged. The
 * catalog changes under the cart here, so each test has a database of its own.
 */
class ChangedLinesTest {
    private val headphones = "${SampleCatalog.SONY_HEADPHONES}-0"
    private val mug = "${SampleCatalog.STONEWARE_MUG}-0"

    private fun DataSource.sql(statement: String) {
        connection.use { c ->
            c.createStatement().use { it.executeUpdate(statement) }
            c.commit()
        }
    }

    private fun KompotComponent.line(skuId: String): CartLine =
        all().filterIsInstance<CartLine>().single {
            it.skuId ==
                skuId
        }

    @Test
    fun `a line whose price changed is marked and left out until acknowledged`() {
        val db = seededFreshDatabase()
        haulTest(db) {
            val guest = guest()
            putLine(guest, headphones, LineChange(quantity = 1)).assertRefresh()
            putLine(guest, mug, LineChange(quantity = 1)).assertRefresh()
            db.sql("UPDATE skus SET price_cents = 2600 WHERE id = '$mug'")

            val changed = cart(guest)
            val line = changed.line(mug)
            assertEquals("Price changed: now $26", line.change)
            assertEquals("OK", line.acknowledgeLabel)
            assertFalse(line.selected, "a changed line is still selected")
            assertFalse(line.selectable)
            assertEquals("$349.00", changed.only<OrderSummary>().total, "the changed line was counted")
            // «Select all» speaks for the lines that can be selected: the headphones are.
            assertEquals(
                CartSelection("selection", allSelected = true, selectedCount = 1),
                changed.only<CartSelection>(),
            )
            // Ticking it does not count it either, until the change is accepted.
            putLine(guest, mug, LineChange(selected = true)).assertRefresh()
            assertFalse(cart(guest).line(mug).selected)

            acknowledge(guest, mug).assertRefresh()
            val accepted = cart(guest)
            assertNull(accepted.line(mug).change)
            assertTrue(accepted.line(mug).selected)
            assertEquals("$375.00", accepted.only<OrderSummary>().total)
        }
    }

    /** «Never above stock», where the stock is below the cap of ten. */
    @Test
    fun `a quantity above the stock is 409 out_of_stock`() {
        val db = seededFreshDatabase()
        haulTest(db) {
            db.sql("UPDATE skus SET stock = 3 WHERE id = '$mug'")
            val guest = guest()
            putLine(guest, mug, LineChange(quantity = 4)).assertError(HttpStatusCode.Conflict, ErrorCode.OutOfStock)
            putLine(guest, mug, LineChange(quantity = 3)).assertRefresh()
            assertEquals(3, cart(guest).line(mug).maxQuantity, "«+» does not stop at the stock")
            putLine(guest, mug, LineChange(quantity = 4)).assertError(HttpStatusCode.Conflict, ErrorCode.OutOfStock)
        }
    }

    @Test
    fun `a line that went out of stock is marked and stays out of the selection`() {
        val db = seededFreshDatabase()
        haulTest(db) {
            val guest = guest()
            putLine(guest, mug, LineChange(quantity = 1)).assertRefresh()
            db.sql("UPDATE skus SET stock = 0 WHERE id = '$mug'")

            val line = cart(guest).line(mug)
            assertEquals("Out of stock", line.change)
            assertEquals("OK", line.acknowledgeLabel)
            assertFalse(line.selected)

            acknowledge(guest, mug).assertRefresh()
            val after = cart(guest)
            // Accepted, but still nothing to buy: marked, without the button, and not counted.
            assertEquals("Out of stock", after.line(mug).change)
            assertNull(after.line(mug).acknowledgeLabel)
            assertFalse(after.line(mug).selected)
            assertFalse(after.only<OrderSummary>().checkoutEnabled)
            // A promo has nothing selected to apply to.
            applyPromo(guest, "AUTUMN10").assertError(HttpStatusCode.UnprocessableEntity, ErrorCode.PromoNotApplicable)
        }
    }
}
