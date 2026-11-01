---
id: B-165
title: "docs: the README says what to look at in five minutes, and the repository points at the stand"
status: done
priority: P1
size: S
stage: stage-15-showcase
blocked_by: [B-160]
---

# B-165 — docs: the README says what to look at in five minutes, and the repository points at the stand

The README tells how the project is built and run, not what a visitor should open to see the stack work; the
repository on GitHub has no homepage link and no topics, so it is not found by anyone browsing «kotlin multiplatform»
or «server-driven UI».

- **A first section «What to look at in five minutes»:** the stand's link, then four stops each with what to notice
  and the code behind it — a page drawn from a server tree, a product added to the cart (a partial update), an order
  placed and followed (the saga, B-162), the live orders feed (B-164) and «Under the hood» (B-163) — the stops that
  do not exist yet are left out and added by their items. The repository's homepage is the stand and its topics name
  the stack.
- Not covered: an article; it is the owner's to write and publish.

- AC: the section is the README's first after the title and every link in it resolves; `gh repo view` shows the
  homepage and the topics.
- Anchors: `README.md`.

## Findings

- **Four stops, two of them waiting.** The tour has the stand with its demo line and About (B-160), a page read as
  the server sends it, a partial update, and an order — which a visitor cannot place yet (B-162). The live orders
  feed (B-164) and «Under the hood» (B-163) are not stops yet; each adds itself when it lands.
- **A sentence that was not true.** «There is always an order in flight to look at» promised what a visitor could not
  see; it now says the orders move through the saga, which they do.
- **Repository metadata:** the homepage is the stand and the topics name the stack (`gh repo edit`), outside the
  repository's history.
