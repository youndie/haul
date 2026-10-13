# docs — Haul

Haul is an open, end-to-end marketplace storefront in Kotlin. Its documentation is three parts, and the
code is the reference for everything they do not say.

| Part | Where | Answers |
|---|---|---|
| Research | [`research/`](research/research-architecture.md) | *why* it is built this way: every decision with its reason, every measurement with its method |
| Backlog | [`backlog/`](backlog/), indexed in [backlog.md](../backlog.md) | what each change was for, what it found on the way, and what is still open |
| QA catalogue | [`qa/`](qa/README.md) | what a release must do, case by case, and what each run of it found |

The feature, screen, endpoint and service layers docs-bootstrap allows for are not kept here (decided
2026-10-13). Their draft fell about ninety items behind the code while it waited to land and was closed
(youndie/haul#2); the research, the items and the QA cases already carried the same decisions, closer to the code.

**Backlog** — [backlog.md](../backlog.md): the index and the decisions; the items themselves are
one file each in [`backlog/`](backlog/), cited as `[B-12](backlog/B-12-shildik-sign-in-in-the-browser.md)`.

## Conventions

- **`id`** in the frontmatter is unique and equals the filename.
- A finding is written from the code, not from memory: the actual status codes, error strings and numbers.
- **The primary consumer is a coding agent.** Every document carries code anchors — paths, not copies.
- Language: English. Code identifiers, URLs and HTTP headers verbatim as in the code.

## Templates

`templates/` holds the templates of the documents kept here — a backlog item and the research — so the format
travels with the repository.

## Checks

The Makefile runs docs-bootstrap's checks at the version `.github/workflows/check.yaml` pins, and CI
runs the same targets:

```bash
pip install pyyaml
make check      # the gate, then the reports: what CI runs
make fix        # regenerate the backlog index, append missing coverage-map lines
```

`make check` also runs `scripts/qa-cases.py`, which holds the QA catalogue in [`qa/`](qa/README.md) — the
cases a release is walked against, each with the test, golden or script that holds it — to its format.

## Coverage map

The list below is **checked** against the files on disk.

### Research (1)

- [x] [research-architecture](research/research-architecture.md) — verified facts about the stack and the canvas, the decisions (JVM server, every screen a kompot tree, the order as a saga), risks, vocabulary, sample data
