---
id: B-92
title: "client: a command that fails or is refused tells the shopper"
status: done
priority: P1
size: M
stage: stage-11-qa
---

# B-92 — client: a command that fails or is refused tells the shopper

Found in the QA pass of 2026-10-10 (v0.3.0). With the server stopped, «Place order» and «Reorder» failed (`ERR_CONNECTION_RESET`) and the UI showed
nothing; the button went back to normal. A refused «Place order» (cart or quote changed, payment not allowed, out of
stock) becomes a `refresh` that throws the reason away (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommandsClient.kt:82-87`). The
same holds across presses: a failed page `load` shows «The page didn't update» with Retry (B-62); a cart, heart,
checkout or vote command with no answer shows nothing (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt:113-118`,
`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/TreeCommands.kt:129-138`); the review dialogs say «not sent»; the Plus trial dialog says nothing.

- **Decided as product owner:** one rule for every command — no answer means a message («Couldn't reach the store»
  with Retry for idempotent presses); a refusal shows its reason (the server's words) where the press was.
- AC: a test per command family (cart, saved, checkout choice/address/place, order reorder/return, reviews, Plus) for
  «no answer» and «refused»; the e2e unchanged.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/cart/CartCommands.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutCommandsClient.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/TreeCommands.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Messages.kt`.

## Done (2026-10-11)

- **The rule, in one place** (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/CommandAnswers.kt`, research
  «Decided in B-92»): `refusal(reason)` is `refresh` followed by B-75's message at the `error` level with the server's
  `ErrorBody.message` (a refusal with no body says `NOT_SENT`); `noAnswer(again)` is «Couldn't reach the store», with
  «Retry» when it is given the press. «Retry» is `PressAgain`, a client-only action — never on the wire — which the
  screen's handler (`Storefront.kt`, `Shown`) and the shell's own (`panelActions`, the dialog over a page the shell
  draws) run; the shell's handler also draws messages now. Each family's `run` takes the press as `again` and decides
  whether it may be sent twice: a `PUT` or a `DELETE` (a quantity, a tick, Remove, a promo code, the heart, a checkout
  choice, «Helpful») and «Place order», whose key makes a retry the same order (B-16) — the placement's «Retry» goes
  through the renderer's own press, so it is Placing again and keeps the key. A `POST` that adds — «Reorder», «Save for
  later», «OK» on a changed line, the address form, the dialogs — gets the message without «Retry».
- **Where the press was.** Read as: on the page the press was on, not lost to a `refresh`, and not a second time where
  the next tree already draws it. So a refusal the server stores and draws at the control says it there alone: a
  refused promo code (`promo_not_found`, `promo_expired`, `promo_not_applicable` on «Apply» — Cart_PromoError), an
  address form refused with `fields` (Checkout_Validation), and `slot_unavailable` on «Place order» (Checkout_PlaceError's
  notice). The review, question and return dialogs keep their own place for what went wrong: a refusal there was
  already drawn in the dialog; no answer now says «Couldn't reach the store» where it said «That didn't go through».
  The Plus trial's refusal (`already_member`) closes the dialog, draws the page again and says why; no answer keeps the
  dialog under the message, its «Start trial» the press again.
- **Before** (the defect): no answer to any command but a dialog's was `null` — nothing followed, nothing said; every
  refusal outside the dialogs was a bare `refresh` (`CheckoutCommands.run`, `CartCommands.run`, `TreeCommands.vote`,
  `startTrial`).
- **Tests** (`composeApp/src/desktopTest`): `CommandAnswersTest` (the rule); per family, «refused» and «no answer» —
  cart: `CartCommandsTest` (every command's Retry or none; a stored promo refusal vs `promo_already_applied`),
  `CartWiringTest` (a refused quantity; Delete selected with no answer, Retry resends; in the storefront the message,
  its Retry pressing again and the cart fetched again, and a refusal's words over the redrawn cart); saved:
  `SavedWiringTest` (a refused heart; a heart with no answer and its Retry; «Save for later» without Retry); checkout:
  `CheckoutCommandsTest`, `CheckoutWiringTest` (a refused choice; a refused form says it under its fields alone; a
  refused placement says why and the button is pressable again, a filled window says nothing more; no answer to a
  placement, whose Retry places under the same key; a choice's Retry, the address form's none); order: `OrderWiringTest`,
  `AccountWiringTest` (a refused reorder says why; no answer without Retry), `ReturnWiringTest` (no answer in the
  dialog; the late return was already covered); reviews: `ReviewWiringTest` (the dialog's words; «Helpful» with no
  answer, Retry resends, and a refused vote's words over the redrawn page), `TreeCommandsTest`; Plus:
  `PlusTrialWiringTest` (refused, no answer), `ShellHeaderTest` (no answer over a page the shell draws).
- **Mutation**, each on the committed change, each red, restored, `git status` clean: the refusal without its message
  (15 tests in every family), no answer silent (16), a stored promo refusal messaged, Retry for every cart command, for
  none, the checkout's tree-drawn refusals messaged, Retry for the address form, the placement without Retry, the shell
  dropping `PressAgain`, the shell's own handler dropping messages, the dialogs back to «not sent», the trial's refusal
  without words, the vote's refusal a bare `refresh`, the vote without Retry.
- **Goldens**: two new, no reference (no artboard draws a message): `Shell_Message_NoAnswer` (1440, the alert mark
  and «Retry») and `Shell_Message_Refused_Phone` (390, a placement's refusal wrapping on two lines). Recorded on Linux
  in a copy outside the mutagen replica; a full `viddikRecord` there changed no other golden.

## Findings (2026-10-11)

- **A «Retry» outlives its page.** The message is held above the pages (B-75) and stays six seconds; a «Retry» pressed
  after the shopper has gone to another page runs in the scope of a page that is gone, so it does nothing. Rare (the
  press and the leaving inside six seconds), and harmless — nothing is sent twice — but the button is dead then.
  Taking the message down on a visit would close it; not done here.
- **Some server sentences are not for a shopper**, and are now shown as they are: `line_not_found` «No line for
  «sku» in this cart», `review_not_found` «No review «id»», `order_not_found` «No order «HL-…» among yours». They reach
  the shopper only in a race (a second tab), but the owner's rule is «the server's words», so their wording is the
  server's to change.
- **«Clear» on recent searches** (`HaulCommands.answered`, the search panel) still leaves the panel as it was on no
  answer: it is not one of the AC's families, and the panel has no place for a message under the field.
