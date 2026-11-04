package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.CatalogRepository

/** Striff negative control: the same probe as the positive control, placed where research D3a puts it. */
internal class CatalogProbe(private val catalog: CatalogRepository) {
    override fun toString(): String = "CatalogProbe($catalog)"
}
