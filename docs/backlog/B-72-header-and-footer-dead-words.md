---
id: B-72
title: "client + server: the header strip and the footer do not pretend to be links"
status: wip
priority: P2
size: S
stage: stage-10-review
---

# B-72 — client + server: the header strip and the footer do not pretend to be links

Dead on every page: «Sell on Haul», «Help», «EN · USD» (`ui/HaulHeaderView.kt:201-204`, phone «Help» :301),
«Deliver to Brooklyn, NY 11211» (:198, :300, :406-421), the «All categories ▾» picker inside the search field
(:244-248), and all sixteen footer links (`ui/HaulFooterView.kt:104-112`; the wire carries `links: List<String>`).
Some have real destinations: footer «Deals», «Haul Plus», «Track an order» (→ orders).

- **Decided as product owner:** wire what has a page (Deals, Haul Plus, Track an order, the search category picker
  as a real scope), and draw the rest as plain, non-link text or remove it; the footer's wire grows an action per
  link.
- AC: every remaining word in the strip and the footer either navigates or reads as text; client tests; goldens.
- Anchors: `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulFooterView.kt`, `server/src/main/kotlin/io/github/youndie/haul/shell/Frame.kt`.
