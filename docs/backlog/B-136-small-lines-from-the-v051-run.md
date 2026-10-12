---
id: B-136
title: "fix: the small lines the v0.5.1 run noticed"
status: done
priority: P3
size: S
stage: stage-12-qa-run
---

# B-136 — fix: the small lines the v0.5.1 run noticed

Found outside the catalogue by its v0.5.1 run ([run](../qa/runs/2026-10-12-v0.5.1.md)), each small:

- At checkout, «Couldn't reach the store» with «Retry» goes away by itself after about six seconds, so a connection that
  comes back later has no Retry («Place order» still presses, nothing is lost). A failure message with an action stays
  until it is answered or the page changes.
- The return dialog's line tiles have no photo, while the same lines on the order page have one.
- On a cold load at 1024 the static frame draws the wide header and a four-across placeholder, then the page jumps to
  1024's layout (the frame's own CSS does not know the 1024 band).
- On the 390 deals row the old price «$277» touches the card's «+».
- After the cart is emptied, the phone cart button's mirror node keeps a stale «3» though its name «Cart» is right.
- «Couldn't copy the link» has a straight apostrophe where the store writes ’.

- AC: each behaves as its line says, or the line is recorded as decided otherwise with its reason; a test per line where
  it is code's logic.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `composeApp/src/wasmJsMain/resources/index.html`.

## Findings (2026-10-12)

- **«Couldn't reach the store» with «Retry»** (`shell/Messages.kt`). The six seconds were B-75's for every message
  with a button («Added to your cart» with «View cart» included), not a decision about failures; B-92 took them over
  and noted that a «Retry» outlives its page. A message at the `error` level with a button now has no time
  (`MessageState.Shown.untilAnswered`): it stays until its button is pressed, another message replaces it, or a page
  is visited (`MessageState.visited`, from the storefront's visit effect beside the panel and the menu). A timed
  message still runs out its time over the next page, as B-75 has it; a failure without a button (a `POST` that adds)
  keeps its four seconds. Research §B-75 and TC-ERR-08 say so. Test: `CartWiringTest` «in the storefront no answer
  with Retry stays until the page changes» (a minute later still there; back → gone).
- **Return dialog tiles.** `ReturnLine` had no photo (B-108 left it out of its list, with no reason beyond the list).
  It carries `image` now (optional, last), the server fills it as the order's own line does, and the dialog draws
  `LineTile`. Tests: `PhotoRoutesTest` (the B-108 test now delivers the order and reads the dialog's line), `LinePhotosTest`
  «the return dialog's lines draw their photos».
- **The cold frame at 1024.** The static frame's CSS does know the 1024 band (B-82's `min-width: 1024px and
  max-width: 1279.98px` block): opened alone in a browser at 1024×768 it draws the search field on its own row, as
  the app's header does. What jumped was the app's own placeholders: their card grids were fixed at the 1440 counts
  (four beside a category's or the Saved list's facets, five on a search, six in a deals row) where the page's grid
  takes only the cards of 168 px that fit (`columnsIn`) — three, four. The placeholders take the same rule now
  (`CardGrid` in `shell/Shell.kt`); at 1440 nothing changes. `index.html` is untouched. Whether the run's «wide header»
  was the frame at another width is not known; at 1024 the frame and the app's header agree. Test: `LoadingCardsTest`
  (home, category, deals, search, Saved at 1024, 1280 and 1440: no card placeholder under 168 px).
- **The 390 deals card.** «$193.90» and «$277» filled the 124 px left of a 160 px card's «+» to within 4 px: they fit
  side by side and the old price touched the button. The prices keep `PRICE_GAP` (8 px) from «+» as from each other,
  so at 390 «$277» goes under the price, as B-108 does for a price that does not fit. Test: `WidthsTest` «a phone's
  deal card keeps its old price off the plus». No golden moved.
- **The phone cart button's stale «3».** The button is pressable, so it merged its badge's text: the mirror node had
  the name «Cart 3» and the text «3», and when the cart was emptied the name became «Cart» while the browser's mirror
  kept the text. The phone's button is its name alone now (`clearAndSetSemantics`); the wide button, which draws the
  word «Cart» itself, is as it was. Not re-walked in a browser: the desktop test reads the merged node, whose text is
  what the mirror drew. Test: `HeaderCountTest` «on a phone the cart button reads its name and nothing else».
- **«Couldn’t copy the link»** now writes ’ (`COPY_REFUSED`); copy, not logic, so no test of its own beyond
  `ProductPageLinksTest` reading the constant and golden `Product_ShareRefused`, recorded again. Two more of the
  store's own sentences still write a straight apostrophe — `ReorderAnswer` («isn't / aren't available now») and
  `OrderScreen.returnRule` («can't be sent back») — and the seeded reviews (shoppers' words) do too; they reach tests,
  fixtures and the QA catalogue, so they are left for an item of their own.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: a failure with Retry timed again
  (`CartWiringTest`), the visit no longer taking it down (`CartWiringTest`), the server's `image` on a return line
  dropped (`PhotoRoutesTest`), the dialog drawing the tone tile (`LinePhotosTest`), the placeholders' fixed columns
  (`LoadingCardsTest`), the prices without their gap (`WidthsTest`), the phone button's `named` back (`HeaderCountTest`
  — green at first, because the test's header had no cart action and so merged nothing; the test presses it now).
- **Goldens**: `viddikRecord --force` in a copy outside the mutagen replica on Linux: only `Product_ShareRefused`
  changed (the apostrophe).
