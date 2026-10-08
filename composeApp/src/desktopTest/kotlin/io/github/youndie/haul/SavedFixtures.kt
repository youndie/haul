package io.github.youndie.haul

import androidx.compose.runtime.Composable
import io.github.youndie.haul.shell.SavedError
import io.github.youndie.haul.shell.SavedLoading
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Saved list (B-20), at the artboard's size. Content, PriceDrops and Empty
// are the server's own trees (`resources/bodies/saved_*.json`, held equal to them by the server's
// `SavedFixturesTest`, which also says what in them is the canvas's — the cards) drawn through the app's
// registry. Loading and Error are the client's.

@ViddikScreenshot(name = "Loading", group = "Saved", width = 1440, height = 1659)
@Composable
internal fun SavedLoadingWide() = Fixture(compact = false) { Page { SavedLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Saved", width = 390, height = 2422)
@Composable
internal fun SavedLoadingPhone() = Fixture(compact = true) { Page { SavedLoading() } }

@ViddikScreenshot(name = "Content", group = "Saved", width = 1440, height = 1890)
@Composable
internal fun SavedContentWide() = Fixture(compact = false) { Page { Body("saved_content.json") } }

@ViddikScreenshot(name = "Content_Phone", group = "Saved", width = 390, height = 2727)
@Composable
internal fun SavedContentPhone() = Fixture(compact = true) { Page { Body("saved_content.json") } }

@ViddikScreenshot(name = "PriceDrops", group = "Saved", width = 1440, height = 1415)
@Composable
internal fun SavedPriceDropsWide() = Fixture(compact = false) { Page { Body("saved_price_drops.json") } }

@ViddikScreenshot(name = "PriceDrops_Phone", group = "Saved", width = 390, height = 1685)
@Composable
internal fun SavedPriceDropsPhone() = Fixture(compact = true) { Page { Body("saved_price_drops.json") } }

@ViddikScreenshot(name = "Empty", group = "Saved", width = 1440, height = 948)
@Composable
internal fun SavedEmptyWide() = Fixture(compact = false) { Page { Body("saved_empty.json") } }

@ViddikScreenshot(name = "Empty_Phone", group = "Saved", width = 390, height = 1316)
@Composable
internal fun SavedEmptyPhone() = Fixture(compact = true) { Page { Body("saved_empty.json") } }

@ViddikScreenshot(name = "Error", group = "Saved", width = 1440, height = 799)
@Composable
internal fun SavedErrorWide() = Fixture(compact = false) { Page { SavedError() } }

@ViddikScreenshot(name = "Error_Phone", group = "Saved", width = 390, height = 641)
@Composable
internal fun SavedErrorPhone() = Fixture(compact = true) { Page { SavedError() } }
