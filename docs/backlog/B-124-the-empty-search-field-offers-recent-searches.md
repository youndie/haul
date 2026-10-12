---
id: B-124
title: "fix: pressing the empty search field offers a customer's recent searches"
status: done
priority: P2
size: S
stage: stage-12-qa-run
epic: feature-search
---

# B-124 — fix: pressing the empty search field offers a customer's recent searches

Found by the catalogue's first run, TC-SRCH-10 ([run](../qa/runs/2026-10-11-v0.4.0.md)): Maya, after searching `mug`, `sony`, `duvet`, `mug`,
pressed the empty field — a caret, no panel, no request. «Recent · Clear» (in the right order) appeared only after
two letters, since `Storefront.kt` asks for the panel only for typed text (`if (!search.typed || typed.isEmpty())
panel = null`) and suggest refuses a query under two characters. No item decided the empty field either way.

- **Recall is for the empty field.** A recent list that shows only once two letters are typed is suggest filtered by
  history, not recall; the shopper who wants last night's query has to remember it to get it back. The empty field
  pressed by a customer asks for the panel with no query and the server answers the recent list alone; a guest's
  empty field asks nothing. Rejected: correcting the case to «two letters» — it would write the gap down as the
  design.

- AC: Maya presses the empty field and sees her last ten, newest first, with «Clear»; a row runs its search in the
  picker's scope; a guest's empty field sends no request. A client test and a route test hold it.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Storefront.kt`,
  `server/src/main/kotlin/io/github/youndie/haul/feature/search/SearchRouting.kt`.

## Findings (2026-10-12)

- **The wire is suggest with an empty `q`**: `/ui/search/suggest?q=` (and `&category=…`) answers the same
  `SearchSuggestPanel` with `query` empty, the customer's recent searches alone — newest first, each row's search in
  the scope — and «Clear»; no queries, categories, products or `allResultsAction`, an empty `allResultsLabel`. A
  guest gets the same panel with nothing in it (the route stays public). A blank or absent `q` is recall; anything
  typed is a query, so `q=m` and `q=%20r%20` are still `400 query_too_short`. A scope the catalog does not have is
  still `404`. Why this and not a route of its own: it is the same panel under the same address builder
  (`Address.suggest`), so the scope, its `404` and «Clear»'s asking-again carry over unchanged; nothing is matched
  for an empty query. Written into the research («Decided in B-124») with the alternatives refused.
- **Client**: the shell asks at once — no typing pause — when a customer (`SessionControls.signedIn`) presses the
  empty field or empties it while in it; a guest's (or a session-less screenshot's) empty field asks nothing. The
  recall stays when the focus leaves the field for a press on the panel and ends on typing, the scrim or a visit. A
  panel with no query and nothing recent — a guest's, or one just cleared — is no panel
  (`HaulTransport.suggest`), so «Clear» closes it. The view draws a recall panel without the products' column.
  The suggest panel now carries `SUGGEST_PANEL_TAG` so a test can tell «closed» from «an empty card».
- **Trap met on the way**: the effect that asks for the panel first read the recall flag as state; an emptied
  field restarts it before the flag turns on and again once it has, and the first restart already saw the flag
  on — two requests. It reads the value of its composition now (`recall`), which the typing test holds (one recall
  request after the field is emptied).
- **Not changed**: the arrow keys still move over the panel's queries only, not over recent rows; a customer
  typing one letter over the recall panel sees it close after the pause (the one-letter `400`), as typing did before.
- **Tests**: server `RecentSearchesRoutesTest` «the empty field offers a customer's recent searches alone, newest
  first, in the scope» (Maya's `mug`, `sony`, `duvet`, `mug` on a ticking clock → `mug`, `duvet`, `sony`; rows in
  `home-kitchen`; an unknown scope `404`) and «the empty field offers a guest nothing and one character is still too
  short». Client `EmptyFieldRecallTest`: the customer's press asks `/ui/search/suggest?q=&category=home-kitchen`,
  draws the rows in order with «Clear» and no «Top products», and a row opens its scoped search; a guest's and a
  session-less empty field ask nothing; «Clear» sends the `DELETE` and the panel closes; typing leaves the recall
  for suggestions and emptying the field recalls once.
- **Mutation**, each on the committed change, each red, restored, `git status` clean: suggest's empty `q` back to
  `Query.of` (both route tests red); the recall rows built without the scope (the customer route test red); the
  shell's recall branch disabled (three client tests red); the guest guard dropped (the guest test red); the
  transport's empty-panel filter dropped (the «Clear» test red); the view's recall branch dropped (the customer
  client test red on «Top products»).
- **Goldens**: two new, no artboard — `Search_Recent` and `Search_Recent_Phone`, the recall panel over the home page
  (`SearchFixtures.kt`), recorded on the Linux box in a copy outside the mutagen replica and looked at. No other
  golden moved (`viddikRecord` without `--force` wrote only these two; `viddikVerify` green in `check`).

