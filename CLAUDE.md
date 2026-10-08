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

## Modules

| Module | What it is | Targets |
|---|---|---|
| `:shared` | the contract: Haul components on the wire, `ErrorCode`, money and time — paths are the server's strings, the client follows actions | jvm, wasmJs |
| `:server` | every screen as a kompot tree, every command, the order saga, the simulators; an `application` | JVM |
| `:composeApp` | the storefront; `jvm("desktop")` only draws the screenshots | wasmJs, desktop |
| `:e2e` | the whole path over HTTP against a composed stack | JVM |

One root package everywhere, `io.github.youndie.haul`; a feature lives in `feature/<name>/` in every
module it touches, and only packages several features import (`db`, `seed`, `di`, `ops`, `theme`,
`registry`, `shell`, `ui`) sit at the root (research D3a).

## What runs on every pull request

```bash
make check                                                                  # the documentation gate
./gradlew check :server:installDist :composeApp:wasmJsBrowserDistribution  # the code gate
scripts/image-check.sh                                                      # the image, its AOT cache, the page it serves
scripts/chart-check.sh                                                      # the chart renders and refuses what it must
```

A change that touches only documentation (`docs/`, `backlog.md`, `README.md`, `CLAUDE.md`) runs the
documentation gate alone in CI: the code jobs report success with their steps skipped
(`scripts/code-changed.sh`). The default branch runs everything on every push.

The server's tests and the image check need Docker: PostgreSQL runs in Testcontainers, and the image
trains its cache against a PostgreSQL of its own (`docker/Dockerfile`).

## Screenshot goldens

Goldens (`composeApp/src/desktopTest/snapshots`) are recorded on Linux, where CI verifies them: the
bundled variable fonts rasterise a few glyph edges differently on macOS (research, risk 1). Record in a
Linux checkout and commit the PNGs; `viddikVerify` runs as part of `check`.

## Rules

- Code, comments, KDoc, test names, exception messages, commits and pull requests in English;
  documentation in English too (research D10).
- Commits in Conventional Commits.
- A change that would make a document a lie changes the document in the same pull request.
- A library is added by the item that first needs it, not up front.
