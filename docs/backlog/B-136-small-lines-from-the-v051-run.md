---
id: B-136
title: "fix: the small lines the v0.5.1 run noticed"
status: open
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
