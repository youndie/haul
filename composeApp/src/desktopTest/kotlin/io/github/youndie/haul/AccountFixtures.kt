package io.github.youndie.haul

import androidx.compose.runtime.Composable
import io.github.youndie.haul.shell.AccountError
import io.github.youndie.haul.shell.AccountLoading
import io.github.youndie.viddik.annotations.ViddikScreenshot

// One fixture per artboard of the Account screen (B-19), at the artboard's size. Content, Orders, NotMember
// and NoOrders are the server's own trees (`resources/bodies/account_*.json`, held equal to them by the
// server's `AccountFixturesTest`, which also says whose they are and what in them is the canvas's) drawn
// through the app's registry. Loading and Error are the client's.

@ViddikScreenshot(name = "Loading", group = "Account", width = 1440, height = 1014)
@Composable
internal fun AccountLoadingWide() = Fixture(compact = false) { Page { AccountLoading() } }

@ViddikScreenshot(name = "Loading_Phone", group = "Account", width = 390, height = 1403)
@Composable
internal fun AccountLoadingPhone() = Fixture(compact = true) { Page { AccountLoading() } }

@ViddikScreenshot(name = "Content", group = "Account", width = 1440, height = 1629)
@Composable
internal fun AccountContentWide() = Fixture(compact = false) { Page { Body("account_content.json") } }

@ViddikScreenshot(name = "Content_Phone", group = "Account", width = 390, height = 2255)
@Composable
internal fun AccountContentPhone() = Fixture(compact = true) { Page { Body("account_content.json") } }

@ViddikScreenshot(name = "NotMember", group = "Account", width = 1440, height = 1112)
@Composable
internal fun AccountNotMemberWide() = Fixture(compact = false) { Page { Body("account_not_member.json") } }

@ViddikScreenshot(name = "NotMember_Phone", group = "Account", width = 390, height = 1378)
@Composable
internal fun AccountNotMemberPhone() = Fixture(compact = true) { Page { Body("account_not_member.json") } }

@ViddikScreenshot(name = "Orders", group = "Account", width = 1440, height = 1067)
@Composable
internal fun AccountOrdersWide() = Fixture(compact = false) { Page { Body("account_orders.json") } }

@ViddikScreenshot(name = "Orders_Phone", group = "Account", width = 390, height = 1332)
@Composable
internal fun AccountOrdersPhone() = Fixture(compact = true) { Page { Body("account_orders.json") } }

@ViddikScreenshot(name = "NoOrders", group = "Account", width = 1440, height = 708)
@Composable
internal fun AccountNoOrdersWide() = Fixture(compact = false) { Page { Body("account_no_orders.json") } }

@ViddikScreenshot(name = "NoOrders_Phone", group = "Account", width = 390, height = 693)
@Composable
internal fun AccountNoOrdersPhone() = Fixture(compact = true) { Page { Body("account_no_orders.json") } }

@ViddikScreenshot(name = "Error", group = "Account", width = 1440, height = 900)
@Composable
internal fun AccountErrorWide() = Fixture(compact = false) { Page { AccountError() } }

@ViddikScreenshot(name = "Error_Phone", group = "Account", width = 390, height = 641)
@Composable
internal fun AccountErrorPhone() = Fixture(compact = true) { Page { AccountError() } }
