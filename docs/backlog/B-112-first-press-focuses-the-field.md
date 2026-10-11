---
id: B-112
title: "client: the first press on a text field focuses it"
status: wip
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
