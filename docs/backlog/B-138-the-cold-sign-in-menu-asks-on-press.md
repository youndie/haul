---
id: B-138
title: "client: the cold sign-in page's menu and picker ask for the header when pressed"
status: done
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

## Findings

- **How a press asks** — the sign-in page's branch of `Storefront` provides `LocalAskForHeader`
  (`ui/HaulHeaderView.kt`), the lambda `askForHeaderOnPress` (`shell/ShellHeaders.kt`) makes: the first call sends
  `GET /ui/header` (the same `HaulTransport.header()` B-105's `AskForHeader` uses) and draws the answer into
  `ShellHeaders` for who is looking; every call after it in the same visit sends nothing, answered or not. Once a
  header is known for who is looking the local is `null`, so a sign-in page reached from a page with a tree asks
  nothing at all. Every other page provides none, so nothing else changed.
- **The phone menu opens at once**, with what the client has, and the press also asks: the menu's top half —
  the sign-in, «Orders», «Saved», «Deals», «HAUL PLUS» — is usable while the header is on its way, and «Catalog»
  becomes the server's 32 with their addresses when it arrives (the open sheet follows the header it is drawn
  from). Holding the menu shut until the answer would make the button look dead on a slow network.
- **The picker opens when the scopes arrive.** It has nothing to offer before them, so `Menu` takes an `ask`: a
  press with no entries to press asks, and the menu opens by itself once entries are there. A header that does
  not arrive leaves the picker shut, as on any page whose header alone failed (research, B-90).
- **Left as it was, not in the decision:** at 1440 the cold sign-in page's «Catalog» menu and its category row
  still lead each word to `/c` until something asks — a press on the picker brings the server's header, and they
  follow it. The owner's decision names the phone menu and the picker; widening it to «Catalog» is a one-line
  `ask` on its `LinkMenu`, if wanted.
- **Docs** — research: B-105's paragraph (the sign-in page asks on press since B-138) and the picker's
  paragraph under the search; QA `TC-SIGN-05` gains step 7 and its coverage. B-133's finding is left as the record
  of what was decided then.
- **Tests** (`ColdSignInHeaderTest`, desktopTest): no request while the page loads, at 1440 and at 390; the phone
  menu pressed asks once, lists the 32 (none of the client's own words) and one pressed opens its address; the
  picker pressed asks once, offers the 32 scopes, and the search Enter runs is in the scope chosen; the menu
  pressed four times while the header is held back and twice after makes one request; a header that fails is not
  asked for again by three more presses, and the picker stays shut.
- **Mutations**, each red then restored (`git status` clean): the local never provided (four red), the
  once-per-visit guard removed (the two count tests), the page asking on its load (the no-request test, `[/ui/header]`),
  the picker not opening when the scopes arrive (the picker test), the phone button not asking (the menu and count
  tests).
- **Goldens** — none moved: screenshots provide no `LocalAskForHeader`, and a press handler draws nothing.
