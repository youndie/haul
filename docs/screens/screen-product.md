---
id: screen-product
title: Product
type: client_screen
platform: [web]
status: active
entry:
  web: "/p/{productId}"
parent_feature: feature-product
calls_api:
  - endpoint-catalog
  - endpoint-reviews
  - endpoint-cart
  - endpoint-saved
source: haul/composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product
design:
  canvas: https://claude.ai/design/p/d306660f-831e-43aa-8911-ca02a3397c59 (page canvas/Product)
  references: haul/composeApp/src/desktopTest/snapshots/design
  states:
    Loading: Product_Loading
    Description: Product_Description
    Specifications: Product_Specifications
    Reviews: Product_Reviews
    Questions: Product_Questions
    ReviewDialog: Product_ReviewDialog
    QuestionDialog: Product_QuestionDialog
    OutOfStock: Product_OutOfStock
    NotFound: Product_NotFound
    Error: Product_Error
---

# Screen: Product

## 0a. Code anchors

| What | File |
|---|---|
| Renderers of this screen's components | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/` |
| Client shell: Loading and Error | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/` |
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/`; the reviews and questions tabs and their dialogs in `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt` |
| The dialogs and their commands | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewDialogs.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/ReviewCommandsClient.kt`; the shell draws a `present` over the page (`presenting` in `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`) |
| Reference PNGs, one per artboard | `composeApp/src/desktopTest/snapshots/design/` |
| Parity fixtures, one per artboard | `composeApp/src/desktopTest/kotlin/io/github/youndie/haul/ProductFixtures.kt` |

## 0. Entry point and visibility

- **Entry point:** `/p/{productId}` in the browser.
- **Shown when:** always; guests included.

## 1. Screen states

The names are the artboard names without the screen prefix. `Loading` and `Error` are drawn by the
client while it has no tree or after a failed request; every other state is a tree the server
returns. The list is held against the real state when the code exists.

- [x] **Loading:** breadcrumbs, photo placeholder, placeholder lines for title, price and delivery
- [x] **Description:** the page with the Description tab open
- [x] **Specifications:** Specifications tab: the full key → value list
- [x] **Reviews:** Reviews tab: 4.8, histogram 78/14/4/2/2, two reviews, newest first. Answered by the server (`?tab=reviews`, B-22); the fixture's body (`composeApp/src/desktopTest/resources/bodies/product_reviews.json`) is held equal to it by `ReviewFixturesTest`
- [x] **Questions:** Questions tab: two answered questions, one «Not answered yet», «Ask a question». Answered by the server (`?tab=questions`, B-22); `composeApp/src/desktopTest/resources/bodies/product_questions.json`, held the same way
- [x] **ReviewDialog:** dialog over Reviews: stars, title, body, Post — kompot's `present` of `ReviewForm` carried by «Write a review», drawn over the tab; no address of its own (B-22)
- [x] **QuestionDialog:** dialog over Questions: text, Send — `present` of `QuestionForm` carried by «Ask a question» (B-22)
- [x] **OutOfStock:** colour «Silver» chosen: «Out of stock», Add to cart and Buy now greyed and carrying no command, Save kept
- [x] **NotFound:** «This product is no longer available», link home
- [x] **Error:** header, message, Retry

### Artboards and sizes

Desktop artboards are named as in `design.states`; each has a phone twin with the suffix
`_Phone`, which the parity task looks up in the same directory. Heights, desktop / phone: Loading 835 / 1547; Description 1494 / 2720; Specifications 1624 / 2869; Reviews 1572 / 3067; Questions 1723 / 2930; ReviewDialog 1572 / 3067; QuestionDialog 1723 / 2930; OutOfStock 1494 / 2680; NotFound 799 / 653; Error 900 / 641.

## 2. API integration

| Call | Endpoint document |
| :--- | :--- |
| endpoint-catalog | [endpoint-catalog](../api/endpoint-catalog.md) |
| endpoint-reviews | [endpoint-reviews](../api/endpoint-reviews.md) |
| endpoint-cart | [endpoint-cart](../api/endpoint-cart.md) |
| endpoint-saved | [endpoint-saved](../api/endpoint-saved.md) |

## 5. Navigation (summary)

- colour / bundle → another `Sku` (the variant's address, followed by the shell since B-35)
- crumb → that category or home; tabs → the tab's address
- Add to cart → `PUT /api/v1/cart/lines/{skuId}` with one more of the SKU shown (`ProductDetails.add`, the card's «+»), then the page drawn again with the header's count; absent at the line's limit
- Buy now → the same line selected (`ProductDetails.buy`), then its `next`: `/checkout` (screen-checkout) for a customer, `/sign-in?next=%2Fcheckout` for a guest; a refusal draws the page again and goes nowhere (B-48)
- the heart, «Save» → `PUT` / `DELETE /api/v1/me/saved/{productId}` (`ProductDetails.heartCommand`), then the page drawn again with the heart filled or not; a guest's → `/sign-in` (B-20)
- the photo → the stored photo (`ProductDetails.photo`, B-30) over the placeholder tile, which stays without one
- seller card → nothing in v1
- «Write a review» / «Ask a question» → the dialog over the tab for a customer, `/sign-in` for a guest; Post / Send → `POST /api/v1/products/{id}/reviews` or `/questions` ([endpoint-reviews](../api/endpoint-reviews.md)), the client checking the rules first; `201` closes the dialog and draws the page again, a refusal is drawn in the dialog; Cancel and «×» close it and send nothing
- «Helpful» → `PUT /api/v1/reviews/{reviewId}/helpful` with the vote the press leaves (`Review.helpfulCommand`: the first press votes, the page drawn again carries the opposite, so the second takes it back), then the page drawn again — a refusal too; a guest's → `/sign-in`; on one's own review it does nothing (B-43)

## 6. Quirks

- «12K bought this month» under the title is drawn from the fixture bodies only: the server does not
  send `ProductDetails.bought`, so the page on the stand has no such line ([feature-product](../features/feature-product.md)).
- The client's product fixture bodies carry no `add` or `buy`: the commands draw nothing, and the bodies
  are held equal to the server only in their tabs and reviews (`ReviewFixturesTest`).
- «Helpful» has no pressed look — the canvas draws one state; after a vote only the count says so.
