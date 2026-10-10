---
id: B-105
title: "client: a page opened directly knows who is signed in"
status: open
priority: P2
size: S
stage: stage-11-qa
---

# B-105 — client: a page opened directly knows who is signed in

Found in the QA pass of 2026-10-10 (v0.3.0), signed-in walk (phone). Reloading `/account/orders/HL-99999`, `/p/p-does-not-exist` or `/c/no-such-category` as
Maya draws the guest header («Sign in», no cart count); «Sign in» goes to the sign-in page, which sees she is signed in
and sends her back. B-67's `SHELL_HEADER` is the guest's; its category words all lead to `/c`.

- AC: before any tree, the shell's header reflects the session (account slot, a known cart count or none); category
  words lead to their category once the client knows them; tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Shell.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`.
