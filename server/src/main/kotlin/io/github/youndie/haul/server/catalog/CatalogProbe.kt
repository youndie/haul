package io.github.youndie.haul.server.catalog

import io.github.youndie.haul.feature.catalog.domain.CatalogRepository

/** Striff positive control: a module-specific root (`…haul.server`), which research D3a rules out. */
internal class CatalogProbe(private val catalog: CatalogRepository) {
    override fun toString(): String = "CatalogProbe($catalog)"
}
