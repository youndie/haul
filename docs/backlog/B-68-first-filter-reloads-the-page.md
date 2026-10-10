---
id: B-68
title: "server + client: the first filter on a category does not reload the page"
status: open
priority: P1
size: S
stage: stage-10-review
---

# B-68 — server + client: the first filter on a category does not reload the page

Seen on the stand: on `/c/electronics/headphones`, ticking «Sony» sent `GET /ui/parts/c/headphones?brand=Sony` and
then `GET /ui/c/headphones?brand=Sony` — the whole page right after its parts. The `update`'s `deeplink` names the
category by its last segment (`/c/headphones`), the page was opened at the full path (`/c/electronics/headphones`);
the shell keys the screen by path (B-62), so the recorded address counts as another screen and is loaded. The second
tick (path already `/c/headphones`) sent only the parts. The address bar also changes shape under the shopper.

- **Decided as product owner:** a category has one address. Pick one form (the full path, as the breadcrumbs and
  the category links use), make every link, `update.deeplink` and `load` URL use it, and answer the other form with
  a redirect to it.
- AC: a facet tick from a category opened by any link sends exactly one request (the parts); the address keeps its
  form; the old form redirects; a server test over every category link and every parts deeplink.
- Anchors: `server/src/main/kotlin/io/github/youndie/haul/feature/catalog/screen/CatalogScreen.kt`, `server/src/main/kotlin/io/github/youndie/haul/shell/Parts.kt`, `composeApp/src/commonMain/kotlin/io/github/youndie/haul/shell/Navigation.kt`.
