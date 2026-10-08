---
id: B-35
title: "client: the app loads screens from the server and navigates between them"
status: done
priority: P1
size: M
stage: stage-3-search
blocked_by: [B-07]
---

# B-35 — client: the app loads screens from the server and navigates between them

Every screen so far renders to the canvas from fixture bodies, and the app's root still draws the
theme with an empty body: nothing fetches a tree, nothing follows a `NavigateAction`, and «now» does
not tick. B-07 and B-10 both recorded this as «not done here» and no item owned it — so the stand
would answer a page that draws nothing (B-28 had to wire Home by hand to measure a real first load).

The app shell: resolve the browser's path to a screen route, fetch its kompot tree from the same
origin, draw it through `haulRegistry()` inside the header frame, follow `NavigateAction` deeplinks
(browser history and back), refresh on kompot's `refresh`, and draw the shell's loading, error
(retry) and not-found states between them. The search field fetches `/ui/search/suggest` as the
shopper types and opens `/ui/search` on submit; the countdowns tick from one clock the screens share.

- Not covered: sign-in redirects (B-12), cart commands (B-13).
- AC: in the browser bundle, `/`, a category, a product and a search load from a running server and
  link to each other through the actions in their trees; back returns; a server error shows the
  error state with retry. Tested against a fake transport (states, navigation, refresh), and walked
  once in headless Chrome against the image.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Transport.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/Links.kt`,
  `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/shell/WindowHistory.kt`,
  `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/StorefrontTest.kt`.

## Findings (2026-10-08)

- **Built.** The browser's address is the navigation state, and it is the deeplink the server's
  `NavigateAction`s carry (`/`, `/c/…`, `/p/…`, `/search?q=…`); the screen's tree is the same address
  under `/ui` (`/` is `/ui/home`), so the client builds no other URL — except the one its own search
  field submits, `/search?q=` encoded the way `SearchScreen.searchLink` encodes it (`%20`). kompot's
  runtime does the work it has: `KompotScreenLoader` loads each address (Loading, Failed with retry),
  `KompotScreen` draws it, `withRefresh` answers `refresh`. The shell (`shell/Storefront.kt`) owns the
  address, `history.pushState` and `popstate` (`WindowHistory`), the header the client last drew
  (Product_NotFound is drawn under it), the search field and the one ticking «now».
- **Links.** A component's action reaches the views deep inside its renderer through
  `LocalHaulActions`, which the registry provides around every Haul renderer (a decorator in
  `haulRegistry()`); a view adds `Modifier.follows(action)`, a crumb `appendLink` (a `LinkAnnotation`),
  a button `onClick = following(action)`. With no handler — every screenshot — nothing is added, so the
  pixels are the same: `viddikDesignParity` 43/43 within tolerance with the same percentage per
  artboard as main (`3ac68e5`) to the hundredth, `viddikVerify` green, no golden re-recorded. The logo
  goes home (the client's own address, like Product_NotFound's button).
- **Seams for the other items.** Every request goes through one `HaulTransport`; the browser's is
  `ktorTransport(http, origin, identity::send)` over B-30's client, so B-12's `Identity.send` adds the
  bearer or `X-Haul-Guest` and retries a `401`. A tree's `navigate` to `/sign-in` is claimed by B-12's
  `SignInActions` before navigation and the screen is redrawn in place. `LocalScreenRefresh`
  (`ScreenRefresh.refresh()`) re-fetches the screen being shown and draws it in place; it is what
  kompot's `refresh` runs, and what a cart command (B-13) calls after it changed the cart. B-12's
  stand-in host (`Trees`, home only) is gone; `App(photos, transport, history, identity, clock)`.
- **Fixed on the way, a wire defect: the client could not read a guest's screen.** The server writes
  with `explicitNulls = false` and left the guest header's `customerName` out; the client's Json
  required it, and the first walk drew «The home page didn't load» over a `200`. The screenshot bodies
  spell their nulls out, so no test saw it. B-12 gave the field a default the same day; this item reads
  with the server's settings instead (`haulJson`, `WireDecodeTest`), so the next nullable field added
  without a default is not refused again.
- **The search field.** It is a `BasicTextField` only in the app (the screenshots draw it as before);
  typing asks for `/ui/search/suggest` after a 250 ms pause (one request for «run» typed a letter at a
  time), Enter or the button opens the results, a suggestion, category, product or «All N results»
  follows its own action. The panel stays open when the field loses focus — pressing a suggestion
  takes the focus first, and the panel closed under the press until this was found — and closes on
  the scrim, an emptied field or a new page.
- **Errors.** No answer is «We couldn't reach Haul», any other answer that is not a tree is the
  server's error, `404` is the not-found page. In the browser a failed fetch is a JavaScript error,
  which is not an `Exception` on Kotlin/Wasm; caught narrower, a stopped server was drawn as the
  server's error (seen in the walk, fixed).
- **Tests** (`desktopTest`, on the Linux build machine): `StorefrontTest` (13, a fake transport and a
  fake history at 1440 px: the screen route per address, Loading until the tree, a card to the product
  and back, a deep address, `500` and Retry, no answer, a failed search keeping its query, not-found
  under the last header, `refresh` in place, sign-in claimed and redrawn, typing → suggest → Enter, a
  suggestion followed, the countdown ticking with the clock), `AddressTest` (4), `WireDecodeTest` (2).
  Mutation-checked: links that follow nothing, `refresh` that fetches nothing, no debounce, the
  client's `explicitNulls` back on, `/sign-in` followed as an address — each failed the tests written
  for it. The gate: `check :server:installDist :composeApp:wasmJsBrowserDistribution` (server 102
  tests, client 37, `viddikVerify`) and `scripts/image-check.sh` (551 of 551 classes from the AOT
  cache; the module served brotli, immutable) on the Linux build machine; `make check` on the Mac.
- **The browser walk** (headless Chrome for Testing over CDP, the image built from this branch,
  PostgreSQL, `HAUL_SEED=true`, shildik 0.4.1 with a realm): `/` drew the home page as a guest (a guest
  id created on the first request); the Electronics tile opened `/c/electronics`; the Sony facet
  `/c/electronics?brand=Sony`; a card `/p/p-001-05`; the «Home» crumb `/`; back twice returned to the
  product and the category; typing «head» opened the panel (queries, a category, three products),
  Enter `/search?q=head`; the «Headphones 9» chip `/search?q=head&category=headphones`; the logo `/`
  and back the search; «Sign in» opened shildik's page in a popup, and after the form the header read
  «Maya» on the same address; the deals countdown ticked; with the server stopped the logo drew «The
  home page didn't load / We couldn't reach Haul» and Retry, once it was back, the page; `/p/p-nope`
  drew Product_NotFound under Maya's header.
- **Bundle cost** (`wasmJsBrowserDistribution` on the build machine, against main at `3ac68e5`, whose
  stand-in host already drew the home page): the app `.wasm` from 4,143,071 to 4,575,364 bytes raw
  (+422 KiB), 1,331,276 to 1,468,678 under `gzip -9` (+134 KiB) — navigation, the editable field, links
  and the shell's pages; skiko's `.wasm` byte-identical. Against main before B-12 (`1d68d4f`, which drew
  nothing and so shipped no renderers) the app `.wasm` was 1,926,538 / 643,451.
- **A link from outside opens nothing.** The server answers only files at `/` (`HaulModule`: no
  fallback to `index.html`, on purpose), so `/c/electronics` typed, pasted or reloaded is a `404` (seen
  in the walk); inside the page every address works, back and forward included. Serving the page at
  the storefront's addresses (everything that is not `/ui`, `/api` or a file) is a server decision for
  the owner, not taken here.
- **What the contract gives nothing to follow:** `HaulPagination` (pages, «Show 24 more»),
  `AppliedFilters`' «Clear all» and the sort, the header's categories, «Catalog», «Deals», Orders,
  Saved and the cart, the suggest panel's recent searches, `PlusBlock`, a card's «+» and heart (B-13,
  B-20). Those stay drawn and inert. Home's «View all deals» goes to `/deals`, which has no screen: the
  not-found page.
- **Widths the canvas does not draw.** Between 768 and about 1,150 px the wide header's field is
  narrower than the suggest panel's 270 px products column, and the panel's suggestions column
  collapses to nothing (seen at 1,024 px in a test; B-12 saw the field squeezed at 800). A design
  question, not this item's.
- **Back loads the page again** (no tree is kept per address) and opens it at the top.

