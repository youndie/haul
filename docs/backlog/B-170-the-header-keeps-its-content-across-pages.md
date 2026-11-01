---
id: B-170
title: "fix: the header keeps its content while the next page loads"
status: wip
priority: P2
size: S
stage: stage-15-showcase
---

# B-170 — fix: the header keeps its content while the next page loads

The designer and the shopper (2026-11-01) both saw the header go back to its placeholders on navigation — the
search, the account and the cart blink to grey bars and back. The header is the same on every page; drawing it
again from nothing on each is a flash with no reason.

- First reproduce it on the stand with a visible browser (the reviews ran in a hidden pane, which slows frames); if
  it is real, the shell keeps the last header drawn until the next tree brings its own. If it does not reproduce,
  the item is dropped with what was seen.

- AC: navigating between home, a category and a product never shows the header's placeholders after the first load
  (a client test over two trees); or the item is dropped with the walk that did not reproduce it.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/`.
