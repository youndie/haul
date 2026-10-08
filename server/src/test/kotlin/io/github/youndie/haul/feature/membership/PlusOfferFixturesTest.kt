package io.github.youndie.haul.feature.membership

import io.github.youndie.haul.feature.account.domain.Standing
import io.github.youndie.haul.feature.membership.screen.PlusOffer
import io.github.youndie.haul.haulWireJson
import io.github.youndie.haul.seed.SampleCustomers
import io.github.youndie.haul.shell.Viewer
import io.github.youndie.haul.ui.PlusBlock
import io.github.youndie.kompot.encodeKompotComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Home_PlusTrialDialog` is drawn from a wire body (`composeApp/src/desktopTest/resources/bodies/
 * home_plus_trial.json`): the home page as Sam sees it, the dialog presented from its Plus block. The page
 * around the block is the Content body's — «Picked for you» is held to the server's by `PickedSectionTest`
 * (B-25), its cards the canvas's — and the block and the dialog it presents are this item's, and this holds
 * them equal to what the server sends a customer who is not a member.
 */
class PlusOfferFixturesTest {
    private val bodies = File(System.getProperty("haul.clientBodies") ?: error("haul.clientBodies is not set"))

    @Test
    fun `the trial dialog's body carries the block and the dialog the server sends`() {
        val drawn =
            Json
                .parseToJsonElement(File(bodies, "home_plus_trial.json").readText())
                .jsonObject
                .getValue("children")
                .jsonArray
                .single { it.jsonObject["type"] == JsonPrimitive("haul_plus_block") }
        val built: PlusBlock =
            PlusOffer.block(
                Viewer(firstName = "Sam", customerId = SampleCustomers.SAM),
                Standing(0, null),
            )
        assertEquals(Json.parseToJsonElement(haulWireJson.encodeKompotComponent(built)), drawn)
    }
}
