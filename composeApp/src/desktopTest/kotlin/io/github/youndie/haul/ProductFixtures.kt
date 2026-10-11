package io.github.youndie.haul

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.haul.feature.product.DialogOverlay
import io.github.youndie.haul.feature.product.ProductNotFound
import io.github.youndie.haul.feature.product.QuestionFormView
import io.github.youndie.haul.feature.product.ReviewFormView
import io.github.youndie.haul.feature.product.ShareFallback
import io.github.youndie.haul.feature.reviews.QuestionEntry
import io.github.youndie.haul.feature.reviews.ReviewEntry
import io.github.youndie.haul.shell.ErrorShell
import io.github.youndie.haul.shell.ProductLoading
import io.github.youndie.haul.shell.ShellFailure
import io.github.youndie.haul.ui.HaulHeader
import io.github.youndie.haul.ui.Highlight
import io.github.youndie.haul.ui.ProductDescription
import io.github.youndie.haul.ui.QuestionForm
import io.github.youndie.haul.ui.ReviewForm
import io.github.youndie.haul.ui.toneColor
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Product screen (B-08, and the two dialogs B-22), at the artboard's
// size. The tabs are wire bodies with the canvas's copy (`resources/bodies/product_*.json`) drawn
// through the app's registry — the reviews and questions tabs and the dialogs in them held equal to the
// server's trees by `ReviewFixturesTest` — Loading, Error and NotFound are the client's own. A dialog is
// its tab's page with the form its `present` carries drawn over it, holding what the canvas has typed.

@ViddikScreenshot(name = "Loading", group = "Product", width = 1440, height = 835)
@Composable
internal fun ProductLoading() = Fixture(compact = false) { Page { ProductLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Product", width = 390, height = 1547)
@Composable
internal fun ProductLoadingPhone() = Fixture(compact = true) { Page { ProductLoading() } }

@ViddikScreenshot(name = "Description", group = "Product", width = 1440, height = 1494)
@Composable
internal fun ProductDescription() = Fixture(compact = false) { Page { Body("product_description.json") } }

@ViddikScreenshot(name = "Description_Phone", group = "Product", width = 390, height = 2720)
@Composable
internal fun ProductDescriptionPhone() = Fixture(compact = true) { Page { Body("product_description.json") } }

/** The product at 1024 (B-82): the buy box under the title, beside the photo; the facts under the copy. */
@ViddikScreenshot(name = "Description_1024", group = "Product", width = 1024, height = 2365)
@Composable
internal fun ProductDescriptionMedium() =
    Fixture(compact = false, medium = true) {
        Page { Body("product_description.json") }
    }

/**
 * Product_Description with the tiles the server sends for the seeded headphones — the first four
 * specifications, «Noise cancelling» and «Connectivity» among them — wrapped and shrunk to fit their tiles
 * rather than cut (B-104). No artboard draws them.
 */
@ViddikScreenshot(name = "Description_SpecFacts", group = "Product", width = 1440, height = 1560)
@Composable
internal fun ProductDescriptionSpecFacts() = Fixture(compact = false) { Page { Render(remember { specFacts() }) } }

@ViddikScreenshot(name = "Description_SpecFacts_Phone", group = "Product", width = 390, height = 2720)
@Composable
internal fun ProductDescriptionSpecFactsPhone() = Fixture(compact = true) { Page { Render(remember { specFacts() }) } }

/**
 * The share button's answer when the browser keeps the link off the clipboard (B-104): what it says and the
 * whole link to copy by hand, over the corner of the photo it opens on. No artboard draws it.
 */
@ViddikScreenshot(name = "ShareRefused", group = "Product", width = 390, height = 180)
@Composable
internal fun ProductShareRefused() =
    Fixture(compact = true) {
        Box(Modifier.fillMaxSize().background(toneColor("#E6E4FF"))) {
            ShareFallback(
                "https://haul.kotlin.website/p/p-sony-wh-1000xm6?sku=p-sony-wh-1000xm6-0",
                Modifier.align(Alignment.TopEnd).padding(14.dp),
            ) {}
        }
    }

/** Product_Description's page with the seeded headphones' first four specifications as the tiles. */
private fun specFacts(): KompotComponent {
    val page = decode("product_description.json") as ColumnComponent
    val facts =
        listOf(
            Highlight("Battery", "Up to 40 hours, 3 min charge = 3 h"),
            Highlight("Noise cancelling", "Adaptive, 12 microphones"),
            Highlight("Connectivity", "Bluetooth 5.4 · LDAC · multipoint"),
            Highlight("Weight", "250 g"),
        )
    return page.copy(children = page.children.map { if (it is ProductDescription) it.copy(facts = facts) else it })
}

@ViddikScreenshot(name = "Specifications", group = "Product", width = 1440, height = 1624)
@Composable
internal fun ProductSpecifications() = Fixture(compact = false) { Page { Body("product_specifications.json") } }

@ViddikScreenshot(name = "Specifications_Phone", group = "Product", width = 390, height = 2869)
@Composable
internal fun ProductSpecificationsPhone() = Fixture(compact = true) { Page { Body("product_specifications.json") } }

@ViddikScreenshot(name = "Reviews", group = "Product", width = 1440, height = 1572)
@Composable
internal fun ProductReviews() = Fixture(compact = false) { Page { Body("product_reviews.json") } }

@ViddikScreenshot(name = "Reviews_Phone", group = "Product", width = 390, height = 3067)
@Composable
internal fun ProductReviewsPhone() = Fixture(compact = true) { Page { Body("product_reviews.json") } }

/**
 * The reviews tab once Maya has reviewed the headphones and found Aisha's review helpful (B-99): her review
 * leads, the button's place says when she wrote it, and Aisha's «Helpful» is drawn voted. No artboard draws it.
 */
@ViddikScreenshot(name = "Reviews_Mine", group = "Product", width = 1440, height = 1960)
@Composable
internal fun ProductReviewsMine() = Fixture(compact = false) { Page { Body("product_reviews_mine.json") } }

@ViddikScreenshot(name = "Questions", group = "Product", width = 1440, height = 1723)
@Composable
internal fun ProductQuestions() = Fixture(compact = false) { Page { Body("product_questions.json") } }

@ViddikScreenshot(name = "Questions_Phone", group = "Product", width = 390, height = 2930)
@Composable
internal fun ProductQuestionsPhone() = Fixture(compact = true) { Page { Body("product_questions.json") } }

@ViddikScreenshot(name = "ReviewDialog", group = "Product", width = 1440, height = 1572)
@Composable
internal fun ProductReviewDialog() = Fixture(compact = false) { ReviewDialog() }

@ViddikScreenshot(name = "ReviewDialog_Phone", group = "Product", width = 390, height = 3067)
@Composable
internal fun ProductReviewDialogPhone() = Fixture(compact = true) { ReviewDialog() }

@ViddikScreenshot(name = "QuestionDialog", group = "Product", width = 1440, height = 1723)
@Composable
internal fun ProductQuestionDialog() = Fixture(compact = false) { QuestionDialog() }

@ViddikScreenshot(name = "QuestionDialog_Phone", group = "Product", width = 390, height = 2930)
@Composable
internal fun ProductQuestionDialogPhone() = Fixture(compact = true) { QuestionDialog() }

@ViddikScreenshot(name = "OutOfStock", group = "Product", width = 1440, height = 1494)
@Composable
internal fun ProductOutOfStock() = Fixture(compact = false) { Page { Body("product_out_of_stock.json") } }

@ViddikScreenshot(name = "OutOfStock_Phone", group = "Product", width = 390, height = 2680)
@Composable
internal fun ProductOutOfStockPhone() = Fixture(compact = true) { Page { Body("product_out_of_stock.json") } }

@ViddikScreenshot(name = "NotFound", group = "Product", width = 1440, height = 799)
@Composable
internal fun ProductNotFound() = Fixture(compact = false) { Page { NotFound() } }

@ViddikScreenshot(name = "NotFound_Phone", group = "Product", width = 390, height = 653)
@Composable
internal fun ProductNotFoundPhone() = Fixture(compact = true) { Page { NotFound() } }

@ViddikScreenshot(name = "Error", group = "Product", width = 1440, height = 900)
@Composable
internal fun ProductError() = Fixture(compact = false) { Page { ErrorShell("This product", ShellFailure.Server) } }

@ViddikScreenshot(name = "Error_Phone", group = "Product", width = 390, height = 641)
@Composable
internal fun ProductErrorPhone() = Fixture(compact = true) { Page { ErrorShell("This product", ShellFailure.Server) } }

/** `404 product_not_found`, drawn with the header the client last had: the product page's. */
@Composable
private fun NotFound() {
    val header =
        remember {
            (decode("product_description.json") as ColumnComponent).children.filterIsInstance<HaulHeader>().single()
        }
    ProductNotFound(header)
}

/** Product_ReviewDialog: five stars, «Worth it for the commute» and the canvas's review, over the reviews tab. */
@Composable
private fun ReviewDialog() {
    val form =
        remember {
            presented<io.github.youndie.haul.ui.ProductReviews, ReviewForm>(
                "product_reviews.json",
            ) { it.action }
        }
    Page { Body("product_reviews.json") }
    DialogOverlay(onDismiss = null) {
        ReviewFormView(
            form,
            ReviewEntry(
                rating = 5,
                title = "Worth it for the commute",
                body =
                    "Noise cancelling handles the subway and the open office. Comfortable for a full workday, " +
                        "and the battery lasts all week.",
            ),
        )
    }
}

/** Product_QuestionDialog: the canvas's question, over the questions tab. */
@Composable
private fun QuestionDialog() {
    val form =
        remember {
            presented<io.github.youndie.haul.ui.ProductQuestions, QuestionForm>(
                "product_questions.json",
            ) { it.action }
        }
    Page { Body("product_questions.json") }
    DialogOverlay(onDismiss = null) {
        QuestionFormView(form, QuestionEntry("Do the ear cushions come off for cleaning?"))
    }
}

/** The [F] that the [T] section of [body] presents. */
private inline fun <reified T : KompotComponent, reified F : KompotComponent> presented(
    body: String,
    action: (T) -> KompotAction?,
): F = (action((decode(body) as ColumnComponent).children.filterIsInstance<T>().single()) as PresentAction).content as F
