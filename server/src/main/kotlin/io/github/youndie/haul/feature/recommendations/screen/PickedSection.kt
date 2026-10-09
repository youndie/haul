package io.github.youndie.haul.feature.recommendations.screen

import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.catalog.screen.card
import io.github.youndie.haul.feature.recommendations.domain.PickedForYou
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.ProductGrid
import io.github.youndie.haul.ui.SectionHeader
import io.github.youndie.kompot.KompotComponent
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * The home page's «Picked for you» (feature-recommendations, `Home_Content`): the title, the subtitle that
 * says what the picks are made from, and a row of cards as the deals' are drawn. A guest gets nothing, and
 * nothing is read for them.
 *
 * The block is the page's extra, not its point: a failure inside it drops the block and is logged, and the
 * home page answers without it (endpoint-recommendations left the choice to B-25). A database that cannot be
 * reached still fails the page, through the reads every home page makes.
 */
internal class PickedSection(
    private val pickedForYou: PickedForYou,
    private val calendar: DeliveryCalendar,
    private val photos: ProductPhotos,
) {
    suspend fun build(viewer: Viewer): List<KompotComponent> {
        val customerId = viewer.customerId ?: return emptyList()
        val picks =
            try {
                pickedForYou(customerId, viewer.prices)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("«Picked for you» left out of the home page", e)
                return emptyList()
            }
        if (picks.items.isEmpty()) return emptyList()
        return listOf(
            SectionHeader(TITLE_ID, "Picked for you", subtitle = picks.basis.subtitle, accent = "you"),
            ProductGrid(GRID_ID, picks.items.map { card(it, calendar, photos, viewer) }, columns = PickedForYou.TOTAL),
        )
    }

    companion object {
        const val TITLE_ID = "picked-title"
        const val GRID_ID = "picked"
        private val log = LoggerFactory.getLogger(PickedSection::class.java)
    }
}
