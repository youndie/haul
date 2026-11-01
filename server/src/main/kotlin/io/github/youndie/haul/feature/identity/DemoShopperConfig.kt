package io.github.youndie.haul.feature.identity

/**
 * Where demo shoppers are made (B-162, `ServerConfig.demoShoppers`): the realm's management API at [management]
 * (`http://<release>-shildik:9000` on a stand, never the public host, which has no such routes), reached with the
 * bearer [managementToken], and the storefront client's return page [redirectUri], which their sign-in names as the
 * browser's does. At most [dailyLimit] are made in any day.
 */
internal data class DemoShopperConfig(
    val management: String,
    val managementToken: String,
    val redirectUri: String,
    val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
) {
    /** The token opens the realm's management: printed, it says nothing. */
    override fun toString(): String =
        "DemoShopperConfig(management=$management, managementToken=<hidden>, redirectUri=$redirectUri, dailyLimit=$dailyLimit)"

    companion object {
        /**
         * Five hundred a day: well past the visitors a demo stand has, and a ceiling on the people one script can
         * leave in the realm and the rows it can leave in the store before the sweep catches up.
         */
        const val DEFAULT_DAILY_LIMIT = 500
    }
}
