---
id: B-112
title: "client: the first press on a text field focuses it"
status: dropped
priority: P1
size: M
stage: stage-11-qa
---

# B-112 — client: the first press on a text field focuses it

Found by B-83 while walking a local stand in a browser: on a freshly loaded page the first press on a text field
often focuses nothing — no caret, and Compose creates no hidden input — in 6 of 9 fresh loads. A second press on the
field works, and so does any field once a button was pressed first. Keys typed after the failed press go nowhere. This
matches the lost keystrokes B-69 saw and the review title the QA pass (B-91…B-110) lost. Load time is ruled out (10 s
waits failed too), and so is the canvas's DOM focus. The cause is somewhere in Compose's web text input (Compose UI
1.12.1).

- **Decision:** find the cause and a workaround in haul (the stack stays on Compose 1.12.x until Kotlin 2.5.20); no
  report upstream — record what was found in the findings.
- AC: on a fresh load, the first press on the search field, a price field, the promo code and a dialog's text box
  focuses it and the keys typed next land, in a real browser (headless Chrome on the Linux box is fine) — a
  repeatable check (a script or a browser test) red before and green after, run at least ten fresh loads each.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/TypedInput.kt`,
  `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt`.

## Findings (2026-10-11)

- **The check**: `scripts/first-press-check.py` (standard library, B-28's DevTools client) drives a fresh headless
  Chromium per load on the Linux box: it waits until the field is in the tree Compose mirrors into the DOM (the
  test tag is the node's id) and the static frame is gone, presses the field's centre, reads whether Compose made
  its hidden input (`.compose-backing-field`) and gave it the DOM focus, types `123` and reads the field's text
  back from the same node. Options put the page in each of the pane's conditions (below). Its control can fail:
  the press 300 px left of the field (`--offset 300`) — 0 of 9 focused, 0 landed.
- **In a page that draws, the first press focuses every time.** Against this branch's server (`installDist` with
  the bundle of `ee428b1f`, beside a seeded PostgreSQL on the WSL box), fresh load, the field pressed 1 s after it
  is laid out — the header's search field on the home page, the price «from» on `/c/electronics`, the cart's promo
  code with a guest cart of one line:

  | condition | search | price | promo |
  |---|---|---|---|
  | as is (move, press, release 50 ms later) | 10/10, 10/10 | 10/10, 10/10 | 10/10, 10/10 |
  | the pane's click (no move, released after 10 ms) in a window without the focus | 10/10, 10/10 | 10/10, 10/10 | 10/10, 10/10 |
  | animation frames held back to one a second (what B-83 measured for the hidden pane) | 10/10, 10/10 | 10/10, 10/10 | 10/10, 10/10 |
  | a background tab, drawn only by a screenshot each second | 10/10, 0/10 | 10/10, 0/10 | — |

  (focused, landed per field). In the background tab the keys never land because the tab runs no animation frame,
  and Compose applies typed input at the next one (B-76's mechanism) — the field holds the focus throughout.
  No condition made the first press fail to focus; no workaround was added, so there is no «red before».
- **In the app's browser pane (hidden, 1024×768 and 1440×900)** the price «from» pressed blind at its place 5–8 s
  after a fresh load: focused in 9 of 10 loads. The one that failed (no hidden input, the canvas focused) was
  pressed while the page was still its skeleton — a screenshot taken just before showed the loading state, and
  the field's node was not in the tree yet: a hidden pane runs few animation frames (from 1 in 2 s to 17 in 5 s,
  measured), and Compose composes the arrived screen only in one, so the press landed on the skeleton.
  B-83's description fits the same: the header's category chips are drawn in the skeleton (a chip pressed first
  navigates), the press and its screenshot make the pane draw (a second press works), and waiting does not help
  a pane that draws nothing.
- **Under frames held back five seconds, keys typed into the search field right after its first frame are lost**
  (5 of 5 loads; focused in all), while typed one frame later they land (3 of 3), and the price field keeps them
  (2 of 2, applied a frame late). Cause not found; no browser that draws gives a field less than a frame of age
  at the first keystroke.
- **Not checked**: a dialog's text box — the review and question dialogs are a customer's, so the check would need
  a signed-in browser (shildik), and the dialog's field is never the first press on its page.

## Question (2026-10-11)

The item's premise — a Compose web text-input defect on a fresh load — did not reproduce in a browser that draws
(the table above); what failed was a press on a page the hidden pane had not drawn yet. The owner decides:

1. **Close the item** as not a client defect: the failure belongs to walking the stand in a hidden pane. Agents
   walking the pane press a field only after a screenshot shows it (or after the field's node is in the mirrored
   tree), and `scripts/first-press-check.py` stays as the check for the next report.
2. **Make a hidden page draw** — a fallback that runs Compose's frames from a timer while
   `document.visibilityState` is hidden. The pane would behave like a visible tab, at the cost of a shopper's
   background tab drawing (the deals' countdowns recompose every second) — the battery the browser throttles to
   save.
3. **Take the search field's five-second case** as its own item, if a field that loses keys typed in its first
   frame matters outside the pane.

## Decision (2026-10-12)

The owner closed the item (option 1): the first press did not fail in a browser that draws, only in a hidden pane on a
page it had not drawn yet — a walker's condition, not a client defect. Walkers in the pane press a field only once a
screenshot or the mirrored tree shows it; `scripts/first-press-check.py` stays as the check for the next report.
