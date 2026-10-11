# Search — `TC-SRCH`

The header's search field and its suggest panel, the scope picker, the results page and its states. Paging a
long search is walked in [catalog and filters](03-catalog-and-filters.md) (TC-CAT-08, TC-CAT-09).

### TC-SRCH-01 — The suggest panel offers queries, categories and products, and follows the keyboard

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the home page.
- **Steps:**
  1. Type `head` and pause.
  2. Press ↓ three times, ↑ once, then Enter.
  3. Type `head` again, press ↓ then ↑ twice, then Enter.
- **Expected:**
  - Step 1: one request once typing pauses; the panel lists query suggestions, categories with their counts,
    «Top products» with their photos (where the product has one stored) and «All N results».
  - Step 2: the highlight moves over the query suggestions only (↓ from none to the first …, ↑ back); Enter opens
    the highlighted query's search.
  - Step 3: ↑ above the first leaves none highlighted, and Enter opens the text as typed.
- **Decided in:** B-09, B-77, B-108
- **Coverage:** `StorefrontTest` «typing asks for suggestions once the shopper pauses and Enter opens the results»;
  `StorefrontTest` «the arrow keys and Enter pick a suggestion»;
  `StorefrontTest` «up past the first suggestion leaves Enter to the typed text»;
  `SearchRoutesTest` «the panel keeps to its limits and counts every result»;
  `LinePhotosTest` «the suggest panel's top products draw their photos»; golden `Search_Autocomplete`

### TC-SRCH-02 — A category suggestion opens the query in that category

- **Who:** guest
- **Width:** 1440
- **Preconditions:** none.
- **Steps:**
  1. Type `running` and wait for the panel.
  2. Press a category suggestion, e.g. «Sports › Running shoes 12».
- **Expected:**
  - Step 2: `/search?q=running&category=running-shoes` — the query kept — and the chosen chip counts the same 12 the
    suggestion said; not the whole category without the query.
- **Decided in:** B-103
- **Coverage:** `SearchPanelScopeTest` «a category suggestion opens the query in that category»

### TC-SRCH-03 — The scope picker scopes the next search, the panel and the results

- **Who:** guest
- **Width:** 1440
- **Preconditions:** the home page. (The phone's header has no picker: the canvas draws its field alone.)
- **Steps:**
  1. Press «All categories ▾» and choose «Home & Kitchen».
  2. Type `mug`; read the panel; press Enter.
  3. Press a card's «+» on the results.
  4. Press the «All» chip.
- **Expected:**
  - Step 1: the picker reads «Home & Kitchen»; no page opens.
  - Step 2: the panel's products, categories, counts and «All N results» are the matches under Home & Kitchen;
    Enter opens `/search?q=mug&category=home-kitchen`, the scope a chosen chip after «All», the results every
    product under the category and its subcategories.
  - Step 3: the header keeps the query and the scope.
  - Step 4: the picker goes back to «All categories».
- **Decided in:** B-72, B-103
- **Coverage:** `composeApp:DrawnActionsTest` «the search picker scopes the next search to its category»;
  `composeApp:DrawnActionsTest` «the picker reads the page's scope and all categories leaves it»;
  `SearchPanelScopeTest` «the panel follows the picker's scope»;
  `SearchRoutesTest` «a search in a top-level category holds the results under it»;
  `LineAnswersTest` «a card's plus on a scoped search keeps the header's scope»;
  golden `HaulHeader_Search_Scoped`

### TC-SRCH-04 — A scope the query finds nothing in says so

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. Choose the scope «Fashion» and search `mug`.
  2. Press «Search all categories».
- **Expected:**
  - Step 1: the title and the chips (Fashion chosen, at 0), then «Nothing for “mug” in Fashion», «N results in other
    categories.» and «Search all categories» — no grid, no pages, no sort.
  - Step 2: the search in every category.
- **Decided in:** B-90
- **Coverage:** `SearchRoutesTest` «a scope the query finds nothing in says so and offers every category»

### TC-SRCH-05 — A search is ordered by relevance until a sort is chosen

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. Search `classic` and read the sort at the end of the chips' row (over it on a phone).
  2. Choose «Popular».
  3. Press a category chip.
  4. Press the chip already chosen, and choose the sort already shown.
- **Expected:**
  - Step 1: «Relevance» is shown and first in the menu; the address has no `sort`.
  - Step 2: the results reload in place from the first page; the address has `sort=popular`.
  - Step 3: the chip keeps the sort.
  - Step 4: nothing is sent.
- **Decided in:** B-77, B-102, B-103
- **Coverage:** `SearchPanelScopeTest` «a search is ordered by relevance unless a sort is chosen»;
  `SearchPanelScopeTest` «no press on the results loads the page it is on»;
  `server:DrawnActionsTest` «the search's sort offers every order and the chips keep it»;
  `ReachablePagesTest` «a search's sort opens its orders and loads the one picked»

### TC-SRCH-06 — A query too short explains itself

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** none; then a search's results on screen.
- **Steps:**
  1. Open `/search?q=a`.
  2. On the results of `mug`, type `a` into the field and press Enter.
- **Expected:**
  - Step 1: «Keep typing», «Search needs at least two characters…», the query in the header's field, no Retry.
  - Step 2: the results stay under a notice saying the search needs at least two characters, with no Retry.
- **Decided in:** B-94
- **Coverage:** `RefusedPagesTest` «a search too short says so with the query in the field and no Retry»;
  `RefusedPagesTest` «a search too short over the results kept says so without Retry»;
  `SearchRoutesTest` «a one character query is 400 query_too_short»; golden `Search_TooShort`

### TC-SRCH-07 — No results gives advice and queries to try

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. Search `qwertyuiop`.
  2. Search a misspelt product word, e.g. `hedphones`.
  3. Press one of the pills of queries to try.
- **Expected:**
  - Step 1: the no-results page; the tips («01 Check the spelling», …) are numbered text, not pills.
  - Step 2: the spelling the catalogue has is offered.
  - Step 3: a pill runs its search.
- **Decided in:** B-77
- **Coverage:** `SearchRoutesTest` «a query that finds nothing answers the no results page»;
  `SearchRoutesTest` «a misspelt query is offered the spelling the catalog has»; golden `Search_NoResults`

### TC-SRCH-08 — Submitting the search already shown runs it again and closes the panel

- **Who:** guest
- **Width:** 1440
- **Preconditions:** the results of `mug`.
- **Steps:**
  1. Type `mug` again so the panel opens, press Enter.
  2. Open the panel again and press the suggestion that is the query shown, then «All N results».
- **Expected:**
  - Every step: the results load again in place (the line under the header) and the panel closes.
- **Decided in:** B-95
- **Coverage:** `StorefrontTest` «the query already shown submitted again loads the results again and closes the panel»;
  `StorefrontTest` «a suggestion to the search already shown loads it again and closes the panel»

### TC-SRCH-09 — The search button reads what was typed just before it

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a fresh load of the home page, the tab in the background or the page under load (a hidden tab
  throttles frames, which is where the race shows).
- **Steps:**
  1. Press the search field, type `kettle` and press the search button at once.
- **Expected:**
  - Step 1: the results of `kettle` — never an empty search, never nothing.
- **Decided in:** B-76, B-83
- **Coverage:** `StorefrontTest` «the search button searches for a query typed just before the press»;
  manual: the keys reaching the field a frame after the press is the browser's (Compose on the web); the desktop
  test holds the order, only a throttled tab shows it

### TC-SRCH-10 — A customer's recent searches run again and clear

- **Who:** Maya; guest
- **Width:** 1440
- **Preconditions:** Maya has searched a few queries; repeat one of them.
- **Steps:**
  1. Press the empty search field.
  2. Press a recent search.
  3. Press «Clear».
  4. As a guest, press the empty field.
- **Expected:**
  - Step 1: the last ten, newest first, the repeated one moved to the top.
  - Step 2: that search runs (in the picker's scope).
  - Step 3: the list empties and the panel offers none.
  - Step 4: nothing to clear.
- **Decided in:** B-09, B-49, B-103
- **Coverage:** `RecentSearchesTest` «the last ten are kept newest first and a repeat moves to the top»;
  `RecentSearchesRoutesTest` «a recent search's row runs that search again»;
  `RecentSearchesRoutesTest` «clear empties a customer's recent searches and the panel no longer offers it»;
  `RecentSearchesRoutesTest` «clearing needs a sign-in and a guest is offered nothing to clear»;
  `composeApp:DrawnActionsTest` «a recent search's row runs that search»
