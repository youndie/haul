---
id: B-160
title: "feat: the stand says it is a demo built with Kotlin, and links to its source"
status: wip
priority: P1
size: S
stage: stage-15-showcase
---

# B-160 — feat: the stand says it is a demo built with Kotlin, and links to its source

Four reviews of the stand (2026-11-01: an architect, a product manager, a designer, an ordinary shopper) agreed on
one thing first: nothing on haul.kotlin.website says it is a demo or what it is built with. The shopper read it as a
real store that cannot be trusted — drawn products, odd names, no owner, a sign-in page on another domain — and said
the strangeness would read as a convention the moment the page said «demo». A developer who lands on it never learns
it is Kotlin end to end, or where the code is.

- **One quiet line on every page, and an About.** A slim strip above the header — «A demo store built with Kotlin:
  Ktor, kompot, Compose Multiplatform in the browser · Source on GitHub» — that a visitor can close (remembered for
  the session), and an «About this demo» page from the footer: what the stack is, what is real (the saga, the
  simulators, the observability) and what is not (no money moves, no parcel ships, the products are invented), and
  links to the repository and its README. Both are server trees like every page, so the copy is the server's.
- Rejected: a banner over the hero (the store stops looking like a store, which is the point of the demo), a modal on
  arrival (one more thing between the visitor and the page).

- AC: every page shows the strip until it is closed; «Source on GitHub» opens the repository; the About page is
  reachable from the footer and by its address; goldens with the strip recorded and looked at.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/shell/`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulHeaderView.kt`,
  `composeApp/src/commonMain/kotlin/io/github/youndie/haul/ui/HaulFooterView.kt`.
