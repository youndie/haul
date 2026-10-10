package io.github.youndie.haul.feature.catalog

import io.github.youndie.haul.feature.catalog.domain.CatalogRepository
import io.github.youndie.haul.feature.catalog.domain.pathOf
import io.github.youndie.haul.shell.Frame
import io.github.youndie.haul.shell.Parts
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.respondRedirect
import org.koin.ktor.ext.inject

/**
 * A category has one address (B-68): the slugs from its top-level category down to it,
 * `/c/electronics/audio/headphones` (`pathOf`). Every link and every address a category page names is that
 * one; any other form of it — the last slug alone, `/c/headphones`, or a path that skips a level — answers
 * `301` to the same address, query kept, wherever the address is asked: the page (`/c/…`), its tree
 * (`/ui/c/…`) and its parts (`/ui/parts/c/…`).
 *
 * Why it matters: the shell takes two addresses for one screen only when their paths are equal (B-62), so a
 * page opened at one form whose filters named the other was loaded whole on the first tick. A form only
 * redirects when its last slug names a category; anything else is left to the route, which answers it as
 * before — the catalog's root, a category that is not there, a path the storefront does not serve.
 */
internal val OneCategoryAddress =
    createApplicationPlugin("OneCategoryAddress") {
        val catalog by application.inject<CatalogRepository>()
        onCall { call ->
            val path = call.request.path()
            val prefix = PREFIXES.firstOrNull { path.startsWith("$it/") } ?: return@onCall
            val asked = path.removePrefix("$prefix/")
            val slugs = asked.split('/')
            if (slugs.any { it.isEmpty() }) return@onCall
            val categories = catalog.categories()
            val category = categories.firstOrNull { it.slug == slugs.last() } ?: return@onCall
            val address = categories.pathOf(category)
            if (address == asked) return@onCall
            val query = call.request.queryString()
            call.respondRedirect("$prefix/$address" + if (query.isEmpty()) "" else "?$query", permanent = true)
        }
    }

/** Where a category's address is asked: the storefront's page, the tree under `/ui`, the parts (B-63). */
private val PREFIXES = listOf(Frame.CATALOG, "/ui${Frame.CATALOG}", "${Parts.PREFIX}${Frame.CATALOG}")
