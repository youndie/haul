---
id: B-108
title: "client + server: the same thing looks the same on every page"
status: wip
priority: P3
size: M
stage: stage-11-qa
---

# B-108 — client + server: the same thing looks the same on every page

Found in the QA pass of 2026-10-10 (v0.3.0):
- the footer is drawn only on home, `/c` and `/deals`;
- `/deals` and `/c` load with only the header (no skeleton), unlike every other page (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt:73-76`);
- Saved has page numbers only (no «Show N more»); a one-page result draws a lone «1»; paging Saved on a phone keeps the
  scroll at the bottom of the next page;
- account: active-order cards open only from the small «Details ›», history rows from anywhere (B-74);
- cart lines, checkout items, order items and the suggest panel's «Top products» draw a blank tinted square where the
  card and the product page draw the stored photo (the wire has no image field there; partly B-78);
- the order page (cancelled) draws «Back to cart» twice; its summary drops the «Promo · AUTUMN10» row the cart and
  checkout show; the header's HAUL PLUS pill does nothing for a member;
- deal cards wrap the struck-out price mid-number («$432.9 / 9»); the «Added to your cart» message is hard to see over the
  dark Plus block.

- AC: per line, or the difference recorded as intended; goldens where the look changes.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/saved/screen/SavedScreen.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductCardView.kt`.

## Done (2026-10-11)

Each line of the QA pass, fixed or kept with the reason; research has «Decided in B-108».

- **The footer only on home, `/c` and `/deals` — kept.** The canvas draws it on home alone (no `Catalog_*`,
  `Product_*`, `Cart_*`, `Saved_*`, `Account_*` or `Order_*` artboard has one), and every word it holds is in the
  header on every page — «Deals» in the category row, «HAUL PLUS» at its end, «Orders» among the shortcuts. The
  two pages without an artboard that draw it are home's kind (where its «All N categories» and «View all deals»
  go). B-107 has since taken «Get the app» out of it.
- **`/deals` and `/c` load with placeholders of their own**, not the header alone: `PageKind.Categories` and
  `Deals` (`shell/Navigation.kt`), `CategoriesLoading` — the crumbs, the title, sixteen tiles as home draws them —
  and `DealsLoading` — the title and count, «Deals of the day» over a row of cards, «On sale» over a grid
  (`shell/Shell.kt`).
- **Saved: page numbers only — kept**, as B-20 decided and the canvas draws `Saved_*`. **A one-page result draws
  no «1»** — on any listing: the catalog, a search and the deals sent a lone «1» too, the Saved list already
  sent no pagination for one page. The client draws nothing for a pagination with one number and no «Show N
  more» (`PaginationView`); the server keeps the node, because a search narrowed in place replaces it by id.
  **A page number opens its page at the top** — the Saved list's on a phone, and the catalog's, a search's and
  the deals' too: the page is still kept while it loads (B-62), the scroll moves to the top at the press
  (`LocalPageScroll`, provided by the storefront's page). A filter, a sort and «Show N more» keep the scroll.
- **An active order's card opens its order wherever it is pressed** (`AccountViews.kt`, `Card`), as a history row
  does; «Details ›» is its label.
- **Cart lines, checkout's lines, an order's lines and the suggest panel's «Top products» draw the stored
  photo** when there is one: `image` on `CartLine`, `SummaryItem`, `OrderItem` and `ProductSuggestion` (default
  `null`), filled by `ProductPhotos.url` as a card's is (`CartScreen`, `CheckoutScreen`, `OrderScreen` — the
  order reads the product as the catalog has it now, as its tile's tone already did — and `SearchScreen`); the
  client draws them with `LineTile` (`ui/ProductPhoto.kt`) through the cards' loader, the tile staying while the
  photo is absent, loading or failed. Only the photos the seed already stores are wired; nothing is drawn where
  there is none (B-78 is still the owner's). The return dialog's lines keep their tiles.
- **A cancelled order draws «Back to cart» once**, in its notice beside the reason; the summary's is gone, and
  `OrderTotals.back` with it. The notice is what a phone draws first; on the other states the summary holds the
  ways on because there is no notice.
- **The order summary draws «Promo · AUTUMN10»**: an order keeps the sale and the code as one
  `discount_cents`, so «Discount» is the sale read off the order's lines (`listTotalCents − totalCents`) and the
  promo row the rest — the cart's and checkout's rows since B-100. An order without a code draws «Discount» as
  before.
- **The HAUL PLUS pill for a member is the membership's mark, not a control** (`Frame.plus` is `null` for a
  member; the footer's «Haul Plus» is text for one). It led to the account overview, which on the overview is
  the page shown — the press that «did nothing». No page is the membership's own (B-74); a dialog describing it
  was rejected (no artboard; the home page's Plus block already says what it saved and when it renews).
- **A card's old price is never broken**: beside the price when the two fit, under it whole when they do not
  (`ProductCardView.kt`, `Prices`). Six deal cards across at 1440 with cents broke «$432.9 / 9».
- **«Added to your cart» stands out over the dark Plus block**: the message has a 2 px ring of the page's white
  and a 16 dp shadow (`MessageNotice`).
