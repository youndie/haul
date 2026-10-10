---
id: B-71
title: "client + server: what looks pressable on the product page does something"
status: done
priority: P2
size: M
stage: stage-10-review
---

# B-71 — client + server: what looks pressable on the product page does something

On the product page these look like controls and do nothing (`feature/product/ProductDetailsView.kt`):
- gallery thumbnails and the «+3» tile (:126-153); the phone gallery dots (:249-267); the extra thumbnails are
  invented tone tiles (`ProductScreen.kt:174, :329`), the counter says «1 / 8» with one photo;
- the share button (:229-245) — no wire field;
- the brand name in link colour (:271-273); stars and «N reviews» in link colour (:289-313) — should open the
  Reviews tab; «All specifications» (:417-420) — should open the Specifications tab;
- the seller card with a chevron (:664-688; `SellerSummary` has no action);
- out of stock, «Save» for an already saved item is drawn enabled and does nothing (:535-546);
- in the tabs (`ProductTabsView.kt`): review photo thumbnails (:375-379), the rating histogram bars (:314-334), the
  «Helpful» pill on the viewer's own review (:388-397); reviews and questions stop at 10 with no «more» or paging
  (`ReviewTabs.kt:56, :85, :213`).

- **Decided as product owner:** each item is wired or drawn as plain text; nothing keeps the look of a link without
  being one. The gallery shows the photos the product has (and no invented ones).
- AC: a client test per wired control; the rest no longer look pressable; goldens re-recorded where the look
  changes, each reviewed.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductDetailsView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ProductTabsView.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`.

## Findings (2026-10-10)

- **Wired** (each a field of the tree, each with a client test in `ProductPageLinksTest`):
  - the brand — `ProductDetails.brandAction`, the brand ticked in the product's category
    (`/c/electronics/audio/headphones?brand=Sony`, `CatalogUrl`);
  - the stars, the rating and «2,341 reviews» — `ratingAction`, the reviews tab at the SKU shown; «All
    specifications» — `specificationsAction`, the specifications tab. Both stay on the page (one path, B-62), and a
    tab that arrives on a kept page brings the tab row into view with the first 240 dp of the tab under it
    (`ProductTabsView`, `BringIntoViewRequester`): from above the fold the tab would otherwise change out of sight.
    A tab pressed in the row is already in view and does not move;
  - share — `ProductDetails.share`, the product's address at its SKU. kompot has `copy_text`, but the server does not
    know the public origin the link must carry, so the client copies the page's own origin plus the address
    (`LinkCopier`, `navigator.clipboard` in `shell/ClipboardLinks.kt`, through `App(links = …)`) and the icon turns
    into a check for two seconds («Link copied» to a screen reader). A browser that refuses the clipboard leaves the
    icon as it was. No server field, no button;
  - out of stock, «Save» is the heart's twin: «Saved» with a filled heart once the product is on the list, and the
    press takes it off, as the heart does (it used to drop the unsave command and press nothing);
  - «Show more reviews» / «Show more questions» — `ProductReviews.more`, `ProductQuestions.more`: kompot's `load` of
    `/ui/parts/p/{id}?sku=…&tab=…&shown=<n+10>`, answered with an `update` of the tab's node (`reviews`, `questions`)
    and the address, `push` (B-63's shape; research D2's table). `shown` is 10–500, else `400 validation_failed`; a
    product gone answers `navigate`. «More» follows the stored rows — one more is asked for than is drawn — not the
    product's count: the seed stores two reviews and three questions of the headphones against the canvas's 2,341
    and 86, so the stand shows no «more» there.
- **Drawn as words**: the seller card has no chevron (there is no seller page); the viewer's own review reads «Your
  review» in the outline colour where the others have the «Helpful» pill (`Review.helpfulLabel`, set by the server).
  A brand, a rating or «All specifications» without an action is drawn in the text colour, not the link colour.
- **The gallery shows the photo the product has**: `ProductDetails.gallery`, `morePhotos`, `photoTotal` and
  `photoCount` are gone from the wire; the desktop photo takes the gallery's width, the phone has no dots, the
  caption reads «PRODUCT PHOTO» without «· 1 / 8». The caption itself and the tile under the photo are B-78's.
- **Left as they are, on purpose**: the review photo tiles and the rating histogram. Neither is pressable — no
  press, no hand cursor — and neither has somewhere to go: the review tiles are the photos the seed says a review has
  (placeholders until B-78, which decides where pictures come from), and a histogram filter would be a feature no
  document asks for. If the owner wants either to open something, it is a new item.
- **Found on the way**: `OneCategoryAddressTest` took every `/c/…` link to be a bare category address; the brand link
  carries a query, so the test reads the link's path.
- **Tests**: client `ProductPageLinksTest` (8: brand, reviews count and «All specifications» opening their tab with
  the row in view, share, «Saved», both «Show more», own review). Server `ReviewPagesTest` (5: ten and ten more in
  place for reviews and questions, the page after the update equal to the page at its address; ten stored draw no
  «more»; `shown` bounds; a product gone), a links test in `ProductRoutesTest`, «Your review» in `ReviewRoutesTest`.
- **Mutations** (on the committed change, restored, `git status` clean): client — no bring-into-view, the brand not
  followed, share pressing nothing, «Save» dropping the unsave command, no «more» button: 7 of 8 red, each on its
  test (the «Saved» test first failed for the wrong reason, an unmerged label when the button presses nothing; it
  reads the words from the unmerged tree now and fails on the missing command). **Survived**: the own review drawn as
  the pill again — a pill with neither a command nor an action presses nothing either, so only the look changes,
  which the server's label test and no semantics hold. Server — «more» never offered, «Your review» never written,
  no brand link, `shown` unbounded: 5 red; the parts route answering `navigate`: 2 red.
- **Goldens**: the 14 `Product_*` goldens and B-75's two `Product_InCart*` re-recorded on the Linux box and looked
  at: the thumbnails, «+3» and «1 / 8» gone and the photo wider (desktop), the dots gone (phone), no seller chevron.
  Nothing else moved; every other golden is byte-identical.
- **Where it ran** (the Linux box, `MemoryMax=5G`, separate runs, after the rebase onto B-75):
  `:composeApp:wasmJsBrowserDistribution`; `check :server:installDist` — `:server:test` 411, `:composeApp:desktopTest`
  244, `viddikVerify` 145, ktlint, all green; `scripts/e2e.sh` against the branch's image — `WholePathTest` green.
  Rebased onto B-67 after that (client only): `:composeApp:check` — `desktopTest` 258, `viddikVerify` 145, ktlint —
  and `:composeApp:compileKotlinWasmJs` green. The Mac: `ktlintFormat`, `make check`, `make docs-against
  BASE=origin/main`.
