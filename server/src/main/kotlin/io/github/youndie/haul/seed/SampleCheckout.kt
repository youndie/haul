package io.github.youndie.haul.seed

import io.github.youndie.haul.feature.checkout.data.PickupPointsTable.PARCEL_LOCKER
import io.github.youndie.haul.feature.checkout.data.PickupPointsTable.PICKUP_POINT
import io.github.youndie.haul.seed.SampleCustomers.MAYA

/**
 * What checkout offers on the sample data: the pickup points and the lockers near Wythe Avenue
 * (research §6, with the canvas's third point and second locker and the lockers' distances:
 * `Checkout_PickupPoint`, `Checkout_ParcelLocker`), and Maya's address. A point's hours are written as
 * its row says them («open until 21:00»), a locker's as «24/7».
 */
internal object SampleCheckout {
    const val BEDFORD = "point-214-bedford"
    const val NORTH_6TH = "point-96-n6th"
    const val GRAND = "point-315-grand"
    const val WYTHE_LOCKER = "locker-wythe-n7th"
    const val BEDFORD_LOCKER = "locker-bedford-station"
    const val MAYA_ADDRESS = "address-maya"

    val pickupPoints: List<SeedPickupPoint> =
        listOf(
            SeedPickupPoint(BEDFORD, PICKUP_POINT, "214 Bedford Ave", 240, "open until 21:00", 1),
            SeedPickupPoint(NORTH_6TH, PICKUP_POINT, "96 N 6th St", 650, "open until 22:00", 2),
            SeedPickupPoint(GRAND, PICKUP_POINT, "315 Grand St", 900, "open until 20:00", 3),
            SeedPickupPoint(WYTHE_LOCKER, PARCEL_LOCKER, "Wythe & N 7th", 180, "24/7", 4),
            SeedPickupPoint(BEDFORD_LOCKER, PARCEL_LOCKER, "Bedford Ave station", 700, "24/7", 5),
        )

    /**
     * «148 Wythe Avenue, Apt 4F, Brooklyn, NY 11211». The form has no state of its own (research §5:
     * street, apt, city, ZIP, door code, courier note), so the state is part of the city.
     */
    val addresses: List<SeedAddress> =
        listOf(SeedAddress(MAYA_ADDRESS, MAYA, "148 Wythe Avenue", "4F", "Brooklyn, NY", "11211"))
}
