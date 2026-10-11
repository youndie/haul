---
id: B-99
title: "server + client: reviews and questions behave as they say"
status: done
priority: P2
size: M
stage: stage-11-qa
---

# B-99 — server + client: reviews and questions behave as they say

Found in the QA pass of 2026-10-10 (v0.3.0):
- «Write a review» is offered (product tab and every delivered order line) after the customer has reviewed; the
  dialog is filled in and Post answers `409 You have already reviewed this product`
  (`server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt:79, 174`, `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt:558`);
- the «Helpful» pill looks the same voted or not; a second press takes the vote back silently (`ReviewTabs.kt:141-146`);
- the question dialog promises «Sony answers questions. You'll see the answer on this page» but nothing answers
  (`ReviewTabs.kt:205, 163`);
- «2,341 reviews» and «86 questions» sit over 2–4 stored rows with no way to reach the rest; the histogram is computed
  from the stored rows (`server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/ProductScreen.kt:187`, `ReviewTabs.kt:72-76`) — so B-71's «Show more»
  cannot even be seen on the stand;
- the review dialog's text box takes focus only on its first line (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewDialogs.kt` `AreaField`);
  the question dialog shares it.

- AC: a reviewer sees their review instead of «Write a review»; a voted «Helpful» looks voted; the question copy says
  what happens; counts match what can be read (seed more rows or count the stored ones); the whole text box focuses;
  tests.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewDialogs.kt`.

## Findings

- **Counts: the rows are seeded, not the counts lowered** (the item left the choice open). Counting the stored rows
  would have written «2 reviews» on the hero product and 0 on nearly every card, and taken «Popular», «Bestseller»
  and every artboard's copy with it. `server/src/main/kotlin/io/github/youndie/haul/seed/SeedReviews.kt` writes the
  rest of each product's count in SQL — 60,849 reviews and 6,799 questions on the seed, the headphones' 2,341 / 86
  among them, their stars following the canvas's histogram (or the one a product's average makes), the canvas's two
  reviews and three questions the newest of each. The generated catalogue's counts were scaled to 0–23 reviews and
  0–7 questions from the same random draws, so nothing else in the seed moves. The fill runs on every seeding start:
  a stand seeded before it gets the rows on its next deploy, then every product's counts and histogram recounted from
  its rows (a customer's own review counted once); a database that has them all reads one count and writes nothing.
  Cost: a fresh seed takes 2.2–2.7 s instead of 0.6–1.4 s on the build box. Recorded in research-architecture
  («Decided in B-99»).
- The headphones' open question («Is the headband adjustable…», Oct 6) is now the 86th: answered ones list first,
  and 83 seeded answered ones sit between. Product_Questions on the canvas draws it third; the goldens follow the
  server (B-85 is where the canvas catches up).
- «Write a review»: a reviewer's tab has no action and says «You reviewed this on Oct 7» where the button was, their
  review leading the list wherever its date would put it; a delivered order line reads «Your review», the product's
  reviews tab. `ReviewTabs.orderLine`, `ReviewRepository.reviewBy` / `reviewed`.
- «Helpful»: `Review.helpfulVoted`; the pill is filled in ink with a tick while the vote is on, and is the node's
  `selected` for a screen reader.
- The question dialog's note now says «Your question appears on this page for other shoppers, marked «Not answered
  yet». Sellers can't answer new questions on Haul yet.» The tab's «Answers come from Sony Official Store.» stays: the
  answers shown are the seller's.
- `AreaField`: the `BasicTextField` is now the whole box (border and min height on the field, padding in its
  `decorationBox`), so a press anywhere in it focuses it; the review and question dialogs share it.
- Goldens re-recorded on Linux: Product_Reviews(_Phone), Product_Questions(_Phone) — ten listed now; the two dialogs
  (_Phone) — the page under them, and the question note; new Product_Reviews_Mine (no artboard). The client's
  `product_reviews.json` / `product_questions.json` are the server's trees again (`ReviewFixturesTest`), and
  `product_reviews_mine.json` is new.
- Checks, on the Linux build box: `:composeApp:wasmJsBrowserDistribution`; `check :server:installDist` (server 444
  tests, client 331, `viddikVerify`, ktlint) green; `scripts/e2e.sh` against an image built from the branch green,
  all re-run after rebasing on B-88, B-97 and B-98.
  Mutation: dropping the reviewer's own-review branch, `helpfulVoted`, the order line's `reviewed`, the new question
  note, the fill on an already-seeded start, the pill's `selected` and the `AreaField` change each turned its test red.
  The reviewer's words in place of the button are held by the `Product_Reviews_Mine` golden only: the button they
  replace had no press either, so no semantic test tells the two apart.
