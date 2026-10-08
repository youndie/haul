package io.github.youndie.haul

import androidx.compose.runtime.Composable
import io.github.youndie.haul.shell.CartError
import io.github.youndie.haul.shell.CartLoading
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Cart screen (B-13), at the artboard's size. The six server states are
// the server's own trees (`resources/bodies/cart_*.json`, held equal to them by the server's
// `CartFixturesTest`) drawn through the app's registry; Loading and Error are the client's own.

@ViddikScreenshot(name = "Loading", group = "Cart", width = 1440, height = 1041)
@Composable
internal fun CartLoadingWide() = Fixture(compact = false) { Page { CartLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Cart", width = 390, height = 1220)
@Composable
internal fun CartLoadingPhone() = Fixture(compact = true) { Page { CartLoading() } }

@ViddikScreenshot(name = "Content", group = "Cart", width = 1440, height = 1179)
@Composable
internal fun CartContentWide() = Fixture(compact = false) { Page { Body("cart_content.json") } }

@ViddikScreenshot(name = "Content_Phone", group = "Cart", width = 390, height = 1668)
@Composable
internal fun CartContentPhone() = Fixture(compact = true) { Page { Body("cart_content.json") } }

@ViddikScreenshot(name = "Empty", group = "Cart", width = 1440, height = 1216)
@Composable
internal fun CartEmptyWide() = Fixture(compact = false) { Page { Body("cart_empty.json") } }

@ViddikScreenshot(name = "Empty_Phone", group = "Cart", width = 390, height = 1722)
@Composable
internal fun CartEmptyPhone() = Fixture(compact = true) { Page { Body("cart_empty.json") } }

@ViddikScreenshot(name = "PromoApplied", group = "Cart", width = 1440, height = 1179)
@Composable
internal fun CartPromoAppliedWide() = Fixture(compact = false) { Page { Body("cart_promo_applied.json") } }

@ViddikScreenshot(name = "PromoApplied_Phone", group = "Cart", width = 390, height = 1705)
@Composable
internal fun CartPromoAppliedPhone() = Fixture(compact = true) { Page { Body("cart_promo_applied.json") } }

@ViddikScreenshot(name = "PromoError", group = "Cart", width = 1440, height = 1179)
@Composable
internal fun CartPromoErrorWide() = Fixture(compact = false) { Page { Body("cart_promo_error.json") } }

@ViddikScreenshot(name = "PromoError_Phone", group = "Cart", width = 390, height = 1688)
@Composable
internal fun CartPromoErrorPhone() = Fixture(compact = true) { Page { Body("cart_promo_error.json") } }

@ViddikScreenshot(name = "ItemChanged", group = "Cart", width = 1440, height = 1263)
@Composable
internal fun CartItemChangedWide() = Fixture(compact = false) { Page { Body("cart_item_changed.json") } }

@ViddikScreenshot(name = "ItemChanged_Phone", group = "Cart", width = 390, height = 1776)
@Composable
internal fun CartItemChangedPhone() = Fixture(compact = true) { Page { Body("cart_item_changed.json") } }

@ViddikScreenshot(name = "Guest", group = "Cart", width = 1440, height = 1179)
@Composable
internal fun CartGuestWide() = Fixture(compact = false) { Page { Body("cart_guest.json") } }

@ViddikScreenshot(name = "Guest_Phone", group = "Cart", width = 390, height = 1607)
@Composable
internal fun CartGuestPhone() = Fixture(compact = true) { Page { Body("cart_guest.json") } }

@ViddikScreenshot(name = "Error", group = "Cart", width = 1440, height = 900)
@Composable
internal fun CartErrorWide() = Fixture(compact = false) { Page { CartError() } }

@ViddikScreenshot(name = "Error_Phone", group = "Cart", width = 390, height = 617)
@Composable
internal fun CartErrorPhone() = Fixture(compact = true) { Page { CartError() } }
