---
id: B-178
title: "feat: the desktop client signs in through the system browser"
status: wip
priority: P3
size: M
stage: stage-15-showcase
---

# B-178 — feat: the desktop client signs in through the system browser

B-176 left the desktop app a guest's: sign-in needs the stand's provider to accept an app's loopback return address,
which shildik does from `0cbc2c7`, released as 0.5.0. The owner chose sign-in (2026-11-01, B-176's question, (a)).

- The chart moves to shildik 0.5.0 and registers the loopback address (`http://127.0.0.1/<path>`, any port) — on
  `haul-web` or as a second public client for the desktop alone, whichever the research says why;
  `kotlin-multiplatform-oidc`'s JVM `oidc-appsupport` flow (system browser, a listener on a loopback port) is wired into
  `DesktopIdentity.kt`: sign-in, renewal, sign-out, and the demo shopper's token renewed like a signed-in person's.

- AC: the desktop app signs Maya in through the system browser against a composed stack (and, after the deploy, the
  stand), shows her account and cart, renews the token past its lifetime and signs out; the browser's sign-in is
  unchanged (e2e green).
- Anchors: `composeApp/src/desktopMain/kotlin/io/github/youndie/haul/feature/identity/DesktopIdentity.kt`,
  `charts/haul/files/shildik-bootstrap.py`, `charts/haul/values.yaml`.
