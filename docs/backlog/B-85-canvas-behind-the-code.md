---
id: B-85
title: "design: the code is the reference; parity compares only the artboards that still describe the store"
status: done
priority: P3
size: M
stage: stage-10-review
---

# B-85 — design: the code is the reference; parity compares only the artboards that still describe the store

The client moved past the canvas in five places, each decided and recorded in research §2: the phone header's
menu button and menu (B-73), the strip «Delivering to … · Free delivery over $35» and the shorter footer (B-72),
the search sort and the no-results tips as text (B-77), the message frame and «N in your cart» (B-75); since B-133
also Order_InTransit's shipment line, which keeps tomorrow's date («TOMORROW, OCT 8 · 15:00 – 18:00»). Parity
reads four short phone artboards just over 5 % (Catalog_Empty_Phone 5.19, Product_NotFound_Phone 5.38,
Order_Placed_Phone 5.12, Account_NoOrders_Phone 5.15) and the search artboards far off; the tolerance is
unchanged. Since B-78 every artboard with a placeholder tile is further off still: the store draws the seed's
pictures there.

- **Decided (owner, 2026-10-13; replaces the decision of 2026-10-12 that Claude updates the canvas):** nothing here
  can edit the Claude Design canvas, so **the code is the source of truth and the canvas is not caught up.**
  Artboards the code has moved past on purpose leave the parity comparison — the mechanism chosen by the
  viddik / design-to-compose conventions of this repository — so `viddikDesignParity` reports only artboards that
  still describe the store; the goldens (`viddikVerify`) stay the regression gate.
- AC: the departed artboards (research §2's departures, and since B-78 every artboard whose placeholder tiles are
  now the seed's pictures) leave the comparison; the references that still match are kept, and the remaining
  parity table is reported; research §1.6 / §2, the design-related documents, `CLAUDE.md` and the QA cases no
  longer treat the canvas as the acceptance criterion.
- Anchors: `composeApp/src/desktopTest/snapshots/design/`, `composeApp/src/desktopTest/snapshots/design/retired/`,
  `composeApp/src/desktopTest/snapshots/design/manifest.json`.

## Done (2026-10-13)

Research §1.6 has «Decided in B-85»; `backlog.md`'s decision «a client item is accepted against the canvas» is
replaced by «the code is the reference; the canvas is a historical one».

- **The mechanism: a retired reference moves to `snapshots/design/retired/`.** viddik reads a reference at
  `design/<stem>.png` only (`ViddikEngine.designParity`: `File(designDir, fileName)`, viddik 0.7.0) and has no list
  of exclusions, so a fixture whose PNG moved reads `no ref`, like the fixtures the canvas never drew.
  `design/manifest.json` keeps all 125 artboards; each retired entry points at `retired/<stem>.png` and carries a
  `retired` line naming the decisions that moved the store past it. The PNGs stay in the tree beside their reason.
  There are no screen documents with `design:` blocks in this repository, so nothing else lists the references.
- **Retired, 76**:
  - the 70 artboards B-78's pull request (#173) measured moving with the pictures, the 14 it took past 5 % and the 56
    it left within — each draws a striped, captioned tile where the store draws a picture, so a number within 5 %
    means only that the tile is small. On the search's pages B-77's sort and tips are named with it, on the
    product page B-71's gallery (no counter, no dots, no invented thumbnails) and seller card (no link);
  - `Product_ReviewDialog_Phone` (11.07 %) and `Product_QuestionDialog_Phone` (13.64 %): the dialog sits over the
    product page, which is B-71's and, under the review dialog, B-86's bars; their 1440 twins are among the 70;
  - four short phone pages the header's departures take over 5 % — B-73's menu button, B-72's strip, B-107's
    «Search products», B-75's empty cart button: `Catalog_Empty_Phone` 5.38, `Product_NotFound_Phone` 5.62,
    `Account_NoOrders_Phone` 5.37, `Account_Error_Phone` 5.00 %. Each `_DIFF` read: the header and the strip, the
    text residual, nothing else (Product_NotFound_Phone also breaks its title one word differently, the
    `text-wrap: balance` residual research «Found in B-10» names).
  - B-75's message frame and «N in your cart» and B-133's shipment date retire nothing of their own:
    `Shell_Message*` and `Product_InCart*` never had an artboard, and `Order_InTransit*` are among the 70.
- **Kept, 49, every one within 5 %** (`viddikDesignParity`, Linux box, this branch): 49/49 within, 119 fixtures
  without a reference.

  | Artboard | % | Artboard | % | Artboard | % |
  |---|---:|---|---:|---|---:|
  | Account_Content | 1.44 | Catalog_Error_Phone | 4.84 | Order_NotFound_Phone | 4.42 |
  | Account_Content_Phone | 3.35 | Catalog_FiltersSheet_Phone | 4.66 | Product_Error | 1.95 |
  | Account_Error | 2.01 | Catalog_Loading | 0.62 | Product_Error_Phone | 4.86 |
  | Account_Loading | 1.08 | Catalog_Loading_Phone | 0.78 | Product_Loading | 1.28 |
  | Account_Loading_Phone | 1.37 | Checkout_Error | 0.95 | Product_Loading_Phone | 1.19 |
  | Account_NoOrders | 2.61 | Checkout_Error_Phone | 3.12 | Product_NotFound | 1.50 |
  | Account_NotMember | 1.86 | Checkout_Loading | 0.09 | Saved_Empty | 2.14 |
  | Account_NotMember_Phone | 3.58 | Checkout_Loading_Phone | 0.26 | Saved_Empty_Phone | 4.05 |
  | Account_Orders | 1.41 | Home_Error | 2.01 | Saved_Error | 2.15 |
  | Account_Orders_Phone | 3.38 | Home_Error_Phone | 4.64 | Saved_Error_Phone | 4.76 |
  | Cart_Error | 1.88 | Home_Loading | 0.50 | Saved_Loading | 0.66 |
  | Cart_Error_Phone | 4.85 | Home_Loading_Phone | 0.92 | Saved_Loading_Phone | 0.79 |
  | Cart_Loading | 1.06 | Order_Error | 1.97 | Search_Error | 1.94 |
  | Cart_Loading_Phone | 1.56 | Order_Error_Phone | 4.90 | Search_Error_Phone | 4.20 |
  | Catalog_Empty | 2.54 | Order_Loading | 0.91 | Search_Loading | 0.82 |
  | Catalog_Error | 2.02 | Order_Loading_Phone | 1.43 | Search_Loading_Phone | 0.88 |
  | | | Order_NotFound | 1.54 | | |

  Before this item the same run read 86/125 within, 39 over.
- **Documents.** Research §1.6 («Decided in B-85»: the canvas's role, the mechanism, what was retired and kept),
  §2's B-77 and B-73 paragraphs (the search artboards left the comparison; the canvas will not gain the menu),
  D8's B-78 paragraph, risk 1 («Settled in B-85»), «Found in B-84»; `backlog.md`'s decision; `CLAUDE.md`'s new
  «Design references» section (the convention for the next decided departure); `docs/qa/README.md` (an artboard is
  never an expected result) and the four cases that pointed at the canvas for one (TC-HDR's category row and footer,
  TC-SRCH's picker note, TC-CHK's refused placement now names the golden). Two fixture comments in
  `composeApp/src/desktopTest` (`ScreenFixtures.kt`, `PhotoFixtures.kt`, the latter stale since B-78). No product
  code and no golden changed.

## Findings (2026-10-13)

- **Five kept phone artboards sit within 0.2 points of the line**: `Order_Error_Phone` 4.90, `Product_Error_Phone`
  4.86, `Cart_Error_Phone` 4.85, `Catalog_Error_Phone` 4.84, `Saved_Error_Phone` 4.76 % — short pages where the
  header's departures weigh most, as on the four retired here. The next header change will likely take them over;
  by the rule in `CLAUDE.md` that retires them with it.
- **Two retired artboards were over 5 % before B-78 for reasons no item names with a number**: `Order_Returned_Phone`
  (8.03 % before the pictures) and `Order_Cancelled_Phone` (8.54 %, B-108's single «Back to cart» per B-108's
  findings). Both are among the 70 and retire with the pictures; their `_DIFF` shows the header and a block at the
  page's foot, not read further.

## Where the checks ran (2026-10-13)

On the Linux build box (WSL2, Ubuntu 24.04): `viddikDesignParity` on `main`'s tree (86/125 within) and on this branch
(49/49 within); `:composeApp:wasmJsBrowserDistribution` green; `check :server:installDist` green (server 531 tests,
client 446, 0 failed; `viddikVerify`, ktlint); `scripts/e2e.sh haul/server:b85` green (WholePathTest,
ShoppersConfigTest 6, SyntheticShopperTest 3). No golden changed, so none was recorded. On the Mac: `make check` and
`make docs-against BASE=origin/main` clean, every anchor found. No test was added, so there was nothing to mutate; the
before-run is the parity's control — the same task over the same fixtures read 39 artboards over with the
references in place.
