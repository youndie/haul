---
id: B-36
title: "server: a reloaded or shared storefront address answers 404"
status: done
priority: P1
size: S
stage: stage-3-search
blocked_by: [B-35]
---

# B-36 — server: a reloaded or shared storefront address answers 404

Since B-35 the app navigates by pushing browser history (`/c/…`, `/p/…`, `/search?q=…`), but the
server serves the bundle with no fallback to `index.html` (B-27, on purpose: an unknown `/ui/...` must
stay a 404). So any address the shopper reloads, pastes or was sent answers 404 — only in-page
navigation works. For a store that is a defect: a shared product link is the commonest way in.

Serve the page for the client's own addresses — exactly the shapes `shell/Navigation.kt` maps
(`/`, `/c/{slug}`, `/p/{id}`, `/search`, `/cart`, `/account`, `/sign-in`), as an allow-list, not a
catch-all — with the page's `no-cache`, while `/ui/...`, `/api/...`, `/images/...` and unknown paths
keep their 404. The client then draws its own not-found for a product or category that does not
exist, as it already does in-page.

- AC: a request for `/p/<existing id>` and `/c/<slug>` answers the page (200, `text/html`, `no-cache`);
  `/ui/nowhere`, `/api/nowhere` and `/nowhere` stay 404; a reload in headless Chrome on a product
  address draws the product.
- Anchors: `shared/src/commonMain/kotlin/io/github/youndie/haul/StorefrontPage.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/WebBundle.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/WebBundleTest.kt`,
  `server/src/test/kotlin/io/github/youndie/haul/StorefrontPageTest.kt`, `scripts/image-check.sh`.

## Findings (2026-10-08)

- **One list, in `:shared`.** The shapes are `StorefrontPage` — `/`, `/c/{path...}`, `/p/{productId}`,
  `/search`, `/cart`, `/checkout`, `/account`, `/sign-in` — and both halves read it: the server to decide where the
  page answers, the client's `Address.kind` to decide which page an address is. One definition rather
  than a test on each side pinning a copy: it cost one small file (string splitting, no regex in the
  wasm), and two pinned copies would still let a third place — the next screen — drift from both.
  Exact shapes, not prefixes: an empty segment or a trailing slash is no page, a product has one id
  (`/ui/p/{productId}`), the other pages none.
- **`/checkout` joined the list on the rebase.** B-14 merged while this was built, with a
  `/ui/checkout` screen the cart links to as `/checkout`; without it in the list a customer reloading
  checkout would get the 404 this item removes. It is exactly the drift the last finding is about.
- **A category address has one or more slugs**, not exactly one: the screen route is
  `/ui/c/{path...}`, the catalog reads the last slug, the client already mapped any `/c/…` to the
  catalog, and `CatalogRoutesTest` asks `/ui/c/electronics/audio/headphones`. Allowing only `/c/{slug}`
  would have made an address that works in the page a 404 on reload. `/c/` with nothing after it, and
  any extra segment on the other pages (`/p/p-1/reviews`, `/search/x`, `/cart/1`), stay 404.
- **How it is served.** The static route gets Ktor's `default("index.html")`, and its `filter` — which
  runs before any file is looked for — turns away what is neither a file of the bundle (resolved with
  Ktor's own `combineSafe`) nor a storefront address, and source maps; those fall through to the 404 as
  before. So the page at `/p/…` is exactly the answer `/` gets: `text/html`, `no-cache`, brotli or gzip
  from the image's precompressed `index.html`, `Vary: Accept-Encoding` on the plain answer. Ktor keeps
  the default file in memory and watches the directory for changes to it (one watcher per process).
- **The client follows the list.** `Address.kind` now reads `StorefrontPage`, so `/p/p-1/reviews` or
  `/c/` — which the server has no screen for either — are drawn as «a page» (generic not-found) rather
  than as a product or category; `/cart`, `/account` and `/sign-in` are `Other` as before (B-13 may give
  the cart a kind of its own, from the same enum).
- **The AOT workload** asks `/p/p-sony-wh-1000xm6` too, so the fallback is trained: 553 of 553 of our
  classes from the cache in `scripts/image-check.sh`.
- **Tests and where they ran** (the Linux build machine): `WebBundleTest` +4 (every shape answers the
  page `no-cache` and varying; brotli at `/`, a product and a nested category; twelve paths that are no
  storefront address — `/nowhere`, `/ui/nowhere`, `/api/nowhere`, `/images/nowhere.webp`, `/deals`,
  `/c/`, `/c/headphones/`, `/p/`, `/p/p-001-05/reviews`, `/search/extra`, `/cart/1`, `/p/p-001-05.map` —
  stay 404; the bundle's own files are still the files), `StorefrontPageTest` (2), `AddressTest` +1.
  Mutation-checked: no `default` (the two page tests fail), a catch-all `filter` (the two 404 tests
  fail), a product shape that takes extra segments (`StorefrontPageTest` and the 404 test fail), the
  client's prefix mapping restored (`AddressTest` fails). The gate: `check :server:installDist
  :composeApp:wasmJsBrowserDistribution` (server 108 tests, client 38, `viddikVerify`; rebased on
  B-14 with `/checkout` added: server 124, client 38),
  `scripts/image-check.sh` (now also `/p/p-sony-wh-1000xm6` → 200 `text/html` `no-cache` `br`, `/nowhere`
  → 404), `scripts/chart-check.sh`; `make check` on the Mac.
- **The browser walk** (headless Chromium over CDP, B-35's harness, the image built from this branch,
  PostgreSQL, `HAUL_SEED=true`, no shildik): `/p/p-001-05` opened from the address bar drew the Tandem
  Lightweight Speakers page with its crumbs; `location.reload()` on it drew it again (navigation type
  `reload`, document status 200); `/c/electronics?brand=Sony` opened drew the category with the Sony
  chip; `/p/p-nope` drew Product_NotFound («This product is no longer available») over a 200 page;
  `/nowhere` was the browser's own 404.
- **Not done here:** nothing ties a new screen route under `/ui` to the list. A test that walks the
  application's routes and requires a `StorefrontPage` for every `/ui/…` page (the suggest panel
  excepted) would catch a screen whose reload is a 404; a line for whoever adds the next screen.
