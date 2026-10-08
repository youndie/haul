package io.github.youndie.haul.feature.catalog.domain

import io.github.youndie.haul.ErrorCode

/** What a catalog route can refuse with; the route maps each to its status once (`CatalogRouting`). */
internal sealed class CatalogError(
    val code: ErrorCode,
    override val message: String,
    val field: String? = null,
) : Exception(message) {
    class CategoryNotFound(
        slug: String,
    ) : CatalogError(ErrorCode.CategoryNotFound, "No category «$slug»")

    class ProductNotFound(
        id: String,
    ) : CatalogError(ErrorCode.ProductNotFound, "No product «$id»")

    class Invalid(
        field: String,
        message: String,
    ) : CatalogError(ErrorCode.ValidationFailed, message, field)
}
