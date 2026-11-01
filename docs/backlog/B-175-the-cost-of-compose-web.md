---
id: B-175
title: "docs: audit the pixel workarounds since B-85 and write down what Compose for Web cost"
status: done
priority: P3
size: M
stage: stage-15-showcase
---

# B-175 — docs: audit the pixel workarounds since B-85 and write down what Compose for Web cost

A good part of the client is workaround for matching the canvas to the pixel — cropped line boxes, clipped layers,
hand-measured glyphs — and since B-85 the canvas is no longer the reference, so some of them may have lost their
reason. The architect's review (2026-11-01) also noted that the list of what Compose Multiplatform 1.12 in the
browser made hard (text selection, cursors, focus, title, first load) is worth more to other developers than any one
fix.

- Each workaround is listed with its reason and whether it still holds; those that do not are removed in this item
  if the goldens stay within tolerance, or turned into items. The list of costs becomes a section of the research
  that the README links.

- AC: the research has the audit and the cost list; removed workarounds leave the goldens within `viddikVerify`.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/`, `docs/research/research-architecture.md`.

## Findings

- **Done as research §3, «The cost of Compose for Web 1.12» and «The client's workarounds for the canvas (B-175)»**;
  the README's «More» links the first. The cost list takes its first-load numbers from B-28, B-34, B-80, B-149 and
  B-150: B-173 («The stack in numbers»), merged while this item ran, measures the server and not the first load.
- **Measured, not argued.** On the WSL box, in a copy of the tree outside the mutagen replica, each canvas-only
  workaround was taken out by a patch and every golden recorded with `viddikRecord --force`, then compared pixel by
  pixel (a throwaway `PngDiff`, counting pixels that differ by more than 2 on a channel, as viddik 0.7.0 does) with
  the copy's own recording — which matched the 181 committed goldens exactly. `viddikVerify` refuses past 0.05 % of a
  golden; run on the first patch (`FlexPair` → two halves) it refused the same two goldens with the same counts
  (4,767 pixels each, 0.44 % and 1.43 %), so the counting is viddik's.

  | Taken out | Goldens changed | Past `viddikVerify` | Worst |
  |---|---:|---:|---:|
  | `FlexPair` | 2 | 2 | 1.43 % |
  | `InlineLine`'s strut | 14 | 14 | 9.69 % |
  | `subPixelSlack` | 1 | 1 | 0.22 % |
  | `CssColumn`'s fractions (whole pixels throughout) | 23 | 23 | 5.99 % |
  | `normal()` (Compose's own line) | 42 | 31 | 10.72 % |
  | `SpacedWords` (a row of texts) | 141 | 140 | 1.51 % |
  | `Text`'s line boxes | 173 | 167 | 48.64 % |
  | `browserLeading()` | 0 (the same style) | 0 | — |

- **Removed: `browserLeading()` alone**, twelve calls of a `TextStyle.copy` with the leading every `HaulType` style
  with a set line height already has (`style()` sets it). Every other canvas-only workaround is now what the goldens
  hold; taking one out means re-recording goldens, which B-85 leaves to a decision, so the code says why each stays
  (`Text`, `FlexPair`, `InlineLine`, `normal()`, `CssLayout.kt`).
- **Proposed follow-ups, not filed** (the item was told not to add items):
  - *Lay the order and account pages out in whole pixels*: drop `CssColumn`/`CssBox`/`cssLines` and the fraction
    bookkeeping in `AccountViews.kt` (`Tiles`, `GridPair`, `PhoneRow`) and `OrderViews.kt`, about 100 lines of
    `CssLayout.kt` and as many in the two pages, and re-record the 23 goldens it moves (0.29–5.99 %). The only removal
    here that buys code back; it needs the owner's yes to pages that sit a few pixels from where they are now.
  - *On Compose 1.13* (after Kotlin 2.5.20): `OverlayHost` back to `Popup`/`Dialog` (CMP-9368), `linkedText` back to
    `LinkAnnotation`, `MIRRORED_ROLE` and the state words out of the names (`aria-checked`), and the store's text
    menus rechecked against Compose's new one.
- **Not removed, though their reason moved:** `FlexPair`, `InlineLine`, `subPixelSlack`, `normal()` and `SpacedWords`
  were the canvas's; each is small, and a re-recording to take one out shows a developer nothing.
- **Noticed, not changed:** the README's «168 screenshot goldens» is 183 on `main`.

