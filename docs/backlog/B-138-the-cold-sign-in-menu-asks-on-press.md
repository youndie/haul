---
id: B-138
title: "client: the cold sign-in page's menu and picker ask for the header when pressed"
status: open
priority: P3
size: S
stage: stage-12-qa-run
---

# B-138 — client: the cold sign-in page's menu and picker ask for the header when pressed

Found by the v0.5.0 run and recorded as decided by B-133 ([run](../qa/runs/2026-10-12-v0.5.0.md)): `/sign-in` landed
on cold makes no server request (B-66, B-105), so its phone menu lists the client's own ten row words, each leading
to the catalog's root, instead of the 32 categories, and the scope picker presses nothing.

- **Decided (owner, 2026-10-12): ask on press.** The page still asks nothing while it loads; a press on the phone
  menu or on the scope picker asks for the header alone (`/ui/header`, as B-105's failed pages do) and opens with its
  32 categories and their scopes. Rejected: copying the 32 into the client (research D2: categories are the server's).
- AC: on a cold `/sign-in`, no request until a press; the phone menu pressed lists the 32 with their addresses, the
  picker pressed offers the scopes; one header request per page however often they are pressed; client tests.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPage.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/ShellHeaders.kt`.
