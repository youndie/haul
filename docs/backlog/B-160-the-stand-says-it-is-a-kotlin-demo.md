---
id: B-160
title: "feat: the stand says it is a demo built with Kotlin, and links to its source"
status: done
priority: P1
size: S
stage: stage-15-showcase
---

# B-160 — feat: the stand says it is a demo built with Kotlin, and links to its source

Four reviews of the stand (2026-11-01: an architect, a product manager, a designer, an ordinary shopper) agreed on
one thing first: nothing on haul.kotlin.website says it is a demo or what it is built with. The shopper read it as a
real store that cannot be trusted — drawn products, odd names, no owner, a sign-in page on another domain — and said
the strangeness would read as a convention the moment the page said «demo». A developer who lands on it never learns
it is Kotlin end to end, or where the code is.

- **One quiet line on every page, and an About.** A slim strip above the header — «A demo store built with Kotlin:
  Ktor, kompot, Compose Multiplatform in the browser · About this demo · Source on GitHub» — that a visitor can close
  (remembered for the session), and an «About this demo» page: what the stack is, what is real (the saga, the
  simulators, the observability) and what is not (no money moves, no parcel ships, the products are invented), and
  links to the repository and its README. Both are server trees like every page, so the copy is the server's.
- **The strip is a tree of its own, not part of the header** (owner, 2026-11-01). In `HaulHeader` it would have moved
  nearly every golden and every client fixture body, and pushed the canvas references past their tolerance. The server
  serves it alone (`GET /ui/demo`, `DemoNote`); the storefront's root asks for it once the first page has drawn, so the
  first load waits for nothing more, and draws it above whatever page is shown. Views drawn outside the storefront —
  the goldens, the fixtures, the shell's own tests — have none, so no existing golden moved.
- **The About page is reached from the strip and by its address, not from the footer** (owner, 2026-11-01; the item
  said «from the footer»). A word in the footer would move every footer golden, and the strip already leads to the
  page from every page. The footer is left as it is.
- Rejected: a banner over the hero (the store stops looking like a store, which is the point of the demo), a modal on
  arrival (one more thing between the visitor and the page).

- AC: every page shows the strip until it is closed, and a closed strip stays closed for the session; «Source on
  GitHub» opens the repository in a tab of its own; the About page is reachable from the strip and by its address (a
  reload of `/about` opens it); goldens of the strip and the About page at 1440 and 390 recorded and looked at; no
  existing golden changes.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/about/AboutRouting.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/about/screen/AboutScreen.kt`,
  `shared/src/commonMain/kotlin/io/github/youndie/haul/ui/AboutComponents.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/DemoStrip.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/AboutViews.kt`.

## Findings

- **What was built.** The contract has `DemoNote` and `AboutBody` (`haul_demo_note`, `haul_about`) and the page
  `StorefrontPage.About` (`/about`), so the server answers a reload of `/about` with the page as at every storefront
  address (B-36). The server answers `GET /ui/demo` with the strip and `GET /ui/about` with the frame — header, body,
  footer — in the public tier (`feature/about/`). The client's root (`App`) wraps the storefront in `DemoStripped`,
  which asks for the strip a frame after the first page — a tree, or a page the shell draws itself — has drawn, and
  draws it at the top of every page's scroll (`Scrolled`, `DemoStripAbove`); the line of a load on its way moves down
  by the strip's height. A strip that did not arrive is asked for again after the next page; a closed one is never
  asked for. The close is remembered in `sessionStorage` (`BrowserDemoNoteMemory`), in memory on the desktop.
- **External links are kompot's `open_url`.** kompot's standard set already has `OpenUrlAction`, which the contract's
  wire carries, so no Haul action was added. The shell follows it through `LinkCopier.openUrl` — `window.open(url,
  '_blank', 'noopener')` in the browser, as «Open in new tab» does — and the right button over such a link offers
  «Open in new tab» and «Copy link» with the whole address.
- **`Main.kt` still calls `App(...)` once with plain arguments** (`demoNote = demoNote`, built the line before), so
  `measure-first-load.sh`'s wired arm and `WiredArmTest` apply as they did.
- **Two goldens drift on main, not here** (as B-158 found). A forced recording on the WSL box differs from the
  committed `Search_Results` (901 px) and `Search_Autocomplete` (188 px) in rows 380–411; a forced recording of
  `origin/main` on the same box gives the same bytes as this branch's, and `viddikVerify` accepts both. Left as they
  are; every other golden is byte-identical, and the four new ones are `Demo_Strip`, `Demo_Strip_Phone`,
  `About_Content`, `About_Content_Phone`.
- **Walked in a browser** (2026-11-01, the image built from this branch on the WSL box, headless Chromium over CDP):
  `/` asked for `/ui/home`, then `/ui/demo`; the strip drew above the header at 1440 and wrapped over two lines at
  390; «×» hid it and wrote `haul.demo-note` to `sessionStorage`, and a reload of `/about` drew the page without it
  and asked no `/ui/demo`; with the storage cleared it came back; «Source on GitHub» opened
  `https://github.com/youndie/haul` in a second tab with the store's tab still on `/`; «About this demo» opened
  `/about`.
