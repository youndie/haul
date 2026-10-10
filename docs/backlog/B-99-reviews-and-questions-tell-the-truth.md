---
id: B-99
title: "server + client: reviews and questions behave as they say"
status: open
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
