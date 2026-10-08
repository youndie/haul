---
id: B-35
title: "client: the app loads screens from the server and navigates between them"
status: open
priority: P1
size: M
stage: stage-3-search
blocked_by: [B-07]
---

# B-35 — client: the app loads screens from the server and navigates between them

Every screen so far renders to the canvas from fixture bodies, and the app's root still draws the
theme with an empty body: nothing fetches a tree, nothing follows a `NavigateAction`, and «now» does
not tick. B-07 and B-10 both recorded this as «not done here» and no item owned it — so the stand
would answer a page that draws nothing (B-28 had to wire Home by hand to measure a real first load).

The app shell: resolve the browser's path to a screen route, fetch its kompot tree from the same
origin, draw it through `haulRegistry()` inside the header frame, follow `NavigateAction` deeplinks
(browser history and back), refresh on kompot's `refresh`, and draw the shell's loading, error
(retry) and not-found states between them. The search field fetches `/ui/search/suggest` as the
shopper types and opens `/ui/search` on submit; the countdowns tick from one clock the screens share.

- Not covered: sign-in redirects (B-12), cart commands (B-13).
- AC: in the browser bundle, `/`, a category, a product and a search load from a running server and
  link to each other through the actions in their trees; back returns; a server error shows the
  error state with retry. Tested against a fake transport (states, navigation, refresh), and walked
  once in headless Chrome against the image.
- Anchors (planned): `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`.
