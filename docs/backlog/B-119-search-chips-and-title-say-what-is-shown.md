---
id: B-119
title: "fix: a search's title, chips and picker say what the grid shows"
status: done
priority: P2
size: S/M
stage: stage-12-qa-run
epic: feature-search
---

# B-119 — fix: a search's title, chips and picker say what the grid shows

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)) on the stand:

- the title counts every result while a chip scopes the grid: «30 results» with «Running shoes 15» chosen,
  «600 results» with one «Classics 15» chosen;
- chips repeat leaf names: `classic` draws ten «Classics 15» chips, `everyday` «Limited 5», «Limited 4», «New in
  4», «New in 4» — leaves of different parents (`party-premium-limited`, `beauty-premium-limited`, …) that read
  the same;
- the picker reads «All categories» while a subcategory chip scopes the results.

- **What the page names is what the grid holds.** The title counts the chosen chip's results (and says of
  how many, if that is worth keeping); a chip whose leaf name repeats carries its parent («Party · Limited»);
  the picker names the chip's category. Rejected: hiding repeated chips — they are different results.

- AC: `classic` with one chip chosen titles the chip's count; no two chips on one search read the same; the
  picker names the scope a chip chose. Server tests on the search tree hold all three.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/search/screen/SearchScreen.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/search/SearchRouting.kt`.

## Findings (2026-10-12)

- **The title** (`SearchScreen.results`) counts what the grid holds: the chosen chip's results, «All»'s every
  match — `classic` with «Appliances · Classics» chosen reads «15 results», with «All» «600 results». «Of how
  many» is not kept: the «All» chip beside it already says it. One result reads «1 result». The page for a scope
  the query finds nothing in (B-90) titles «0 results», as its chosen chip reads, and keeps «N results in other
  categories.» under it.
- **The title is one of the search's parts now** (`SearchScreen.PARTS`: `header`, `title`, `categories`,
  `grid`, `pagination`). A chip's `load` replaced the chips and the grid but left the title the page was opened
  with, so a chip pressed in place would have gone on reading every match; `PartsRoutesTest` «every category
  chip and page of a search answers the parts of the page it opens» caught it (red until `title` was added).
- **The chips** (`SearchScreen.names`, `Catalog.kt` `nameOf`): a chip is its category's name, and where that
  name repeats among the page's chips it carries its top-level category, «Party · Limited» (the item's form, with
  « · »; the suggest panel's «Sports › Running shoes» is left as it is), then every level if that still repeats.
  A name that does not repeat stays bare — «Running shoes 5» beside «Party · Classics 15» on `classic`. The
  seed's generic leaves are `<Top> › Essentials › Classics | New in` and `<Top> › Premium › Signature |
  Limited`, so the immediate parent («Essentials», «Premium») would have repeated too; the top level is what
  tells them apart.
- **The picker** (`Frame.header`): a search in a category below the top level adds that category to the
  header's `scopes`, named as above with its top level («Appliances · Classics») and placed right after it, and
  `scope` is its slug — so the client's picker, unchanged, reads it, offers it, and the next search from the
  field stays in it. «All» still sets it back to «All categories». A card's «+» on such a page keeps it
  (`LineAnswers` passes the categories that name it). No wire change: `HaulHeader.scopes` only carries one more
  entry; its KDoc and the client's `ScopePicker` KDoc say so.
- **Tests.** `SearchSaysWhatIsShownTest`: «the title counts the chosen chip's results», «no two chips on one
  search read the same» (`classic`, `everyday`, `classic` in Party), «the picker names the scope a chip chose»
  (the page, the chip's `load` and «All»). `SearchRoutesTest`: the B-72 test drops «a leaf is not a choice of the
  picker» and finds Sports' chips by their `category`, not their name; the B-90 test holds «0 results».
  `LineAnswersTest` «a card's plus on a scoped search keeps the header's scope» holds a leaf's scope too.
  Mutations, each red then restored with `git status` clean: the title back to every match → «the title counts
  …» and the B-90 test red; chips named by `category.name` → «no two chips …» and «the picker names …» red; no
  inner scope in `Frame.scopes` → «the picker names …» and the `LineAnswersTest` case red; `LineAnswers` without
  the categories → the `LineAnswersTest` case red.
- **Seen on the way.** The first full `check` had `UniqueIdsTest` «every page a customer opens draws each id
  once» red on `/ui/account/orders/HL-48302` answering `404 order_not_found` for Maya; it passed alone and in the
  next full run, and touches no search code — a flake, recorded here rather than as an item.
- **Goldens.** None changed: the search fixtures are hand-written trees (`search_results.json`, «All» chosen,
  the canvas's «14,870 results» over «All 14,870»), and the canvas draws neither a chosen leaf chip nor a
  repeated name. The canvas's title, too, counts «All»'s results with «All» chosen, which the rule keeps.
- **Docs.** Research §«The search field's «All categories ▾» is a real scope» (a leaf's chip now names the
  leaf), §«A scope the query finds nothing in says so» (the title «0 results»), and a new §«What a search's page
  names is what its grid holds». QA: TC-SRCH-04 expects «0 results»; new TC-SRCH-11 walks the three.
