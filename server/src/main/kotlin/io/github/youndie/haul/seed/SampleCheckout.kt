package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.checkout.data.PickupPointsTable.PARCEL_LOCKER
import io.github.youndie.haul.feature.checkout.data.PickupPointsTable.PICKUP_POINT
import io.github.youndie.haul.seed.SampleCustomers.MAYA

/**
 * What checkout offers on the sample data (research §6): the pickup points and the locker near
 * Wythe Avenue, and Maya's address.
 */
internal object SampleCheckout {
    const val BEDFORD = "point-214-bedford"
    const val NORTH_6TH = "point-96-n6th"
    const val WYTHE_LOCKER = "locker-wythe-n7th"
    const val MAYA_ADDRESS = "address-maya"

    val pickupPoints: List<SeedPickupPoint> =
        listOf(
            SeedPickupPoint(BEDFORD, PICKUP_POINT, "214 Bedford Ave", 240, "until 21:00", 1),
            SeedPickupPoint(NORTH_6TH, PICKUP_POINT, "96 N 6th St", 650, "until 22:00", 2),
            // Research §6 gives the locker no distance.
            SeedPickupPoint(WYTHE_LOCKER, PARCEL_LOCKER, "Wythe & N 7th", null, "24/7", 3),
        )

    /**
     * «148 Wythe Avenue, Apt 4F, Brooklyn, NY 11211». The form has no state of its own (research §5:
     * street, apt, city, ZIP, door code, courier note), so the state is part of the city.
     */
    val addresses: List<SeedAddress> =
        listOf(SeedAddress(MAYA_ADDRESS, MAYA, "148 Wythe Avenue", "4F", "Brooklyn, NY", "11211"))
}
