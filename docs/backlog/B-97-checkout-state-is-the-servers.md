---
id: B-97
title: "client + server: what checkout shows is what it will place"
status: wip
priority: P1
size: M
stage: stage-11-qa
---

# B-97 — client + server: what checkout shows is what it will place

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk at 1366:
- the address form is sent only when focus leaves the form or on Enter (`composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt:391-397`);
  typing the address and clicking the greyed «Place order» (or blank space) sends nothing, and the button keeps
  saying «Fill in the delivery address»;
- picking a delivery day is drawn only on the client: after Sun 11 12:00–15:00, clicking «Thu 15» sent no request,
  highlighted Thu with no slot, kept «Place order» enabled, while the server held Sun 11 and the summary said
  «Delivery · Sun, Oct 11».

- AC: leaving the address by any press sends it (B-83's `awaitTypedInput` where a press reads it); choosing a day either
  asks the server or disables «Place order» until a slot of that day is picked; the summary always shows the server's
  choice; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/feature/checkout/CheckoutViews.kt`, `server/src/main/kotlin/io/github/youndie/haul/feature/checkout/screen/CheckoutScreen.kt`.
