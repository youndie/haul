---
id: B-163
title: "feat: «Under the hood» shows the screen's kompot tree, what it cost and what updated"
status: done
priority: P1
size: M
stage: stage-15-showcase
blocked_by: [B-160]
---

# B-163 — feat: «Under the hood» shows the screen's kompot tree, what it cost and what updated

Every page of the store is a kompot tree the server sends and the browser draws with Compose (research D1), and
nothing on the page shows it: from outside it looks like any web shop. The product and the designer reviews
(2026-11-01) both put a «show the machinery» layer at the top of their lists — it is the one thing a developer
would come to a showcase for.

- **A toggle in the demo strip (B-160) opens a side panel** over the page: the tree of the screen on view as an
  outline (component types and ids, expandable to their fields), the request that brought it (path, status, size,
  server time, from the response headers), and — while the panel is open — a flash on the parts of the page a
  partial update (`update` / `load`, B-63) replaced, with the update listed in the panel. A link from a component to
  its renderer and its server tree builder in the repository.
- The panel is a client feature that reads what the client already has (the tree it decoded, the responses it
  made); the server adds only a `Server-Timing` header. It is off by default and costs nothing when closed.
- Rejected: a separate «developer page» (nobody finds it), raw JSON only (an outline reads; a JSON dump is a
  scroll).

- AC: on the home, a product and the cart, the panel shows the screen's tree and its request; adding to the cart
  with the panel open flashes the badge and the card's part and lists the update; closing the panel leaves the page
  pixel-identical to before; screenshots of the panel recorded.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/App.kt`.

## Findings

- **What was built.** The strip's tree names the toggle (`DemoNote.hood`, «Under the hood»); `DemoNoteView` draws it as
  a pill after the links — on a phone on a line of its own — only when the root hands it a press, so `Demo_Strip` and
  `Demo_Strip_Phone` did not move. `App` makes the `UnderTheHood` and wraps the transport (`recording`) and the cart's
  commands (`watching`); `DemoStripped` draws the panel over the storefront and fades the flashes (`HoodFlashes`);
  `Storefront` hands it the tree drawn (`shown`, `noTree` for the shell's own pages), each `load`'s answer and each
  update about to be written (`watchedBy`, above `withUpdates`), and draws every node through `flashedBy`. The panel
  (`UnderTheHoodPanel`) is a sheet from the right at 1440 and a bottom sheet at 390. The outline is read from the
  wire's JSON (`HoodOutline`), the links from a table (`HoodSources`). The server installs `TreeTiming`:
  `Server-Timing: app;dur=<ms>` on every answer under `/ui/`. What was decided and why is in the research, «Decided in
  B-163».
- **The transport keeps the last 20 requests' numbers always**, no body: the panel is opened over a page already
  drawn, and kept only while open it could not name what brought it. Decided and recorded in the research.
- **A component in a one-type list has no `type` on the wire** (a grid's `cards`, a category grid's tiles): the
  outline names it by its field, «cards[]», and links it nowhere; the first outline found only the 8 top-level nodes
  of the home page because of it.
- **At 1440 the panel covers the header's right end, the cart's count among it.** The update «+» answers replaces the
  whole header, whose flash runs along its width, so it shows left of the panel; the count itself is seen once the
  panel closes. Left as it is: a panel that pushed the page would change the layout the visitor came to look at.
- **Not shown:** the order page's and «Live orders»' (B-164) live moves go through kompot's realtime provider, not the screen's actions —
  no flash, no line; the checkout's and the dialogs' commands answer `refresh`, a whole tree.
- **Mutation checks** (each made on the committed tree, run on the WSL box, then restored with `git checkout`):
  listing updates while closed → «closed nothing is listed and nothing flashes» failed; `close` that left the panel
  open → «the toggle … closes it» and «closing the panel leaves the page pixel for pixel» failed; no flash written →
  «an update while the panel is open … flash» failed (`glow` 0); a 1 dp padding in the flash's box →
  `FlashedRegistryTest` failed on `home_content.json` at 1440; `watchedBy(null)` → the three update tests failed;
  `TreeTiming` not installed → `server:TreeTimingTest` failed. Gradle's test daemon died after `FlashedRegistryTest`
  failed in two of those runs («Could not receive a message from the daemon»), after the failure was reported; the
  green runs never did.
- **Walked in a browser** (2026-11-01, the image built from this branch on the WSL box, seeded, headless Chromium
  over CDP, `/tmp/b163-walk.py`): the home's `/ui/home` answered `Server-Timing: app;dur=9.8`, which the browser's
  resource timing read as well; the panel said «200 · 10.6 kB · server 9.8 ms · total 33 ms» and asked the server for
  nothing on opening; «+» on the Sonora deal flashed the card (screenshot) and listed «PUT
  /api/v1/cart/lines/p-sonora-sh-718n-0?answer=card — replaced header, card-p-sonora-sh-718n»; «Northwind» on
  `/c/electronics` flashed the title, the kinds and the results and listed «GET
  /ui/parts/c/electronics?brand=Northwind — replaced title, kinds, results · server 8.7 ms»; the product and the cart
  showed `GET /ui/p/p-sonora-sh-718n` and `GET /ui/cart` with their numbers, at 1440 and 390; the panel's «×» closed
  it. `/api/v1/sign-in` answered without the header.
- **First load** (`scripts/measure-first-load.sh`, the `main` arm, 7 rounds, no throttling and Slow 4G, on the WSL
  box; this branch against a clone of `origin/main` at `2f1b32b`): the app's `.wasm` grew from 5,826 to 5,901 kB
  raw, 1,300 to 1,313 kB brotli (+1 %); the first frame's median went from 627 to 666 ms unthrottled (ranges
  582–805 and 564–878, overlapping) and from 26,496 to 26,515 ms on Slow 4G — the 13 kB at that bandwidth. The first
  page asks for nothing more: `/ui/home`, then the strip's `/ui/demo`, as before, and opening the panel asks for
  nothing. `Main.kt` still calls `App(...)` once with plain arguments; `WiredArmTest` passes.

