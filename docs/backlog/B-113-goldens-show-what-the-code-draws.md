---
id: B-113
title: "test: every golden shows what the code draws now"
status: done
priority: P3
size: XS
stage: stage-11-qa
---

# B-113 — test: every golden shows what the code draws now

Found by B-84: 24 goldens on main show what the code no longer draws — 18 the old «Search 2.4 million products»
placeholder (B-107), 6 the display face's quotation marks before B-106's cut. `viddikVerify` accepts them because
each difference is under its tolerance (about 0.05 % of a page), and `viddikRecord` without `--force` keeps any
golden that verification accepts, which is how B-107's re-record left them behind.

- AC: a forced re-record on Linux (outside the mutagen replica) changes exactly the stale goldens and nothing else
  — list them and look at each; `CLAUDE.md`'s «Screenshot goldens» section says to record with `--force` after a
  change of copy, glyphs or fonts; `viddikVerify` green.
- Anchors: `composeApp/src/desktopTest/snapshots`, `CLAUDE.md`.

## Findings (2026-10-11)

- **A forced recording changes exactly the 24 B-84 named, and nothing else.** On base `6e34dfe6` (B-108 merged),
  in a copy of the replica outside mutagen (`/tmp/b113-copy`, a throwaway git repository for the diff), on the
  Linux build box: `viddikRecord --force` rewrote all 166 goldens (two forks, 83 each, «kept 0»); 142 came out
  byte-identical, 24 differ, no other file changed and the 125 design references were untouched. Each of the 24
  was compared pixel by pixel against main's with a throwaway `ImageIO` tool and its changed box looked at, old
  above new:
  - **The search placeholder** «Search 2.4 million products» → «Search products» (B-107) in the header's field, the
    only pixels that move (1 125 px, ≈0.04–0.05 % of the page, one box at the field): `Home_Loading`,
    `Account_Content`, `Catalog_Content`, `_1024`, `Catalog_Empty`, `Catalog_Loading`, `Product_Description_1024`,
    `Product_Description_SpecFacts`, `Product_Questions`, `Product_Reviews`, `Product_Reviews_Mine`,
    `Product_Specifications`, `Saved_Content`, `Saved_Loading`, `Product_ReviewDialog` (under the scrim), and the
    phone dialogs whose header shows under the sheet's top: `Order_ReturnDialog_Phone`,
    `Product_QuestionDialog_Phone`, `Product_ReviewDialog_Phone` — 18.
  - **The display face's quotation marks and apostrophe** (B-106): main's goldens draw them with hairline tails
    (the cut face before B-106), the recording with full ones — `Search_NoResults`, `_Phone` (the quotes around
    the query), `Search_Error`, `_Phone`, `Checkout_Error`, `_Phone` (the apostrophe in the title) — 6.
- **`CLAUDE.md` «Screenshot goldens»** now says to record with `--force` after a change of copy, glyphs or fonts,
  why the verification does not catch it, and to record outside the replica and look at each changed PNG.
- **Checks.** `viddikVerify` on the replica with the new goldens: 168 tests (2 × 84), 0 failures, results written
  by that run. `check` on the Linux box; `LOCAL=1 make check` and `make docs-against BASE=origin/main` on the Mac.
