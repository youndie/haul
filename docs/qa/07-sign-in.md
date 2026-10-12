# Sign-in — `TC-SIGN`

Sign-in through shildik in a popup, the sign-in page `/sign-in` when the popup is blocked or the page is opened
directly, the prompt over a customer's page, sign-out. On the stand the provider is `haul-id.kotlin.website`
and only Maya and Sam can sign in; on a local stack, the e2e's shildik.

### TC-SIGN-01 — The header's sign-in opens a popup and draws the same page signed in

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a guest cart with a line; a product page shown.
- **Steps:**
  1. Press «Sign in» in the header.
  2. Sign in as Sam in the popup.
- **Expected:**
  - Step 1: a popup with the provider's page; the storefront stays on the product.
  - Step 2: the popup closes; the same product page is drawn again for Sam — his name in the header, the cart's
    count his merged cart's. No other page opens.
- **Decided in:** B-12, B-46, B-66
- **Coverage:** `StorefrontTest` «the header's sign-in is sign-in's and the screen is drawn again in place»;
  `IdentityTest` «signing in keeps the tokens and merges the guest cart with them»;
  `PopupSignInFlowTest` «a popup that answered and closed still signs the shopper in»;
  manual: the provider's form in a real popup is shildik's page on the stand's realm, which no test drives in a browser

### TC-SIGN-02 — A guest's «Orders» and «Saved» land on them once signed in

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the home page.
- **Steps:**
  1. Press «Orders» and sign in.
  2. Sign out, press «Saved» and sign in.
  3. As a guest, press a card's heart and sign in.
- **Expected:**
  - Step 1: `/account/orders` (the press carried `next`).
  - Step 2: `/account/saved`.
  - Step 3: the page the heart was on, drawn again signed in (the heart is not saved by the sign-in itself).
- **Decided in:** B-20, B-66
- **Coverage:** `SignInTapTest` «a sign-in asked with a next opens it once the shopper is signed in»;
  `SignInEverywhereTest` «on a phone the menu's orders signs a guest in and lands on them»;
  `StorefrontTest` «a sign-in that returns to the page shown draws it again in place»;
  `SavedRoutesTest` «hearts are commands for a customer and the way to sign in for a guest»

### TC-SIGN-03 — Closing the popup ends the sign-in at once

- **Who:** guest
- **Width:** 1440
- **Preconditions:** a page shown.
- **Steps:**
  1. Press «Sign in»; press it again while the popup is open.
  2. Close the popup without signing in.
  3. Press «Sign in» again.
- **Expected:**
  - Step 1: the open popup is brought forward; no second popup.
  - Step 2: within about a second the storefront is a guest's again, nothing changed, nothing pending.
  - Step 3: a new popup opens.
- **Decided in:** B-46
- **Coverage:** `PopupSignInFlowTest` «a second press while a sign-in is pending focuses its popup and opens no other»;
  `PopupSignInFlowTest` «a popup the shopper closes ends the sign-in as cancelled within a second»;
  manual: a real popup window closed by hand is the browser's

### TC-SIGN-04 — A blocked popup says so and offers the sign-in in the tab

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** the browser blocks popups for the store (the default for a press that did not come from a
  click, or set in the browser's site settings).
- **Steps:**
  1. Press «Orders».
  2. Press «Sign in on this page» and sign in on the provider's page.
  3. Repeat, and on the provider's page fail or go back.
- **Expected:**
  - Step 1: the sign-in page, «The browser blocked the window», with «Sign in on this page» and «Try the sign-in
    window again».
  - Step 2: the tab goes to the provider and back, «Signing you in», then `/account/orders` with the cart merged;
    back does not return to the sign-in page.
  - Step 3: the sign-in page says it did not go through and offers another try.
- **Decided in:** B-66, B-116
- **Coverage:** `SignInEverywhereTest` «a blocked popup says so and offers the sign-in on this page»;
  `SignInEverywhereTest` «a sign-in in this tab that comes back lands on its next»;
  `SignInEverywhereTest` «a sign-in in this tab that failed says so and offers another»;
  `SignInEverywhereTest` «back from the provider's page says the sign-in did not go through and offers another»;
  `SignInEverywhereTest` «a page the browser kept says the sign-in did not go through on the way back»;
  `PopupSignInFlowTest` «a blocked popup ends the sign-in as blocked without asking the server or the provider»;
  `IdentityTest` «a sign-in in this tab comes back signed in with the cart merged and its next»;
  `IdentityTest` «a sign-in in this tab the provider never answered is forgotten once it is told»;
  manual: a browser's popup blocker and the provider's page in the tab — the app's browser pane blocks every popup and is a good place to walk it

### TC-SIGN-05 — `/sign-in` opened directly is a page of its own

- **Who:** guest; Maya
- **Width:** 1440, 390
- **Preconditions:** none.
- **Steps:**
  1. As a guest, open `/sign-in?next=%2Faccount%2Fsaved` by its address (or reload it).
  2. Sign in from its button.
  3. Press back.
  4. As Maya, open `/sign-in?next=%2Faccount`.
  5. Open `/sign-in?next=https%3A%2F%2Fexample.com`, sign in.
  6. As a guest with pop-up windows blocked, open `/sign-in?next=%2Faccount%2Fsaved` and press the header's «Sign in».
- **Expected:**
  - Step 1: «Sign in to HAUL» with its button; not «This page isn't here».
  - Step 2: `/account/saved`.
  - Step 3: the page before the sign-in page, not the sign-in page (its entry was given to `next`).
  - Step 4: sent straight on to `/account`.
  - Step 5: signed in, staying on the store — a `next` that is not a storefront address is refused.
  - Step 6: «The browser blocked the window», the address still `/sign-in?next=%2Faccount%2Fsaved`: the sign-in on
    this page returns to the Saved list.
- **Decided in:** B-66, B-121
- **Coverage:** `SignInEverywhereTest` «a reload of the sign-in page asks, and the sign-in lands on its next»;
  `SignInEverywhereTest` «back and forward onto the sign-in page draw it»;
  `SignInEverywhereTest` «a customer arriving at the sign-in page goes on to its next»;
  `SignInEverywhereTest` «the header's sign-in blocked on the sign-in page keeps its next»;
  `SignInTapTest` «a next that is not a storefront address is refused»;
  `StorefrontTest` «a sign-in whose next is another site stays on the page»

### TC-SIGN-06 — A customer's page opened by a guest asks for a sign-in, once

- **Who:** guest
- **Width:** 1440, 390
- **Preconditions:** a line in the cart; note `haul.guest` in the page's `localStorage`.
- **Steps:**
  1. Open `/checkout` by its address.
  2. Open `/account/orders/HL-48302` (any order) by its address, then go to the cart.
  3. Open `/checkout` again, press «Sign in to continue» and sign in.
  4. Sign out, open `/account/orders/HL-48302` and close the sign-in window instead.
- **Expected:**
  - Step 1: «Checkout needs a sign-in», «sign-in» on one line at every width, and «Sign in to continue» — no error
    page, and no popup until the press. No `POST /api/v1/guests`.
  - Step 2: the same prompt; the cart still holds the line, the header counts it, and `haul.guest` is the one
    noted — «Whatever is in your cart comes with you» holds.
  - Step 3: the checkout, loaded again for the customer (or the cart, if nothing is selected — TC-CHK-11).
  - Step 4: the home page. A page still refused after a sign-in is an error page; the prompt does not loop.
- **Decided in:** B-44, B-66, B-115, B-121
- **Coverage:** `IdentityTest` «a guest on a customer's page stays the same guest»;
  `server:CartRoutesTest` «no guest is 401 unauthenticated and an unknown one 401 guest_not_found»;
  `SignInPromptTest` «a guest opening the checkout signs in and lands on the checkout»;
  `SignInPromptTest` «a guest opening an order signs in and lands on the order»;
  `SignInPromptTest` «a sign-in that does not go through lands on the home page»;
  `SignInPromptTest` «a page still refused after a sign-in draws the error page and asks no more»;
  `SignInTitleTest` «a sign-in prompt's title never breaks inside sign-in»

### TC-SIGN-07 — Signing out forgets the customer and keeps the guest

- **Who:** Maya
- **Width:** 1440, 390
- **Preconditions:** Maya signed in, on her account; then on a product page.
- **Steps:**
  1. On `/account`, open the account slot's menu and press «Sign out».
  2. Sign in again, open a product, sign out from the menu.
  3. Press «Sign in».
- **Expected:**
  - Step 1: the home page as a guest: «Sign in», no name, the guest's own (empty) cart.
  - Step 2: the same product drawn again for the guest.
  - Step 3: the provider asks for the password again.
- **Decided in:** B-12, B-66
- **Coverage:** `SignInEverywhereTest` «a customer signs out from the header and leaves a customer's page for home»;
  `SignInEverywhereTest` «signing out elsewhere draws the page again»;
  `IdentityTest` «signing out forgets the tokens and keeps the guest»;
  manual: whether the provider keeps a session of its own to sign back in from is shildik's

### TC-SIGN-08 — A lapsed sign-in is renewed quietly, or asks again

- **Who:** Maya
- **Width:** 1440
- **Preconditions:** Maya signed in; on the stand the access token lives five minutes.
- **Steps:**
  1. Leave her account page open for six minutes, then press a history chip.
  2. Sign in, then end the session at the provider (or wait until the refresh token is gone) and press again.
- **Expected:**
  - Step 1: the chip works; the token was renewed once and the request sent again — nothing seen.
  - Step 2: the page is taken down and asks for a sign-in (not an error page, and the customer's page is not left
    on screen for a guest); after it, the page again. Until then she is the guest she was before signing in: the
    same `haul.guest`, no `POST /api/v1/guests`.
- **Decided in:** B-12, B-44, B-62, B-115
- **Coverage:** `IdentityTest` «a lapsed token is renewed once and the request sent again»;
  `IdentityTest` «a token that cannot be renewed signs the customer out, back to the guest»;
  `IdentityTest` «a lapsed sign-in leaves the shopper the guest they were»;
  `KeptPageTest` «a sign-in lapsed within a screen takes the page down and asks for one»;
  `SignInPromptTest` «a lapsed sign-in that cannot be renewed asks again and lands on the page»;
  manual: the stand's five-minute token is real time, which the tests stand in for with a fake transport
