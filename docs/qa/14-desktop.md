# The desktop app — `TC-DESK`

The second client (B-176): the same storefront in a window on macOS, Windows or Linux, drawing the trees the
server sends with the same renderers as the browser. Walked from a checkout (`./gradlew :composeApp:run`, the stand;
`-Pserver=<origin>` for another server) or from the installer `packageDistributionForCurrentOS` builds. Sign-in
through the provider is not in it yet (B-176's question), so every case here is a guest's.

### TC-DESK-01 — The window browses the stand and fills a guest's cart, the pages the browser draws

- **Who:** guest
- **Width:** 1440
- **Preconditions:** the app started against the stand, its window about 1440 wide; the same address open in a
  browser beside it.
- **Steps:**
  1. Read the home page; open a category, a product and the search's results for «headphones».
  2. Press «+» on a card, then open «Cart».
  3. Go back twice with the Go menu (⌘[ on a Mac, Alt+← elsewhere), then forward once.
  4. Quit the app and start it again; open «Cart».
- **Expected:**
  - Step 1: each page as the browser draws it — the same trees, faces and pictures; the window's title is the page's.
  - Step 2: «Added to your cart», the header's count goes up, and the cart holds the line.
  - Step 3: each step lands on the page before, as the browser's back does; «Back» is greyed on the first page.
  - Step 4: the same cart: the guest is kept between runs, one per server.
- **Decided in:** B-176
- **Coverage:** `DesktopStoreTest` «the desktop app draws the home tree its server sends, as the guest the server issued»;
  `DesktopPartsTest` «back and forward move through the entries and tell the shell where they arrived»;
  `DesktopPartsTest` «the guest outlives the window, one per server, and a sign-in does not»;
  manual: the window, its menu and the system's preferences exist only in a desktop session, against a live server

### TC-DESK-02 — «Sign in» in the window leaves the shopper a guest with the cart

- **Who:** guest
- **Width:** 1440
- **Preconditions:** the app started against the stand, a line in the guest's cart.
- **Steps:**
  1. Press «Sign in» in the header.
  2. Press «Sign in to check out» in the cart.
- **Expected:**
  - Step 1: nothing opens and the page is drawn again as the guest's — the answer of a server with sign-in off — until
    the stand's provider accepts a loopback return address (B-176's question).
  - Step 2: the same; the cart keeps its line.
- **Decided in:** B-176
- **Coverage:** `DesktopPartsTest` «a sign-in in the window ends as on a server with sign-in off, the guest and the cart kept»;
  manual: what the provider accepts is the stand's configuration, which no test here reaches
