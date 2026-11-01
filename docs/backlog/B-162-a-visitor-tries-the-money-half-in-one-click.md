---
id: B-162
title: "feat: a visitor tries checkout, the live order and a return as a demo shopper, in one click"
status: open
priority: P1
size: M
stage: stage-15-showcase
---

# B-162 — feat: a visitor tries checkout, the live order and a return as a demo shopper, in one click

The parts of the store that show the stack best — checkout across two sellers, the petich saga with its
compensations, the order page updating live, a partial return — are all behind sign-in (research D5), and a visitor
to the stand has no account and no way to make one. The README promises «an order in flight to look at»; the stand
does not let anyone look. The shopper in the review read the bare sign-in page on haul-id.kotlin.website as phishing.

- **«Try as a demo shopper» beside «Sign in».** One press gives the visitor a customer of their own — a throwaway
  account made for this browser session, with an address and Haul Pay already set, and swept after a day — so no
  visitor sees another's orders, and the guest cart merges into it as any sign-in does. How the customer is made is
  decided in the item, in this order of preference: shildik issues tokens for a demo user it creates on request
  (shildik is our own; a change there is a pull request to it, not a reason to stop), or a demo client whose tokens
  shildik already knows how to issue. The ordinary sign-in stays as it is; its page says what it is the sign-in of.
- Rejected: one shared demo account with a published password (every visitor sees and changes the others' orders,
  and the synthetic shoppers would crowd it), a guest checkout (D5's rule that money needs a customer is what the
  demo shows).
- Not covered: changing D5 for real customers.

- AC: a visitor with no account presses one control and reaches checkout signed in; places an order; watches it move
  on the order page; returns part of it; a second browser's demo shopper sees none of it; demo customers older than a
  day are gone; D5 in the research records the decision. The e2e walks the demo path.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/auth/`, `composeApp/src/wasmJsMain/kotlin/io/github/youndie/haul/`,
  `docs/research/research-architecture.md`.
