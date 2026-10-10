---
id: B-68
title: "server + client: the first filter on a category does not reload the page"
status: done
priority: P1
size: S
stage: stage-10-review
---

# B-68 — server + client: the first filter on a category does not reload the page

Seen on the stand: on `/c/electronics/headphones`, ticking «Sony» sent `GET /ui/parts/c/headphones?brand=Sony` and
then `GET /ui/c/headphones?brand=Sony` — the whole page right after its parts. The `update`'s `deeplink` names the
category by its last segment (`/c/headphones`), the page was opened at the full path (`/c/electronics/headphones`);
the shell keys the screen by path (B-62), so the recorded address counts as another screen and is loaded. The second
tick (path already `/c/headphones`) sent only the parts. The address bar also changes shape under the shopper.

- **Decided as product owner:** a category has one address. Pick one form (the full path, as the breadcrumbs and
  the category links use), make every link, `update.deeplink` and `load` URL use it, and answer the other form with
  a redirect to it.
- AC: a facet tick from a category opened by any link sends exactly one request (the parts); the address keeps its
  form; the old form redirects; a server test over every category link and every parts deeplink.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/shell/Parts.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`.

## Findings (2026-10-10)

- **The item's premise was half right.** The links did *not* use the full path: every category link — the
  header, home's and the root's tiles, the breadcrumbs of a category and of a product, a search's suggestions and
  popular tiles — was `/c/<slug>`, the last slug alone, and so were the addresses a category page's filters load.
  The route answered any path by its last slug, so `/c/electronics/headphones` (which is not even the category's
  path: headphones sit under `electronics/audio`) opened the page, and the first filter's `/c/headphones` was a
  path the shell had not drawn. **Kept the decision's form, the full path**, as written; the parenthetical reason
  was wrong, the choice itself stands on its own (the address says where the category is, and the shared tests
  and `StorefrontPage` already wrote it that way). Swapping to the last slug later is one function (`pathOf`)
  and the redirect keeps either form's old links working.
- **One address**: `pathOf` (`feature/catalog/domain/Catalog.kt`), the slugs of the category's lineage —
  `/c/electronics/audio/headphones`, `/c/electronics/audio`, a top-level `/c/electronics` as before. Every link is
  `categoryLink(category, categories)`, and `CatalogUrl` renders the same path, so every `load` on a category page
  names the path the page is at.
- **The other form redirects**: `OneCategoryAddress` (`feature/catalog/OneCategoryAddress.kt`, an application
  plugin, so the storefront page served from the bundle is covered too) answers `301` to the whole path, query
  kept as the request had it, at `/c/…` (the page), `/ui/c/…` (its tree) and `/ui/parts/c/…` (its parts), when the
  last slug names a category and the path is not its address — the slug alone, a skipped level, a wrong parent.
  Anything else is the route's own answer as before: the catalog's root, a category that is not there (`404`), a
  trailing slash. Cost: one read of the categories per `/c` request on top of the page's own.
- **A tab drawn before the deploy** follows its old links into the redirect: the fetch of `/ui/c/headphones`
  follows the `301`, the page is drawn at the old address, and its first filter loads the page once at the whole
  path; from there they agree. A typed or shared old address is redirected before the app starts.
- **Client**: no code changed. The shell compares paths (B-62); with one address per category the paths it
  compares are the server's own. `shell/Navigation.kt`'s comment names the address form. The client's desktop tests keep
  addresses such as `/c/mugs`: they are a fake server's, and the shell reads no form into them.
- **Tests** — `OneCategoryAddressTest` (3, server): every category link on home, the root, three category pages,
  two searches, the suggest panel, a product and the deals equals the address computed from the seed and its tree
  answers `200` without a redirect (and a subcategory, a leaf and a suggested leaf are among them); every `load` on
  a leaf, a subcategory and a filtered top-level category opened from a product's breadcrumbs names that path and
  is answered with an address of the same path; the other forms answer `301` with the address at all three
  prefixes, the address itself, the root and an unknown category do not. `WebBundleTest`: `/c/headphones?brand=Sony`
  arrives at the page at `/c/electronics/audio/headphones?brand=Sony`. The e2e opens the product's own category from
  its breadcrumbs, ticks a brand there and checks the `update`'s address has the page's path. Server tests that
  asked `/ui/c/headphones` or `/ui/c/mugs` ask the whole path now.
- **The image's AOT training asked the old form** (`server/build.gradle.kts`, the zavarnik workload's
  `/ui/c/headphones?…`): answered `301` now, the image build stopped at training. It asks the whole path.
- **Mutations** (each on the committed change, restored, `git status` clean): links back to the last slug —
  `OneCategoryAddressTest` 2 red; the page's loads by the last slug (the stand's defect) — 1 red; the redirect
  plugin not installed — 2 red (`OneCategoryAddressTest`'s redirects, `WebBundleTest`'s old address).
- **Goldens and wire fixtures unchanged**: the fixtures carry only the header's top-level categories, whose
  address is their slug as before; `viddikVerify` green, no PNG in the change.
- **Where it ran** (the Linux box, `MemoryMax=5G`, runs apart): `:composeApp:wasmJsBrowserDistribution`; `check
  :server:installDist` — `:server:test` 391, `:composeApp:desktopTest` 188, `viddikVerify`, ktlint, all green;
  `scripts/e2e.sh` against the branch's image (its AOT training included) — `WholePathTest` green.
  `scripts/image-check.sh` not run here. The Mac: `ktlintFormat`, `make check`, `make docs-against BASE=origin/main`.
