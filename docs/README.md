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

- [ ] [haul-server](services/haul-server.md) — owns all data; builds every screen as a kompot tree; the order saga and the simulators (draft: deal prices B-57, campaign and deal ends B-58)
- [x] [haul-web](services/haul-web.md) — the browser storefront: renderers, navigation, Loading/Error, sign-in, cart commands
- [x] [haul-shared](services/haul-shared.md) — the contract: components on the wire, command bodies, error codes

### Features (11)

Browsing:
- [ ] [feature-browse](features/feature-browse.md) — Home and category catalog (draft: deal prices B-57, campaign and deal ends B-58)
- [x] [feature-search](features/feature-search.md) — Search and autocomplete
- [x] [feature-product](features/feature-product.md) — Product page
- [x] [feature-reviews](features/feature-reviews.md) — Reviews and questions
- [x] [feature-recommendations](features/feature-recommendations.md) — Picked for you

Buying:
- [x] [feature-identity](features/feature-identity.md) — Sign-in, guests and the guest cart
- [x] [feature-cart](features/feature-cart.md) — Cart
- [x] [feature-checkout](features/feature-checkout.md) — Checkout
- [x] [feature-orders](features/feature-orders.md) — Order lifecycle, tracking and returns

Account and loyalty:
- [x] [feature-account](features/feature-account.md) — Account overview and the Saved list
- [x] [feature-membership](features/feature-membership.md) — Haul Plus, points and Haul Pay

### Screens / flows (10)

- [x] [screen-account](screens/screen-account.md) — Account, 6 states
- [x] [screen-cart](screens/screen-cart.md) — Cart, 8 states
- [x] [screen-catalog](screens/screen-catalog.md) — Category, 5 states
- [x] [screen-checkout](screens/screen-checkout.md) — Checkout, 9 states
- [ ] [screen-deals](screens/screen-deals.md) — Deals, no artboard (draft: B-57, B-58, and no artboard)
- [x] [screen-home](screens/screen-home.md) — Home, 5 states
- [x] [screen-order](screens/screen-order.md) — Order, 10 states
- [x] [screen-product](screens/screen-product.md) — Product, 10 states
- [x] [screen-saved](screens/screen-saved.md) — Saved, 5 states
- [x] [screen-search](screens/screen-search.md) — Search, 5 states

### API (12)

- [x] [endpoint-account](api/endpoint-account.md) — Account overview
- [x] [endpoint-cart](api/endpoint-cart.md) — Cart
- [x] [endpoint-catalog](api/endpoint-catalog.md) — Home, deals, category, product, photos
- [x] [endpoint-checkout](api/endpoint-checkout.md) — Checkout and placement
- [x] [endpoint-identity](api/endpoint-identity.md) — Guests, sign-in, cart merge, addresses
- [x] [endpoint-membership](api/endpoint-membership.md) — Haul Plus
- [x] [endpoint-ops](api/endpoint-ops.md) — Probes
- [x] [endpoint-orders](api/endpoint-orders.md) — Orders and returns
- [x] [endpoint-recommendations](api/endpoint-recommendations.md) — Picked for you
- [x] [endpoint-reviews](api/endpoint-reviews.md) — Reviews and questions
- [x] [endpoint-saved](api/endpoint-saved.md) — Saved list
- [x] [endpoint-search](api/endpoint-search.md) — Search
