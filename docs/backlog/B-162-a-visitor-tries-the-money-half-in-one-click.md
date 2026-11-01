---
id: B-162
title: "feat: a visitor tries checkout, the live order and a return as a demo shopper, in one click"
status: done
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

## Findings

- **How the customer is made: shildik as it is (option a).** shildik 0.4.1 has no token exchange, no impersonation
  and no grant for a person on a server's behalf (`docs/api/protocol-oidc-subset.md`: `authorization_code`,
  `client_credentials` for services, `refresh_token`), but its management API imports a person under a given id, sets
  a password and turns a person off (`ImportUserRequest.enabled`), and its code flow can be walked from the server as
  the e2e and the server's suite already walk it. So the server imports `demo-<uuid>`, sets a one-time random
  password, walks the flow at the storefront's public client and hands the browser the tokens. A shildik change
  (option b) — a grant or an admin route that issues a person's tokens, a scoped admin role, a delete route — would
  be cleaner, but needs a release and a new version on the stand, and is not needed; a token haul issues itself
  (option c) would be a second issuer on every route and a renewal path of its own. The comparison and the costs are
  in research D5, «Decided in B-162».
- **No stand change beyond the next release.** The chart hands the server the release's provider's management port
  (`http://<release>-shildik:9000`) and the bootstrap token from the provider's own Secret, and admits the server to
  that port in the NetworkPolicy, when `shildik.enabled` and `server.demoShoppers=true`. Nothing new for the deploy to
  pass beyond that value; it is **off by default** (see «Switching it on»). The cost, written in D5: the internet-facing server
  now holds the realm's admin token.
- **Where the control is.** Beside «Sign in» in the wide header it squeezed the search field until its placeholder
  was cut at 1280 (`WidthsTest`); it sits at the end of the category row instead, before «HAUL PLUS», whose words give
  way, and keeps its whole name at 1024–1440. On a phone it is a row of the header's menu under «Sign in», and it is
  the second button of the sign-in page and of the prompt a guest gets on a customer's page.
- **The sweep** forgets a demo shopper made more than a day ago, hourly, after turning them off at shildik:
  their orders and what they held (stock back on the shelf less what a return restocked, window places, payments,
  saga rows, idempotency keys), reviews, questions and votes with the product counts and averages they moved, and
  the customer. shildik 0.4.1 cannot delete a person, so a swept one stays in the realm, turned off — its sign-in and
  any renewal refused (walked in the server's suite against the provider's image). A token of a swept demo shopper
  is refused by the store too, rather than making a new, empty customer.
- **Bounded**: 500 a day by default (`server.demoDailyLimit`), then `503` «Every demo shopper for today is taken».
- **The ordinary sign-in is as it was.** The storefront's own page already says «Sign in to HAUL»; the provider's page
  on haul-id.kotlin.website is shildik's, and what it says of itself is shildik's and the realm's to change — not
  changed here, and a visitor who only wants to try the store no longer has to meet it.
- **Not here:** the README's «Run it» says nothing of the demo (B-165 is rewriting the README); reviews and questions
  written by a demo shopper are public until the sweep, as anybody's are.

## Switching it on

Merged off (`server.demoShoppers: false`, 2026-11-01): with it on, the server that faces the internet holds shildik's
bootstrap token, which can do anything in the stand's realm — a change of the stand's exposure that is the owner's to
make, not a chart default. The owner decides between:

- turning it on as it is (`server.demoShoppers: true` in infra's values for haul);
- first a scoped role in shildik that can only create, disable and sign in people (a shildik change), then on;
- leaving it off and showing the saga through the live orders feed (B-164) alone.

Also for the owner: demo shoppers' reviews and questions are public until the sweep, and shildik 0.4.1 cannot delete a
person, so swept ones stay in the realm, disabled.
