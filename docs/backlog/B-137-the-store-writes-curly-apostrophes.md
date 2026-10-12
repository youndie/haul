---
id: B-137
title: "fix: every sentence the store writes uses ’, not a straight apostrophe"
status: done
priority: P3
size: XS
stage: stage-12-qa-run
---

# B-137 — fix: every sentence the store writes uses ’, not a straight apostrophe

Found by B-136, which fixed «Couldn’t copy the link» from the v0.5.1 run
([run](../qa/runs/2026-10-12-v0.5.1.md)): two more of the store's own sentences use a straight apostrophe where the
store writes ’ — `ReorderAnswer` («isn't / aren't available now») and `OrderScreen.returnRule` («can't be sent
back»). They appear in tests, fixtures and the QA catalogue, so the change moves those too.

- **The store's own copy uses the typographic apostrophe**, as the canvas and the rest of the copy do; a check keeps a
  straight one from coming back in a string the store writes (server screens and client copy), shoppers' and sellers'
  words aside (seeded reviews and questions keep what their authors typed).
- Not covered: seeded reviews, questions and product names.

- AC: no string the store writes carries `'` inside a word; the two sentences above read with ’ on screen, in their
  fixtures and in the QA cases; a test fails if a straight apostrophe comes back in such a string.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/ReorderAnswer.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/order/screen/OrderScreen.kt`.

## Findings (2026-10-12)

- **Four sentences, not two.** Beside `ReorderAnswer` («… isn’t / aren’t available now») and `OrderScreen.returnRule`
  («… can’t be sent back»), the search over every string literal of the server, the contract and the client found
  `CartScreen`'s points line («You’ll earn 1,024 points on this order») and `ReviewTabs.QUESTION_NOTE` («Sellers can’t
  answer new questions on Haul yet.»). All four write ’ now. The client's own copy had none left after B-136.
- **Moved with them**: the server tests that quote them (`CustomerCartTest`, `OrderRoutesTest`, `ReturnRoutesTest`,
  `ReviewRoutesTest`), the client's `OrderWiringTest`, six wire bodies (`cart_content`, `cart_item_changed`,
  `cart_promo_applied`, `cart_promo_error`, `order_partly_returned`, `product_questions` — the fixture tests hold them
  to the server's output), TC-ORD-06 and TC-ORD-08, research D4 and the KDoc that quotes them. The QA runs keep what
  was seen on the day.
- **The check**: `CurlyApostropheTest` fails on `'` between two letters in any literal under `BundledGlyphsTest`'s
  roots. The lexer moved out of `BundledGlyphsTest` into `theme/SourceStrings.kt`, which both read; it now also names
  the call a literal is written into (the name before the innermost bracket; a trailing lambda takes its call's name)
  and keeps a template as one letter, so «${name}'s» still counts. Left out: the `seed/` package (reviews, questions,
  answers, product names, as their authors wrote them) and text for the developer — a `@Suppress` reason, a
  `require`/`check`/`error` message, an exception's, a log line's, `ofPattern`'s `'T'`. A string for the developer
  put in a variable first and thrown later is not told apart; none is today.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: `ReorderAnswer` back to
  «isn't» (the check names the line); the seed no longer left out (the seed's reviews listed); the developer's text
  no longer left out (both tests: suppression reasons, `require`, exceptions, the date pattern); a template standing as
  a space (the control loses «${…}'s list»).
- **Goldens**: `viddikRecord --force` in a copy outside the mutagen replica on Linux: 13 changed, each the apostrophe
  alone — `Cart_Content` (and `_1024`, `_Phone`), `Cart_ItemChanged`, `Cart_PromoApplied`, `Cart_PromoError` (each
  with `_Phone`), `Order_PartlyReturned` (and `_Phone`), `Product_QuestionDialog` (and `_Phone`); no line re-broke.
