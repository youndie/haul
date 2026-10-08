---
id: feature-reviews
title: Reviews and questions
type: feature
status: draft
owner: unassigned
involved_services:
  - haul-shared
  - haul-server
  - haul-web
client_entries:
  - screen-product
api:
  - endpoint-reviews
tags: []
---

# Reviews and questions

## 1. Overview

Reviews with a rating histogram; questions to the seller. Answers exist only in the seed data — there is no seller side to write new ones.

> Built (B-22): the Reviews and Questions tabs served from storage, «Write a review» and «Ask a
> question» as dialogs over them, and their two commands. Still *target*, which keeps this document a
> draft: «Helpful» votes (B-43) — the button is drawn and carries nothing.

## 2. Business rules

* any signed-in customer may review a product, **once** (`409 review_exists`, a unique index under the check); a guest's «Write a review» and «Ask a question» go to sign-in (`navigate` to `/sign-in`);
* rating 1…5, title 1…120 characters, body 20…5,000; a question 10…1,000; lengths count the text without its leading and trailing blanks. The rules live once, in the contract (`ReviewRules`, `reviewProblems`, `questionProblems` in `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/ReviewCommands.kt`): the server refuses by them with every field at fault, and the client checks them before it sends;
* «Verified purchase» is a **mark, not a gate**: set when the review is written if the author has a shipment of one of their orders holding the product that is `delivered` or `picked_up`; without one the review is posted unmarked;
* writing a review takes the product's row and, in the same transaction, moves its review count, its histogram (`rating_counts`) and its average — the stored average moved by the new review's share, not recomputed from the histogram (`ExposedReviews.average`); asking moves the questions' count;
* reviews are listed **newest first** (product owner's decision, B-22: the only order the canvas's data agrees with; no sort control is drawn); questions answered first, each group newest first; at most 10 of each are listed, with no paging;
* a new question shows «Not answered yet»;
* *target* (B-43): «Helpful» — one vote per customer per review, a second press takes it back, the author cannot vote on their own (`409 own_review`).

Numbers in these rules (limits, page size) are decisions of the brief, recorded in
[research-architecture](../research/research-architecture.md) D6–D7, and checked against the code.

## 3. Flow

1. The product page's tab row lists four tabs with the reviews' and questions' counts;
   `GET /ui/p/{productId}?tab=reviews` / `?tab=questions` answers the tab ([endpoint-catalog](../api/endpoint-catalog.md)).
2. «Write a review» / «Ask a question» carry kompot's `present` of the form (`ReviewForm`,
   `QuestionForm`) for a customer, `navigate` to `/sign-in` for a guest. The dialog has no address.
3. Post / Send: `POST /api/v1/products/{id}/reviews` with `ReviewEntry`, or
   `POST /api/v1/products/{id}/questions` with `QuestionEntry` (customer tier) → `201` with kompot's
   `sequence` of `close` and `refresh`: the dialog goes and the page is drawn again. A refusal is drawn
   in the dialog, under the field the server names; no answer keeps the dialog and what was typed.

## 4. Code anchors

| Service | Code |
|---|---|
| haul-shared | `shared/src/commonMain/kotlin/io/github/youndie/haul/feature/reviews/ReviewCommands.kt` — the bodies and the rules; `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/ProductComponents.kt` — `ProductReviews`, `ProductQuestions`, `ReviewForm`, `QuestionForm` |
| haul-server | `server/src/main/kotlin/io/github/youndie/haul/feature/reviews/` — the commands, storage, the tabs and forms (`screen/ReviewTabs.kt`); `server/src/main/resources/db/migration/V12__reviews.sql`; the seed's rows in `server/src/main/kotlin/io/github/youndie/haul/seed/SampleReviews.kt` |
| haul-web | `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/product/` — `ReviewDialogs.kt`, `ReviewCommandsClient.kt` |

## 5. Scenarios (BDD / test cases)

### Scenario: A verified review
* **Given:** Maya with delivered order #HL-46102 containing the product
* **When:** she posts a 5-star review
* **Then:** the server returns `201`; her review is first, «Oct 7 · Verified purchase», and the product's review count grows by one («2,342 reviews»).
* **Automated:** `ReviewRoutesTest.a verified review - marked verified, and the product's count grows by one` (`server/src/test/kotlin/io/github/youndie/haul/feature/reviews/ReviewRoutesTest.kt`, PostgreSQL and shildik in Testcontainers); unmarked without a delivered shipment, `ReviewRoutesTest.a review without a delivered shipment of the product is posted unmarked`

### Scenario: A second review
* **Given:** Maya already reviewed the product
* **When:** she posts another
* **Then:** the server returns `409` with `review_exists`, and the count does not move.
* **Automated:** `ReviewRoutesTest.a second review is refused with review_exists`

### Scenario: Too short
* **When:** a review body is 5 characters
* **Then:** the server returns `400` with `validation_failed` naming `body` (`field_invalid`).
* **Automated:** `ReviewRoutesTest.a body of five characters is refused naming body`; every field at once and a body that is not JSON (`field: "request"`), `ReviewRoutesTest.every field at fault is named at once`; the client refusing before it sends, `ReviewWiringTest.a form at fault by the server's rules sends nothing and says why` (`composeApp/src/desktopTest/kotlin/io/github/youndie/haul/feature/product/ReviewWiringTest.kt`)

### Scenario: A question asked
* **When:** Maya asks «Do the ear cushions come off for cleaning?»
* **Then:** the questions tab counts 87 and lists it «Asked Oct 7», «Not answered yet».
* **Automated:** `ReviewRoutesTest.a question asked shows as not answered yet and is counted`; shorter than ten characters, `ReviewRoutesTest.a question shorter than ten characters is refused naming text`

### Scenario: A guest is sent to sign in
* **Given:** a guest on the reviews tab
* **When:** they press «Write a review»
* **Then:** the button's action is `navigate` to `/sign-in`, and the commands answer a guest `401` with `unauthenticated`.
* **Automated:** `ReviewRoutesTest.the reviews tab is the server's - the canvas's rating, histogram and two reviews`, `ReviewRoutesTest.the commands are a customer's`, `ReviewWiringTest.a guest is sent to sign in`

## 6. Out of scope

* What [research-architecture](../research/research-architecture.md) D6 and D8 leave out of v1.
* Moderation; editing or deleting a review; a seller's answer (seed data only).

## 7. Quirks

* **The canvas's histogram does not give its average**: 78 / 14 / 4 / 2 / 2 % averages 4.6, every
  artboard writes 4.8. Both are kept as drawn (product owner, B-22): the histogram as counts (1,826 /
  328 / 94 / 47 / 46 = 2,341), the average on the product, moved by each new review's share.
* **#HL-46102** is Maya's delivered order holding the headphones here, while the canvas's
  Order_Delivered gives #HL-46102 a sweater and a serum ($103). No orders are seeded; the test writes
  its own #HL-46102, and the order fixtures (B-18) pick one of the two.
* The dialogs' product line is the server's — brand and title, «Sony WH-1000XM6 Wireless Noise
  Cancelling Headphones» — where the canvas writes «Sony WH-1000XM6».
* Only the sample product has rows: a generated product keeps its random counts with no reviews, no
  questions and an empty histogram. A database seeded before V12 has the tables empty until it is
  seeded again.
* With more than ten answered questions a new one would not be listed (answered first, no paging).
