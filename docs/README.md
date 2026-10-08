# docs — Haul

Haul is an open, end-to-end marketplace storefront in Kotlin. The documentation is layered; links
run top to bottom.

```
[ Research (why the architecture is what it is) ]
                     │
[ Feature (business + BDD) ] ──▶ [ Client screen ]
                                        │
                                        ▼
                              [ API endpoint (contract, auth tier) ]
                                        │
                                        ▼
                              [ Service (ownership, deploy) ]
```

| Layer | Directory | Answers | Source of truth |
|---|---|---|---|
| Research | `research/` | *why* it is built this way; what is verified, what is a hypothesis | the artefacts each fact names |
| Feature | `features/` | *what* the system does and *why*; BDD scenarios | this repository |
| Client | `screens/` | what the shopper sees: states, actions, navigation | this repository + the screen's code |
| API | `api/` | URL, method, auth tier, where the contract lives | the `shared` module |
| Service | `services/` | who owns the data, dependencies, deploy, local setup | this repository |

The feature, screen, endpoint and service documents are drafted in an open pull request and arrive
here `active`, once the code behind each has landed.

**Backlog** — [backlog.md](../backlog.md): the index and the decisions; the items themselves are
one file each in [`backlog/`](backlog/), cited as `[B-12](backlog/B-12-shildik-sign-in-in-the-browser.md)`.

## Conventions

- **`id`** in the frontmatter is unique and equals the filename.
- Cross-layer links are ids in the frontmatter and ordinary markdown links in the body.
- One document, one entity. A feature spanning three modules is **one** file with three entries
  in `involved_services`.
- BDD scenarios are written from the code, not from memory: check the actual status codes and
  error strings before a document goes `active`.
- **The primary consumer is a coding agent.** Every document carries code anchors — paths, not
  copies.
- Language: English. Code identifiers, URLs and HTTP headers verbatim as in the code.

## Templates

`templates/` holds a copy of the document templates, so the format travels with the repository.

## Checks

The Makefile runs docs-bootstrap's checks at the version `.github/workflows/check.yaml` pins, and CI
runs the same targets:

```bash
pip install pyyaml
make check      # the gate, then the reports: what CI runs
make fix        # regenerate the backlog index, append missing coverage-map lines
```

## Coverage map

The list below is **checked** against the files on disk.

### Research (1)

- [x] [research-architecture](research/research-architecture.md) — verified facts about the stack and the canvas, the decisions (JVM server, every screen a kompot tree, the order as a saga), risks, vocabulary, sample data

### Services (3/3)

- [ ] [haul-server](services/haul-server.md) — owns all data; builds every screen as a kompot tree; the order saga and the simulators (draft)
- [ ] [haul-web](services/haul-web.md) — the browser storefront: renderers, navigation, Loading/Error, sign-in (draft)
- [ ] [haul-shared](services/haul-shared.md) — the contract: components on the wire, command bodies, error codes (draft)

### Features (11)

Browsing:
- [ ] [feature-browse](features/feature-browse.md) — Home and category catalog (draft)
- [x] [feature-search](features/feature-search.md) — Search and autocomplete
- [ ] [feature-product](features/feature-product.md) — Product page (draft)
- [ ] [feature-reviews](features/feature-reviews.md) — Reviews and questions (draft)
- [ ] [feature-recommendations](features/feature-recommendations.md) — Picked for you (draft)

Buying:
- [x] [feature-identity](features/feature-identity.md) — Sign-in, guests and the guest cart
- [ ] [feature-cart](features/feature-cart.md) — Cart (draft)
- [ ] [feature-checkout](features/feature-checkout.md) — Checkout (draft)
- [ ] [feature-orders](features/feature-orders.md) — Order lifecycle, tracking and returns (draft)

Account and loyalty:
- [ ] [feature-account](features/feature-account.md) — Account overview and the Saved list (draft)
- [ ] [feature-membership](features/feature-membership.md) — Haul Plus, points and Haul Pay (draft)

### Screens / flows (10)

- [ ] [screen-account](screens/screen-account.md) — Account, 6 states (draft)
- [ ] [screen-cart](screens/screen-cart.md) — Cart, 8 states (draft)
- [ ] [screen-catalog](screens/screen-catalog.md) — Category, 5 states (draft)
- [ ] [screen-checkout](screens/screen-checkout.md) — Checkout, 9 states (draft)
- [ ] [screen-deals](screens/screen-deals.md) — Deals, no artboard (draft)
- [ ] [screen-home](screens/screen-home.md) — Home, 5 states (draft)
- [ ] [screen-order](screens/screen-order.md) — Order, 10 states (draft)
- [ ] [screen-product](screens/screen-product.md) — Product, 10 states (draft)
- [ ] [screen-saved](screens/screen-saved.md) — Saved, 5 states (draft)
- [x] [screen-search](screens/screen-search.md) — Search, 5 states

### API (12)

- [ ] [endpoint-account](api/endpoint-account.md) — Account overview (draft)
- [x] [endpoint-cart](api/endpoint-cart.md) — Cart
- [ ] [endpoint-catalog](api/endpoint-catalog.md) — Home, deals, category, product, photos (draft)
- [x] [endpoint-checkout](api/endpoint-checkout.md) — Checkout and placement
- [x] [endpoint-identity](api/endpoint-identity.md) — Guests, sign-in, cart merge, addresses
- [ ] [endpoint-membership](api/endpoint-membership.md) — Haul Plus (draft)
- [x] [endpoint-ops](api/endpoint-ops.md) — Probes
- [ ] [endpoint-orders](api/endpoint-orders.md) — Orders and returns (draft)
- [ ] [endpoint-recommendations](api/endpoint-recommendations.md) — Picked for you (draft)
- [ ] [endpoint-reviews](api/endpoint-reviews.md) — Reviews and questions (draft)
- [ ] [endpoint-saved](api/endpoint-saved.md) — Saved list (draft)
- [x] [endpoint-search](api/endpoint-search.md) — Search
