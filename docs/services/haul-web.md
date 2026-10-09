---
id: haul-web
title: Haul web storefront
type: service
module: composeApp
tech_stack: [Kotlin, Compose Multiplatform, wasmJs, kompot-client, viddik]
owner: unassigned
depends_on:
  - haul-shared
  - haul-server
publishes:
  - the web bundle, served by haul-server
---

# Haul web storefront

> Describes the module as it stands after B-56: the app loads its screens from the server and
> navigates between them (B-35), signs in through shildik and keeps the guest id (B-12), returning to
> the page sign-in was asked from (B-41), asking a guest on a customer page to sign in (B-44), watching
> its own sign-in popup (B-46, B-47), draws product photos (B-30), the cart (B-13), the checkout (B-15),
> the order page with reorder and the return dialog (B-18, B-21), the account and its history (B-19),
> the Saved list and the hearts (B-20), the review and question dialogs and helpful votes (B-22, B-43),
> the Plus trial dialog and the points toggle (B-23), the product page's buy box (B-48), Haul Pay's
> schedule (B-24), the return dialog's points back (B-50), the order page moving by itself (B-29),
> follows the controls' actions (B-37) — the last of them given an action or drawn as plain text
> (B-49) — keeps the filter sheet open while filtering (B-54) and sends every tree-fixed command through
> one seam (B-51); every screen's renderers with an artboard are checked against the canvas.

## 1. Responsibility

Renders every screen the server describes, at 1440 and 390 wide, through a registry of one renderer
per Haul component; owns navigation, the `Loading` and `Error` states of every screen, the browser
sign-in flow and the guest id kept in browser storage.

Deliberately does **not**: decide what a screen holds (the server's tree does), compute a total, a
fee or a delivery date, or keep any state the server owns.

## 2. API contracts

* **Contracts:** [haul-shared](haul-shared.md); every call is listed in the screen documents.
* **Wire:** trees and error bodies are decoded with `haulJson`
  (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/HaulRegistry.kt`), kompot's
  engine settings with the server's `explicitNulls = false`, so a nullable field the server leaves
  out cannot blank a screen (`WireDecodeTest`).
* **Every request goes through `Identity.send`**: the bearer token when signed in, `X-Haul-Guest`
  otherwise — never both — and once more after a `401` that a renewed token or a new guest can
  answer.

## 2a. Code anchors

| File | What is there |
|---|---|
| `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt` | the bundle's entry point: one Ktor `HttpClient(Js)` to this origin, the photo loader, `Identity` over `PopupSignInFlow(BrowserSignInPopup::open, OidcSignInFlow(...))`, the screen transport, the command seams (`ktorCartCommands`, `ktorCommands`, `ktorCheckoutCommands`, `ktorTreeCommands`) and the live stream (`ktorRealtime`) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/App.kt` | the root: the theme at the page's width around `Storefront` |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt` | the shell: the address, history, each page's Loading and failure, the search field and its suggest panel, the one ticking clock, `LocalScreenRefresh` |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt` | `Address` (the address ↔ the tree under `/ui`), `PageKind` read off `StorefrontPage`, `BrowserHistory` |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Transport.kt` | `HaulTransport` (trees — a bare tree or kompot's `KompotScreenResponse`, read by shape — and suggest) and `HaulCommands` («Clear» on recent searches) over Ktor |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/TreeCommands.kt` | `TreeCommands`: the one seam for a command whose URL the tree fixed — `TreeCommand.Review`, `Ask`, `Return`, `StartTrial`, `Vote`; `CommandRefused`, `CommandOutcome`, `LocalTreeCommands`, `ktorTreeCommands` (B-51) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/LiveUpdates.kt` | `ktorRealtime`: a page's channel as server-sent events through `Identity.send`, reopened after 1 s doubling to 30 s, ended by a `401` or `404` (B-29) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/catalog/FiltersSheetState.kt` | the phone's filter sheet, held by the shell above the keyed page (`LocalFiltersSheet`, `FiltersSheetOverlay`, B-54) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt` | the Loading, Error and NotFound pages a screen shows before or instead of its tree (the order's, the account's and the Saved list's among them) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPrompt.kt` | what a guest's `401` on a storefront page draws: «… needs a sign-in», «Sign in to continue» (B-44) |
| `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/shell/WindowHistory.kt` | `history.pushState` / `popstate` |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/theme/` | colour roles and the four bundled fonts (`composeApp/src/commonMain/composeResources/font/`) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/registry/` | the renderer registry (`haulRegistry()`, which also provides `LocalHaulActions` around every Haul renderer) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/` | the renderers of `catalog/`, `home/` (with the Plus trial dialog, `PlusTrialDialog.kt`), `product/` (the buy box, the review and question dialogs `ReviewDialogs.kt`), `search/`, `cart/` (with `CartCommands` and `LinePress.kt`, the one press of a fixed line change), `checkout/` (`CheckoutViews.kt`, with `CheckoutCommandsClient.kt`), `order/` (the page with Haul Pay's schedule, its not-found page, the return dialog `ReturnDialog.kt`), `account/` (`AccountViews.kt`), `saved/` (`SavedViews.kt`); `identity/` |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/` | shared views: the header, cards, `Links.kt` (following an action), `LinkMenu.kt` (the sort and «Catalog» menus), `ProductPhoto.kt` (the photo over the placeholder tile), `CssLayout.kt` (columns laid out on the browser's fractional line boxes, B-18, B-19) |
| `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/identity/` | `Identity`, `IdentityApi`, `SignInActions`, `Session`, `PopupSignInFlow` (watches the popup, B-46) |
| `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/feature/identity/` | `OidcSignInFlow` (kotlin-multiplatform-oidc, told to reuse the storefront's popup), `BrowserSignInPopup` (opens `haul-sign-in` and marks the return page's answer) and `BrowserSessionStore` (guest id in `localStorage`, tokens in `sessionStorage`) |
| `composeApp/src/wasmJsMain/resources/signed-in.html` | where shildik returns the popup: it posts its address to the opener (its own origin) and closes |
| `composeApp/src/desktopTest/snapshots/design/` | one reference PNG per artboard, exported from the canvas |

## 3. How it is built

* **wasmJs ships, `jvm("desktop")` tests.** The same renderers run on both; the desktop target
  draws the screenshots that `viddikDesignParity` compares with the references.
* **A fixture is a recorded server body** for the sample data (*hypothesis*, research risk 3), so a
  screenshot tests the tree and the renderer together; the cart's six bodies and the checkout's are held
  equal to what the server builds (`CartFixturesTest`, `CheckoutFixturesTest`).
* **The theme is written from the canvas's roles**, not from the pictures (research §1.6).
* **The shell is kompot's runtime** (B-35): `KompotScreenLoader` loads each address (Loading, Failed
  with retry), `KompotScreen` draws it, `withRefresh` answers kompot's `refresh`. The browser's
  address is the navigation state and is what a `NavigateAction` carries (`/`, `/c/…`, `/p/…`,
  `/search?q=…`, `/cart`, `/deals`, `/checkout`, `/account`, `/account/orders`, `/account/orders/{id}`,
  `/account/saved`, and the catalog's root `/c`, B-49); the tree is the same address under `/ui` (`/` is `/ui/home`).
  The one URL the client builds is its own search submit, `/search?q=`. `navigate` pushes a history
  entry; back and forward load that address again and open it at the top.
* **Links**: a component's action reaches the views inside its renderer through `LocalHaulActions`;
  with no handler — every screenshot — nothing is added, so the pixels do not change.
* **`LocalScreenRefresh`** fetches the screen being shown again and draws it in place (no loading
  page, scroll kept): what kompot's `refresh` runs and what a cart command's answer triggers.
* **Failures**: no answer at all is «We couldn’t reach Haul» (on wasm a failed fetch is a JavaScript
  error, caught as such); any other non-tree answer is the server's error with Retry; a `404` draws
  `ProductNotFound` or `NotFoundShell` under the header last drawn.
* **The search field** asks for `/ui/search/suggest` after a 250 ms pause; Enter or the button opens
  the results.
* **Sign-in** (feature-identity): a `navigate` to `/sign-in` is claimed by `SignInActions` before
  navigation; `Identity.signIn` reads `GET /api/v1/sign-in`, runs the code flow with PKCE in a popup
  that returns to `signed-in.html`, keeps the tokens and merges the guest cart. Then it opens the
  action's `next` when that is a storefront address (`SignInActions.next`: a path `StorefrontPage` has
  a page for, not `/sign-in`; never an absolute URL or `//host`, B-41), or else draws the screen again
  in place; a sign-in that did not go through opens nothing — or `/` from the sign-in prompt. The popup
  is the storefront's (`PopupSignInFlow`, B-46): opened blank as `haul-sign-in` from the press, polled
  every 250 ms, a closed or blocked one ending the sign-in within a second, a second press focusing it;
  any `Throwable` inside the sign-in, a browser error included, ends it as not gone through (B-47).
* **A guest's `401` on a storefront page** draws `SignInPrompt` instead of the page's error (B-44); its
  press signs in with `next` the page itself and loads the page again in place; the load a sign-in from
  this page asked for draws the page's own error if it is refused again, so the prompt never loops.
* **Commands**: the cart's presses are `CartCommand`s (`feature/cart/CartCommands.kt`) — a card's
  «+», the product page's «Add to cart» and «Buy now» (one press, `linePress`, whose `LineCommand.next`
  is followed only once the change was accepted), the hearts and «Save for later» (`CartCommand.Heart`,
  `CartCommand.SaveForLater`) and an order's «Reorder» (`CartCommand.Reorder`) too; the checkout's are
  `CheckoutCommand`s (`feature/checkout/CheckoutCommandsClient.kt`: `Choose` — the points toggle
  included — `SaveAddress`, `Place` under one idempotency key per quote); a command whose URL the tree
  fixed — the review, question, return and Plus trial dialogs and «Helpful» — is a `TreeCommand`
  (`shell/TreeCommands.kt`, B-51), the dialogs' sent only once the contract's rules pass, a refusal a
  `CommandRefused`; «Clear» on recent searches is the one `HaulCommands` left (a request the tree names
  by method and path, its answer not followed). All go through `Identity.send`, and the answer
  (`refresh`, a refusal included) redraws the screen; placement's and reorder's `navigate` are followed.
* **The live page** (B-29): a tree that names a channel (`KompotScreenResponse.realtimeTopic`, the order
  page's `order:<id>`) is drawn inside kompot's `KompotRealtimeProvider` while it is shown, fed by
  `ktorRealtime` from `GET /ui/updates?topic=`; a frame swaps the node with its id in place; leaving the
  page stops listening; `key(tree)` makes a refresh start over from the new tree.
* **The filter sheet** (B-49, B-54) is the one control wired in the client alone: «Filters» opens it
  over the facets already in the tree; its own presses navigate and it stays open, redrawn from each new
  page; «Show N items», «×» and the scrim close it; arriving at any other address closes it.
* **Plain text**: «Help», «Sell on HAUL», the language and the footer's links have no click action until
  a page exists for them (B-49; the client's `DrawnActionsTest.help sell on haul the language and the footer are plain text`).
* **Dialogs**: the shell draws kompot's `present` over the page (`DialogOverlay`, `presenting` in
  `shell/Storefront.kt`) and follows `close` and `sequence`, so a dialog's `201` — `close` then
  `refresh` — takes the dialog away and draws the page again. A refusal is drawn in the dialog, under
  the field the server names; no answer keeps the dialog and what was typed. A dialog has no address
  of its own.
* **Photos**: a `PhotoLoader` composition local draws the stored photo over the placeholder tile on
  cards, the product photo and the first gallery thumbnail; the app's loader is Coil 3 over the same
  Ktor client (its own fetcher and disk cache off); the default loads nothing, so every fixture draws
  placeholders (`PhotoFallbackTest`).

## 4. Dependencies

| Kind | Name | What for |
|---|---|---|
| Module | [haul-shared](haul-shared.md) | the components on the wire, `StorefrontPage` |
| Service | [haul-server](haul-server.md) | every tree, every command, the photos |
| External | shildik | the browser's sign-in (discovery, JWKS and the token endpoint, read cross-origin) |
| Library | kotlin-multiplatform-oidc 0.18.4, Coil 3.6.3, Ktor client (Js) | the sign-in flow, photos, every request (`gradle/libs.versions.toml`) |

## 5. Infrastructure and deploy

* Served as static files by [haul-server](haul-server.md): `:composeApp:wasmJsBrowserDistribution`
  is copied into the server's distribution, in its `web` directory, without source maps
  (`server/build.gradle.kts`), and the server serves `HAUL_WEB_DIR` at `/`, precompressed (brotli or
  gzip, written at image build), the content-hashed `.wasm` cached for a year and the rest
  `no-cache`, and the page at the storefront's addresses — a reloaded or shared `/p/…` opens. One
  image and one origin, so the client needs no base URL.
* **First load**, measured in B-28 and again in B-34: the bytes and the time to the first frame, per
  file and per network profile, are in [research-architecture](../research/research-architecture.md)
  D9 («Measured in B-28», «Measured in B-34»). Skiko's wasm dominates them; what Haul's own screens add
  grows with each item (B-35's and B-30's findings give the deltas).

## 6. Local setup

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

The page calls the server at its own origin, and the development server has no API behind it, so
it draws no screen; to see screens, serve the distribution from a running server (`HAUL_WEB_DIR`, see
[haul-server](haul-server.md)), or look at the screenshot tests: `viddikVerify` and
`viddikDesignParity` on the desktop target.

## 7. Quirks

* Between 768 and about 1,150 px the wide header's search field is narrower than the suggest panel's
  products column, and the panel's suggestions column collapses (B-35's findings); the canvas draws
  1440 and 390 only.
* Home's promo banners carry no action, and no item gives them one.
* While the page a filter-sheet tick opened is on its way, the sheet's facets follow nothing; a page
  that fails to arrive leaves the sheet open and inert over the error until «×» (B-54).
* `/deals` and `/c` are `PageKind.Other`: a pending header while loading, the generic error page on failure.
  `/checkout`, the account's pages (`PageKind.Account`), the Saved list (`PageKind.Saved`) and an
  order (`PageKind.Order`) have their own Loading and Error pages.
* The dialogs' chrome (`DialogFrame`, `Buttons`, `FormProblems`) still lives in
  `feature/product/ReviewDialogs.kt`, and the return dialog imports it from there (B-51's findings).
* Streaming through Ktor's JS engine in wasm is proven by its parts, not by a browser drawing the live
  page (B-29's findings).
* The header's «Sign in» is inert on shell-drawn pages (not found, the sign-in prompt).
* The desktop app's own browser pane opens the sign-in popup in the same tab, so the flow cannot
  finish there.
