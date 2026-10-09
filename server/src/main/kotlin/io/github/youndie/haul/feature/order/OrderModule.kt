package io.github.youndie.haul.feature.order

import io.github.youndie.haul.feature.order.data.ExposedOrders
import io.github.youndie.haul.feature.order.data.ExposedStock
import io.github.youndie.haul.feature.order.domain.OrderMoves
import io.github.youndie.haul.feature.order.domain.OrderRepository
import io.github.youndie.haul.feature.order.domain.Placement
import io.github.youndie.haul.feature.order.domain.Reorder
import io.github.youndie.haul.feature.order.domain.StockReservations
import io.github.youndie.haul.feature.order.saga.SagaStorage
import io.github.youndie.haul.feature.order.saga.orderEngine
import io.github.youndie.haul.feature.order.saga.orderSaga
import io.github.youndie.haul.feature.order.saga.orderSweeper
import io.github.youndie.haul.feature.order.screen.OrderScreen
import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import io.github.youndie.petich.PetichEngine
import io.github.youndie.petich.SuspendedPetichSweeper
import org.koin.dsl.module

/**
 * The order saga's graph: its storage, the engine with the one definition it runs, the sweeper that
 * carries on a saga whose process died, and placement; then the order's page and its reorder (B-18). The
 * saga's `PetichClock` comes from the application, which reads the wall clock once (`Application.kt`). The
 * order's live page (B-29): the moves the simulators tell, and kompot's broadcaster over its in-memory bus,
 * which [LiveOrders] draws them through; the application starts it.
 */
internal val orderModule =
    module {
        single { SagaStorage(get(), get()) }
        single<OrderRepository> { ExposedOrders(get()) }
        single<StockReservations> { ExposedStock(get()) }
        single<PetichEngine> {
            orderEngine(
                storage = get(),
                clock = get(),
                definitions =
                    listOf(
                        orderSaga(
                            stock = get(),
                            slots = get(),
                            orders = get(),
                            payments = get(),
                            carts = get(),
                            points = get(),
                        ),
                    ),
            )
        }
        // An explicit lambda: `stuckAfter` is a defaulted parameter, which Koin would otherwise ask for.
        single<SuspendedPetichSweeper> { orderSweeper(get(), get(), get()) }
        single {
            Placement(
                checkout = get(),
                orders = get(),
                engine = get(),
                sagas = get<SagaStorage>().sagas,
                keys = get<SagaStorage>().idempotencyKeys,
                clock = get(),
            )
        }
        single { OrderScreen(tracking = get(), catalog = get(), checkout = get(), reviews = get(), clock = get()) }
        single { Reorder(orders = get(), carts = get(), catalog = get(), commands = get()) }
        single { OrderMoves() }
        // An explicit lambda: the bus is a defaulted parameter (kompot's in-memory one).
        single { KompotUpdateBroadcaster() }
        single {
            LiveOrders(
                moves = get(),
                orders = get(),
                tracking = get(),
                screen = get(),
                customers = get(),
                broadcaster = get(),
            )
        }
    }
