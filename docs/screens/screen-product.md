---
id: screen-product
title: Product
type: client_screen
platform: [web]
status: draft
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
| The server tree for this screen | `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/` |
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
- [x] **Reviews:** Reviews tab: 4.8, histogram 78/14/4/2/2, two reviews. Drawn from a wire body (`composeApp/src/desktopTest/resources/bodies/product_reviews.json`); the server answers `400` for this tab until B-22
- [x] **Questions:** Questions tab: two answered questions, one «Not answered yet», «Ask a question». Drawn from a wire body (`composeApp/src/desktopTest/resources/bodies/product_questions.json`); the server answers `400` for this tab until B-22
- [ ] **ReviewDialog:** dialog over Reviews: stars, title, body, Post (B-22)
- [ ] **QuestionDialog:** dialog over Questions: text, Send (B-22)
- [x] **OutOfStock:** colour «Silver» chosen: «Out of stock», Add to cart and Buy now disabled, Save kept
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

- colour / bundle → another `Sku`
- Add to cart → header count +1
- Buy now → adds and opens screen-checkout
- seller card → nothing in v1
- helpful → vote
