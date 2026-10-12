---
id: B-121
title: "fix: the small presses and lines the v0.4.0 run noticed"
status: wip
priority: P3
size: S
stage: stage-12-qa-run
---

# B-121 — fix: the small presses and lines the v0.4.0 run noticed

Found outside the catalogue by its first run ([run](../qa/runs/2026-10-11-v0.4.0.md)), each small and each seen
on the stand:

- «Checkout needs a sign-in» breaks at its hyphen at 1440 («sign-» / «in»): a non-breaking hyphen
  (`SignInPrompt.kt`).
- The header's «Sign in» on `/sign-in?next=…` drops the `next` (→ `/sign-in`).
- A too-short query rewrites the address: `a` + Enter over the `mug` results leaves `/search?q=a` with the mug
  results under the notice; a reload then shows «Keep typing».
- A suggest request fires after a submit (`GET /ui/search/suggest?q=classic` right after Enter opened the
  results).
- The rating link pressed while already on Reviews refetches the same address and does not scroll the tab row
  into view.
- The sort menu stays open after picking the order already shown.

- AC: each of the six behaves as its line says it should; a test per line where the line is the client's logic.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/SignInPrompt.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`.

## Findings (2026-10-12)

- **«sign-in» stays whole** — the prompt's three titles («Checkout / This order / This page needs a sign-in») and
  their accent write the word with a non-breaking hyphen, U+2011 (`SIGN_IN_WORD`, `SignInPrompt.kt`). Both cuts of
  Bodoni Moda and Archivo draw it, so B-128's check passes it and no fallback font is fetched; JetBrains Mono does
  not, which no title uses. A word joiner was not needed. The accent is the same constant: a title whose hyphen
  differed from the accent's would lose its italic. `SignInTitleTest` lays the titles out at every width from the
  word's own to 1,600 px, at both sizes, and finds no line ending in «sign‑». No golden draws the prompt, so none
  changed. Left as it is: the sign-in page's «The sign-in didn’t go through» has the same hyphen; it was not seen to
  break and is not this line.
- **The header's «Sign in» on `/sign-in?next=…`** — the press names no `next`, and a blocked popup led to the
  press's own address, a bare `/sign-in`. On the sign-in page a press without a `next` now keeps the page's
  address (`Signing.blocked`, `SignInPage.kt`). A popup that goes through was already right: the page is drawn again
  for the customer, who is sent on to its `next`.
- **A too-short query over the results** — B-94 decided the results stay under the notice; the address was the
  refused search's, because a kept page's address changes before its tree arrives (B-62). The entry the search took
  is given back: `Navigator.withdraw` goes back to the results' address, and that arrival is no visit, so the results
  are not loaded again and the notice stays. Only an entry `open` pushed for the refused address is withdrawn; a
  refused address reached by back or forward stays as it is (forward to it again draws the same notice). Rejected:
  replacing the entry with the results' address — it leaves two entries of the results, and back would load the
  same page again.
- **A suggest after a submit** — Enter before the typing pause was over left the pause running, and the suggest
  was asked for once it ended, after the results. Submitting ends the typing (`SearchInput.submit`), which cancels
  the pause.
- **The rating link on Reviews** — the refetch is B-95's decision (a press to the address shown loads it again), so
  it stays. The tab row was brought into view only when another tab arrived; «2,341 reviews» and «All
  specifications» now ask for it as they are pressed (`TabsInView`, provided beside the page scroll), whichever tab
  arrives. A tab pressed in the row still does not move the page.
- **The sort menu** — the order shown has no action (the server writes none, B-37), so its entry was not pressable
  and the menu stayed open. The entry drawn as chosen now closes a menu, with nothing sent (`Menu`, `LinkMenu.kt`);
  an entry without an action that is not the chosen one is still inert. The client fixture's «Popular» had an
  action the server never sends; it has none now.
- **Documents**: TC-CAT-07, TC-SRCH-01, TC-SRCH-06, TC-PDP-03, TC-SIGN-05 and TC-SIGN-06 carry the new steps and
  tests; research, B-94's and B-95's paragraphs, the two decisions.
