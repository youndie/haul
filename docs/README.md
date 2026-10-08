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

Nothing is built yet. The feature, screen, endpoint and service documents are drafted in an open
pull request and arrive here one by one, `active`, as the code behind each lands.

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
