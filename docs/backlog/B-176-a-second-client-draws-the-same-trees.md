---
id: B-176
title: "feat: a second client — desktop — draws the same server trees"
status: open
priority: P3
size: L
stage: stage-15-showcase
---

# B-176 — feat: a second client — desktop — draws the same server trees

Server-driven UI's promise is one server and many clients, and haul shows one. `composeApp` already compiles for
desktop to draw the goldens, so a desktop app that browses the stand is the cheapest second client. The owner put it
at low priority (2026-11-01).

- A `jvm("desktop")` entry point that talks to the stand (sign-in through the system browser), packaged for the three
  desktop systems; what had to change on the server for a second client (nothing, ideally) recorded in the research.

- AC: the desktop app browses the stand, adds to the cart, and shows the same pages as the browser; the server
  changed only where the research says why.
- Anchors: `composeApp/build.gradle.kts`, `composeApp/src/desktopMain/`.
