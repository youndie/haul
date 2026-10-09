---
id: endpoint-reviews
title: Reviews and questions
type: api_endpoints
status: active
services:
  - haul-server
contract_source:
  - haul:shared ReviewEntry
  - haul:shared QuestionEntry
  - haul:shared HelpfulVote
  - haul:shared ErrorCode
parent_feature: feature-reviews
---

# API: Reviews and questions

> The two commands exist since B-22, the helpful vote since B-43; all are described as the code has
> them. There are no route classes in `shared`: the paths are the server's strings (`ReviewPaths` in
> `ReviewsRouting.kt`), handed to the client inside the dialogs' components (`ReviewForm.url`,
> `QuestionForm.url`) and each review (`Review.helpfulCommand`); the contract is the command bodies
> and their rules (`shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/ReviewCommands.kt`)
> and `ErrorCode`.

Reading reviews and questions is the Product screen's `tab` parameter (`GET /ui/p/{productId}?tab=reviews`
or `?tab=questions`, [endpoint-catalog](endpoint-catalog.md)).

## Routes — all of them, no exceptions

| Method and path | Service | Auth tier | In the generated schema? | Purpose |
|---|---|---|---|---|
| `POST` `/api/v1/products/{id}/reviews` | haul-server | customer (shildik bearer) | yes | request: `ReviewEntry` (`rating`, `title`, `body`); answers `201` with kompot's `sequence` of `close` and `refresh` |
| `POST` `/api/v1/products/{id}/questions` | haul-server | customer (shildik bearer) | yes | request: `QuestionEntry` (`text`); answers `201` with kompot's `sequence` of `close` and `refresh` |
| `PUT` `/api/v1/reviews/{id}/helpful` | haul-server | customer (shildik bearer) | yes | request: `HelpfulVote` (`helpful`: the vote the press leaves); answers `200` with kompot's `refresh` |

Conventions for every group — trees versus actions, the error body, `404` for «not yours» — are
in [haul-server](../services/haul-server.md), section 2.

## Handlers (code anchors)

| Route | Handler |
|---|---|
| `POST` `/api/v1/products/{id}/reviews` | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/ReviewsRouting.kt` → `ReviewCommands.post` (`server/src/main/kotlin/io/github/youndie/haul/feature/reviews/domain/ReviewCommands.kt`) → `ExposedReviews.write` (`server/src/main/kotlin/io/github/youndie/haul/feature/reviews/data/ExposedReviews.kt`) |
| `POST` `/api/v1/products/{id}/questions` | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/ReviewsRouting.kt` → `ReviewCommands.ask` |
| `PUT` `/api/v1/reviews/{id}/helpful` | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/ReviewsRouting.kt` → `ReviewCommands.helpful` → `ExposedReviews.vote` (`helpful_votes`, `server/src/main/resources/db/migration/V15__helpful_votes.sql`) |
| the tabs and the forms | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/screen/ReviewTabs.kt` |
| contract | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/ReviewCommands.kt` (`ReviewEntry`, `QuestionEntry`, `ReviewRules`, `HelpfulVote`, `HelpfulCommand`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt` (`ReviewForm`, `QuestionForm`, `FormProduct`), `shared/src/commonMain/kotlin/io/github/youndie/haul/ErrorCode.kt` |

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
* **A helpful vote** — the tree carries the vote a press leaves (`HelpfulCommand`, `HelpfulVote(helpful
  = !voted)`), so a press sent twice counts once. `true` writes the customer's row with `ON CONFLICT DO
  NOTHING`, `false` deletes it, and `reviews.helpful` moves by one only when a row went in or out, in
  one transaction; the primary key `(review_id, customer_id)` is «one vote each». The author's own
  review carries no command and is refused all the same.
* **The answer**, `201` with kompot's `SequenceAction` of `CloseAction` and `RefreshAction`
  (`CLOSE_AND_REFRESH`): the dialog closes and the page is drawn again with what was written; the
  vote answers `200` with `refresh`, which the client follows on a refusal too.

## Errors

| Route | Status and `code` |
|---|---|
| `POST` `/api/v1/products/{id}/reviews` | `400` validation_failed (with `fields`; `request` for a body that is not JSON), `401` unauthenticated, `404` product_not_found, `409` review_exists |
| `POST` `/api/v1/products/{id}/questions` | `400` validation_failed (with `fields`; `request` for a body that is not JSON), `401` unauthenticated, `404` product_not_found |
| `PUT` `/api/v1/reviews/{id}/helpful` | `400` validation_failed (`request`, a body that is not JSON), `401` unauthenticated, `404` review_not_found, `409` own_review (`ReviewRoutesTest.a vote on one's own review is refused with own_review`, `ReviewRoutesTest.a vote is a customer's for a review that exists`) |

Tests: `server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewRoutesTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewFixturesTest.kt`,
`server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewCountingTest.kt` — PostgreSQL and
shildik in Testcontainers; the client's half in
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/shell/TreeCommandsTest.kt` (the seam every
tree-fixed command goes through since B-51) and
`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/product/ReviewWiringTest.kt`.
