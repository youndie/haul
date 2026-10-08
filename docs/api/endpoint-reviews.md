---
id: endpoint-reviews
title: Reviews and questions
type: api_endpoints
status: draft
services:
  - haul-server
contract_source:
  - haul:shared ReviewEntry
  - haul:shared QuestionEntry
  - haul:shared ErrorCode
parent_feature: feature-reviews
---

# API: Reviews and questions

> The two commands exist since B-22 and are described as the code has them; the helpful vote is
> *planned* (B-43), which keeps this document a draft. There are no route classes in `shared`: the
> paths are the server's strings (`ReviewPaths` in `ReviewsRouting.kt`), handed to the client inside
> the dialogs' components (`ReviewForm.url`, `QuestionForm.url`); the contract is the command bodies
> and their rules (`shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/ReviewCommands.kt`)
> and `ErrorCode`.

Reading reviews and questions is the Product screen's `tab` parameter (`GET /ui/p/{productId}?tab=reviews`
or `?tab=questions`, [endpoint-catalog](endpoint-catalog.md)).

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/products/{id}/reviews` | haul-server | customer (shildik bearer) | yes | request: `ReviewEntry` (`rating`, `title`, `body`); answers `201` with kompot's `sequence` of `close` and `refresh` |
| `POST` `/api/v1/products/{id}/questions` | haul-server | customer (shildik bearer) | yes | request: `QuestionEntry` (`text`); answers `201` with kompot's `sequence` of `close` and `refresh` |
| `PUT` `/api/v1/reviews/{id}/helpful` | haul-server | customer (shildik bearer) | yes | *planned, B-43*: request: —; answers action: refresh |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `POST` `/api/v1/products/{id}/reviews` | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/ReviewsRouting.kt` → `ReviewCommands.post` (`server/src/main/kotlin/io/github/youndie/haul/feature/reviews/domain/ReviewCommands.kt`) → `ExposedReviews.write` (`server/src/main/kotlin/io/github/youndie/haul/feature/reviews/data/ExposedReviews.kt`) |
| `POST` `/api/v1/products/{id}/questions` | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/ReviewsRouting.kt` → `ReviewCommands.ask` |
| `PUT` `/api/v1/reviews/{id}/helpful` | *planned* (B-43), in `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/` |
| the tabs and the forms | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/ReviewCommands.kt` (`ReviewEntry`, `QuestionEntry`, `ReviewRules`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt` (`ReviewForm`, `QuestionForm`, `FormProduct`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

## Request and response bodies

Not copied here; what the server does with them:

* **Checked in this order**: a caller who is not a customer is `401 unauthenticated`; a body that is
  not the command's JSON `400 validation_failed` with `field: "request"` (not `body`, which is the
  review's own field); an unknown product `404 product_not_found`; a form at fault `400
  validation_failed` with every field at fault in `fields` (`rating`, `title`, `body`; `text`) —
  `field_required` when empty, `field_invalid` otherwise — and `field` the first of them; a second
  review of the product by the same customer `409 review_exists`.
* **A review** is stored without its leading and trailing blanks, signed «Maya K.» from the
  customer's name, marked «Verified purchase» when the author has a `delivered` or `picked_up`
  shipment holding the product, and counted into the product in the same transaction (count,
  histogram, average). Two posts at once both pass the check; the unique index lets one in and the
  other is `409 review_exists`.
* **The answer**, `201` with kompot's `SequenceAction` of `CloseAction` and `RefreshAction`
  (`CLOSE_AND_REFRESH`): the dialog closes and the page is drawn again with what was written.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/products/{id}/reviews` | `400` validation_failed (with `fields`; `request` for a body that is not JSON), `401` unauthenticated, `404` product_not_found, `409` review_exists |
| `POST` `/api/v1/products/{id}/questions` | `400` validation_failed (with `fields`; `request` for a body that is not JSON), `401` unauthenticated, `404` product_not_found |
| `PUT` `/api/v1/reviews/{id}/helpful` | *planned* (B-43): `401`, `404`, `409` own_review (`ErrorCode` has no `own_review` yet) |

Tests: `server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewRoutesTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewFixturesTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewCountingTest.kt` — PostgreSQL and
shildik in Testcontainers; the client's half in
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/product/ReviewCommandsTest.kt`.
