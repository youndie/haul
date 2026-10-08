# Haul

An open, end-to-end marketplace storefront in Kotlin: Ktor on the JVM, kompot trees for every
screen, petich for the order, shildik for sign-in, Compose Multiplatform in the browser.

## How to start a session

1. [docs/research/research-architecture.md](docs/research/research-architecture.md) — why the system
   is built this way, the vocabulary, the sample data. A task not read against it looks like «do the
   obvious thing», which here is often wrong.
2. [backlog.md](backlog.md) and the item you are taking in `docs/backlog/`.
3. The layer document the task belongs to — feature, screen, endpoint, service. Until its code
   lands it lives as `status: draft` in the open documentation pull request, not on `main`.

## Rules

- Code, comments, KDoc, test names, exception messages, commits and pull requests in English;
  documentation in English too (research D10).
- Commits in Conventional Commits.
- A change that would make a document a lie changes the document in the same pull request.
- `make check` before pushing; it is what CI runs.
