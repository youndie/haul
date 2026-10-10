package io.github.youndie.haul.feature.product

import androidx.compose.runtime.Composable
import io.github.youndie.haul.shell.NotFoundShell
import io.github.youndie.haul.ui.HaulHeader

/**
 * What the client draws for `404 product_not_found` (Product_NotFound): the answer carries no tree, so
 * the copy is the client's and the header is the one it last drew, or the shell's own on arrival (B-67).
 */
@Composable
public fun ProductNotFound(
    header: HaulHeader,
    onHome: () -> Unit = {},
) {
    NotFoundShell(
        header = header,
        eyebrow = "Product",
        title = "This product is no longer available",
        accent = "available",
        text = "It may have sold out for good or been removed by the seller.",
        actionLabel = "Go to the home page",
        onAction = onHome,
    )
}
