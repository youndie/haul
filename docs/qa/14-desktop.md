# The desktop app — `TC-DESK`

The second client (B-176): the same storefront in a window on macOS, Windows or Linux, drawing the trees the
server sends with the same renderers as the browser. Walked from a checkout (`./gradlew :composeApp:run`, the stand;
`-Pserver=<origin>` for another server) or from the installer `packageDistributionForCurrentOS` builds. Since B-178 it
signs in through the system's browser, against a provider that accepts the loopback return address the chart
registers (`shildik.desktopRedirectUri`, shildik 0.5.0 and later).

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
  `DesktopPartsTest` «the guest and a sign-in outlive the window, one per server»;
  manual: the window, its menu and the system's preferences exist only in a desktop session, against a live server

### TC-DESK-02 — «Sign in» in the window signs in through the system's browser and keeps the cart

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** the app started against the stand (or a local stack whose realm registers the desktop's
  address), signed out, a line in the guest's cart; a browser installed and set as the system's.
- **Steps:**
  1. Press «Sign in» in the header.
  2. Sign Maya in on the provider's page in the browser.
  3. Go back to the window; open «Cart», then «Account».
  4. Quit the app and start it again; open «Account».
  5. Sign out from the header's account menu.
- **Expected:**
  - Step 1: the system's browser opens the provider's sign-in page; the window stays where it was.
  - Step 2: the browser lands on «Signed in to Haul» («You can close this tab and go back to the app»), at an
    address `http://127.0.0.1:<port>/signed-in`.
  - Step 3: the header is Maya's; her cart holds the guest's line beside her own (the merge, research D5); the
    account is hers.
  - Step 4: still Maya's — a sign-in outlives the window, one per server.
  - Step 5: the guest again, with an empty cart; after a restart still the guest.
- **Decided in:** B-12, B-176, B-178
- **Coverage:** `DesktopSignInTest` «Maya signs in in the system browser, the code returns to a loopback port, the guest cart is merged»;
  `DesktopSignInTest` «signing out forgets the tokens here and in the preferences, and the next request is the guest's»;
  `DesktopPartsTest` «the guest and a sign-in outlive the window, one per server»;
  manual: the system's browser, the provider's real page and the window's header exist only in a desktop session against a live stand

### TC-DESK-03 — A sign-in past its token's five minutes is renewed, a demo shopper's too

- **Who:** Maya; guest
- **Width:** 1440
- **Preconditions:** Maya signed in in the window (TC-DESK-02); for step 3, a server with the demo shopper on.
- **Steps:**
  1. Leave the window for more than five minutes, then open «Account».
  2. Quit the app, wait more than five minutes, start it again and open «Account».
  3. Sign out; press «Try as a demo shopper»; leave the window for more than five minutes, then open «Cart».
- **Expected:**
  - Step 1: the account, with no sign-in asked for: the token was renewed with the refresh token.
  - Step 2: the same.
  - Step 3: the demo shopper's cart, still signed in — B-176's desktop let this token lapse after five minutes.
- **Decided in:** B-12, B-162, B-178
- **Coverage:** `DesktopSignInTest` «a token past its lifetime is renewed at the provider, which rotates the refresh token»;
  `DesktopSignInTest` «a demo shopper's token, signed in at the browser's return page, is renewed on the desktop too»;
  manual: the provider's own five minutes pass only on a live stand

### TC-DESK-04 — A sign-in left in the browser can be picked up again, and ends without one

- **Who:** guest
- **Width:** 1440
- **Preconditions:** the app started against the stand, signed out.
- **Steps:**
  1. Press «Sign in», close the browser's tab without signing in, and press «Sign in» again.
  2. Sign in on the page that opened.
  3. Sign out; press «Sign in» and leave the provider's page for more than ten minutes.
  4. On a server with sign-in off (a local server without `HAUL_OIDC_*`), press «Sign in».
- **Expected:**
  - Step 1: the same sign-in's page opens again; no second sign-in is started.
  - Step 2: signed in, as in TC-DESK-02.
  - Step 3: after ten minutes the sign-in ends and the window stays the guest's; finished in the browser after that,
    the provider's page returns to a port nothing listens on any more.
  - Step 4: no browser opens; the page is drawn again as the guest's, the cart kept.
- **Decided in:** B-178
- **Coverage:** `DesktopSignInTest` «a press while a sign-in is under way opens its page again and starts no second one»;
  `DesktopSignInTest` «a sign-in nobody finishes ends as one that did not go through, and its port is closed»;
  `DesktopPartsTest` «a sign-in on a server with sign-in off opens no browser, the guest and the cart kept»;
  manual: a tab the person closes is the system's browser's, which no test drives
