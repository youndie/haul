package io.github.youndie.haul

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.db.FailFastDataSource
import io.github.youndie.haul.feature.about.aboutRouting
import io.github.youndie.haul.feature.account.accountModule
import io.github.youndie.haul.feature.account.accountRouting
import io.github.youndie.haul.feature.cart.cartModule
import io.github.youndie.haul.feature.cart.cartRouting
import io.github.youndie.haul.feature.cart.domain.CartError
import io.github.youndie.haul.feature.catalog.OneCategoryAddress
import io.github.youndie.haul.feature.catalog.catalogModule
import io.github.youndie.haul.feature.catalog.catalogRouting
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.checkout.checkoutModule
import io.github.youndie.haul.feature.checkout.checkoutRouting
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.checkout.domain.TestCardAccess
import io.github.youndie.haul.feature.fulfilment.FulfilmentRunner
import io.github.youndie.haul.feature.fulfilment.FulfilmentSettings
import io.github.youndie.haul.feature.fulfilment.fulfilmentModule
import io.github.youndie.haul.feature.identity.DemoOffer
import io.github.youndie.haul.feature.identity.DemoShopperConfig
import io.github.youndie.haul.feature.identity.SignInConfig
import io.github.youndie.haul.feature.identity.customerIdentityRouting
import io.github.youndie.haul.feature.identity.data.ExposedDemoShoppers
import io.github.youndie.haul.feature.identity.data.ShildikDemoAccounts
import io.github.youndie.haul.feature.identity.domain.DemoShoppers
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.identity.identityModule
import io.github.youndie.haul.feature.identity.identityRouting
import io.github.youndie.haul.feature.identity.installSignIn
import io.github.youndie.haul.feature.live.LiveFeed
import io.github.youndie.haul.feature.live.liveModule
import io.github.youndie.haul.feature.live.liveRouting
import io.github.youndie.haul.feature.membership.domain.MembershipError
import io.github.youndie.haul.feature.membership.membershipModule
import io.github.youndie.haul.feature.membership.membershipRouting
import io.github.youndie.haul.feature.order.LiveOrders
import io.github.youndie.haul.feature.order.domain.OrderError
import io.github.youndie.haul.feature.order.domain.OrderMoves
import io.github.youndie.haul.feature.order.orderModule
import io.github.youndie.haul.feature.order.orderRouting
import io.github.youndie.haul.feature.order.saga.sweeperPatience
import io.github.youndie.haul.feature.payment.paymentModule
import io.github.youndie.haul.feature.recommendations.recommendationsModule
import io.github.youndie.haul.feature.returns.domain.ReturnError
import io.github.youndie.haul.feature.returns.returnsModule
import io.github.youndie.haul.feature.returns.returnsRouting
import io.github.youndie.haul.feature.reviews.domain.ReviewError
import io.github.youndie.haul.feature.reviews.reviewsModule
import io.github.youndie.haul.feature.reviews.reviewsRouting
import io.github.youndie.haul.feature.saved.savedModule
import io.github.youndie.haul.feature.saved.savedRouting
import io.github.youndie.haul.feature.search.customerSearchRouting
import io.github.youndie.haul.feature.search.domain.SearchError
import io.github.youndie.haul.feature.search.searchModule
import io.github.youndie.haul.feature.search.searchRouting
import io.github.youndie.haul.ops.Leader
import io.github.youndie.haul.ops.ObservabilitySettings
import io.github.youndie.haul.ops.PostgresBus
import io.github.youndie.haul.ops.Replicas
import io.github.youndie.haul.ops.installObservability
import io.github.youndie.haul.ops.probes
import io.github.youndie.haul.seed.SaleRedater
import io.github.youndie.haul.seed.SeedArt
import io.github.youndie.haul.shell.TreeTiming
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.SuspendedPetichSweeper
import io.github.youndie.shildik.oidc.JWT_AUTH_OIDC
import io.github.youndie.vojak.Election
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import java.io.File
import java.time.ZonedDateTime
import javax.sql.DataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** «Now» for everything that shows a date, read in one place so a test can hold it still. */
internal fun interface StoreClock {
    fun now(): ZonedDateTime
}

/**
 * The application as both `main` and the tests assemble it: the agents that watch it, DI, error
 * answers, and every route under its tier. Nothing here reads the environment or the clock; [clock],
 * [dataSource], [observability], [web] and [signIn] come in.
 *
 * Everything here reaches [dataSource] through a [FailFastDataSource] (B-127): with the database gone, a request
 * waits for a connection once — the pool's timeout — and not again for each query and each of Exposed's retries.
 * The order saga's work — placement's pass and the sweeper — waits longer, under its `ConnectionPatience` (B-139).
 *
 * [web] is the browser bundle's directory, served at `/` beside the API when given ([webBundle]: its
 * precompressed files, its cache headers). Every API route is more specific than the static one, so a
 * screen route always wins. The page answers the storefront's own addresses ([StorefrontPage], B-36),
 * so a reloaded or shared `/p/...` opens; there is deliberately no catch-all fallback to `index.html` —
 * it would answer an unknown `/ui/...` with a page and a 200 instead of the 404 the client draws. A storefront
 * address whose query does not decode is the page too, answered before routing ([PageAtAnyQuery], B-117).
 *
 * [photoStore] is the object storage product photos are kept in (B-30); `null` — no storage
 * configured — serves no stored photos, and every product is drawn with the seed's picture of it (B-78).
 *
 * [sagaClock] is the order saga's clock (B-16): a wall clock, apart from [clock], because what it
 * stamps is compared across processes — the sweeper takes a saga untouched for a minute as abandoned
 * by a process that died — while [clock] is the store's «now», which the tests hold at the canvas's.
 *
 * [fulfilment] is the simulated world's pace and whether it runs here (B-17): `main` runs it at
 * `HAUL_FULFILMENT_SPEED`; a test gets [FulfilmentSettings.MANUAL] and moves shipments by calling the
 * simulator itself. Its clock is [sagaClock], for the same reason: its stamps outlive the process.
 *
 * [testCard] is who may choose the test card ···· 0002 (B-89): `main` opens it to requests carrying
 * `HAUL_TEST_CARD_KEY`, the synthetic shoppers'; [TestCardAccess.NONE] — a test that does not say — opens it to nobody.
 *
 * [saleLook] keeps a seeded stand's sample sale on the store's day while it runs (B-70, [SaleRedater]): looked
 * at each store midnight and at least every [saleLook]. `main` passes it only when it seeds (`HAUL_SEED`); `null`
 * — a test, or a store that was never seeded — runs nothing.
 *
 * [replicas] says whether this is the only server on its database (B-174): [Replicas.Alone] — a test — runs the
 * sweeper, the simulated world and the sweeps here and hears only its own moves; `main` is always
 * [Replicas.Shared], whatever the chart's count, and [shareWithReplicas] says what that changes.
 */
internal fun Application.haulModule(
    dataSource: DataSource,
    clock: StoreClock,
    commit: String,
    observability: ObservabilitySettings = ObservabilitySettings.NONE,
    web: File? = null,
    photoStore: PhotoStore? = null,
    signIn: SignInConfig? = null,
    sagaClock: PetichClock,
    fulfilment: FulfilmentSettings = FulfilmentSettings.MANUAL,
    saleLook: Duration? = null,
    testCard: TestCardAccess = TestCardAccess.NONE,
    demo: DemoShopperConfig? = null,
    demoSweep: Duration? = null,
    replicas: Replicas = Replicas.Alone,
) {
    require(demo == null || signIn != null) { "a demo shopper needs sign-in: the realm they are made in" }
    val observers = installObservability(observability)
    installSignIn(signIn)
    val pool: DataSource = FailFastDataSource(dataSource)
    val database = Databases.connect(pool)
    install(Koin) {
        modules(
            module {
                single { database }
                single { pool }
                single { clock }
                single { sagaClock }
                single { fulfilment.pace }
                single { DeliveryCalendar(clock::now) }
                single { ProductPhotos(photoStore, SeedArt) }
                single { observers.trail }
                single { testCard }
                single {
                    DemoOffer(
                        demo?.let { config ->
                            DemoShoppers(
                                ShildikDemoAccounts(checkNotNull(signIn), config),
                                ExposedDemoShoppers(get()),
                                get(),
                                get(),
                                get(),
                                config.dailyLimit,
                            )
                        },
                    )
                }
            },
            catalogModule,
            searchModule,
            identityModule,
            cartModule,
            checkoutModule,
            paymentModule,
            orderModule,
            fulfilmentModule,
            reviewsModule,
            savedModule,
            accountModule,
            returnsModule,
            membershipModule,
            recommendationsModule,
            liveModule,
        )
    }
    // The order's live page (B-29): listening to the moves before anything can make one.
    get<LiveOrders>().start(this)
    // «Live orders» (B-164): the same moves, drawn for everybody, over the broadcaster the line above started.
    get<LiveFeed>().start(this)
    // What runs once among the replicas (B-174), in the scope it is handed — the application's when alone, the
    // leadership's when shared, which ends when this replica stops leading.
    val singletons: (CoroutineScope) -> Unit = { scope ->
        // Carries on what a process that died left mid-saga. Saga work: it waits out a short outage (B-139).
        get<SuspendedPetichSweeper>().start(scope + sweeperPatience())
        // The simulated world after placement: shipments move and are charged as they ship, returns are refunded,
        // and Haul Pay's payments are taken as they come due.
        fulfilment.interval?.let { FulfilmentRunner(get(), get(), get(), it).start(scope) }
        // The sample sale moved to the new day at each store midnight, so a stand that runs for days keeps its deals.
        saleLook?.let { SaleRedater(database, clock).start(scope, it) }
        // Demo shoppers older than a day forgotten, with everything they did (B-162).
        demoSweep?.let { get<DemoOffer>().startSweeping(scope, it) }
    }
    when (replicas) {
        Replicas.Alone -> singletons(this)
        is Replicas.Shared -> shareWithReplicas(replicas, pool, singletons)
    }
    // Server-sent events, the live pages' stream (B-29, B-164).
    install(SSE)
    install(StatusPages) {
        // A bearer token that did not verify, or none where the customer tier needs one: the
        // authentication challenge answers with an empty body, and every refusal here has one.
        status(HttpStatusCode.Unauthorized) {
            if (content is UnauthorizedResponse) {
                call.respondError(ErrorCode.Unauthenticated, "Sign in: no valid token came with the request", null)
            }
        }
        exception<CatalogError> { call, error -> call.respondError(error.code, error.message, error.field) }
        exception<SearchError> { call, error -> call.respondError(error.code, error.message, error.field) }
        exception<IdentityError> { call, error ->
            // A guest the cart's routes do not know is a credential refused, as `unauthenticated` is; the
            // merge's `guest_not_found` names a missing guest and stays `404` (B-115).
            val status = if (error is IdentityError.UnknownGuest) HttpStatusCode.Unauthorized else status(error.code)
            call.respondError(error.code, error.message, error.field, status = status)
        }
        exception<CartError> { call, error -> call.respondError(error.code, error.message, error.field) }
        exception<OrderError> { call, error -> call.respondError(error.code, error.message, error.field) }
        exception<ReturnError> { call, error ->
            call.respondError(error.code, error.message, error.field, error.fields)
        }
        exception<MembershipError> { call, error -> call.respondError(error.code, error.message) }
        exception<ReviewError> { call, error ->
            call.respondError(error.code, error.message, error.field, error.fields)
        }
        exception<CheckoutError> {
            call,
            error,
            ->
            call.respondError(error.code, error.message, error.field, error.fields)
        }
        unexpectedFailures(report = observers.reportFailure)
    }
    // Every tree says how long the server took over it, for «Under the hood» (B-163); first, so a redirect has it too.
    install(TreeTiming)
    // A category answers at one address; any other form of it is redirected there (B-68).
    install(OneCategoryAddress)
    // A page's address is the page whatever its query, one that does not decode included (B-117).
    web?.let { install(PageAtAnyQuery) { bundle = it } }
    routing {
        probes(commit = commit, ready = { databaseAnswers(pool) })
        // What a crawler may fetch: everything (B-161).
        robots()
        // Public: a guest, a customer or nobody. A token is optional, and one that does not verify is
        // refused rather than ignored — a shopper whose sign-in lapsed is told, not shown as a guest.
        authenticate(JWT_AUTH_OIDC, optional = true) {
            catalogRouting()
            searchRouting()
            identityRouting(signIn)
            cartRouting()
            aboutRouting()
            // «Live orders» and the stream every live page listens on, the order's page's included (B-164).
            liveRouting()
        }
        // Customer: a verified shildik token, or `401 unauthenticated`.
        authenticate(JWT_AUTH_OIDC) {
            customerIdentityRouting()
            customerSearchRouting()
            accountRouting()
            savedRouting()
            checkoutRouting()
            reviewsRouting()
            orderRouting()
            returnsRouting()
            membershipRouting()
        }
        web?.let { webBundle(it) }
    }
}

/**
 * One replica of several (B-174): the moves this process makes go to the others and theirs come here, through
 * PostgreSQL ([PostgresBus] on [Replicas.MOVES]), so a page is live whichever replica it is connected to; and
 * [singletons] run on the elected [Leader] only, started when this replica takes the leadership and cancelled when
 * it loses it. Stopping closes the election first, while the pool is still open, so the release is a clean one and
 * the next leader takes over within a poll rather than after vojak's `localLease`.
 */
private fun Application.shareWithReplicas(
    replicas: Replicas.Shared,
    pool: DataSource,
    singletons: (CoroutineScope) -> Unit,
) {
    val moves = get<OrderMoves>()
    PostgresBus(Replicas.MOVES, replicas.listen, pool).start(this, moves.madeHere, moves::heard)
    val leader = Leader(replicas.leases, Leader.candidate(replicas.name), replicas.timing)
    val election = CompletableDeferred<Election>()
    launch { election.complete(leader.elect(this@shareWithReplicas, singletons)) }
    monitor.subscribe(ApplicationStopping) {
        runBlocking { withTimeoutOrNull(ELECTION_CLOSE) { election.await().close() } }
    }
}

/** The most stopping waits for the leader's work to stop and its lease to be given back. */
private val ELECTION_CLOSE = 5.seconds

/** The one shape of every refusal: [ErrorBody] with the status its [code] maps to, unless [status] says otherwise. */
internal suspend fun ApplicationCall.respondError(
    code: ErrorCode,
    message: String,
    field: String? = null,
    fields: List<FieldError> = emptyList(),
    status: HttpStatusCode = status(code),
) = respondText(
    haulWireJson.encodeToString(ErrorBody.serializer(), ErrorBody(code, message, field, fields)),
    ContentType.Application.Json,
    status,
)

internal fun status(code: ErrorCode): HttpStatusCode =
    when (code) {
        ErrorCode.ValidationFailed,
        ErrorCode.QueryTooShort,
        ErrorCode.FieldRequired,
        ErrorCode.FieldInvalid,
        ErrorCode.IdempotencyKeyMissing,
        -> HttpStatusCode.BadRequest

        ErrorCode.Unauthenticated -> HttpStatusCode.Unauthorized

        ErrorCode.CategoryNotFound,
        ErrorCode.ProductNotFound,
        ErrorCode.OrderNotFound,
        ErrorCode.SkuNotFound,
        ErrorCode.LineNotFound,
        ErrorCode.PromoNotFound,
        ErrorCode.GuestNotFound,
        ErrorCode.SlotNotFound,
        ErrorCode.PickupPointNotFound,
        ErrorCode.AddressNotFound,
        ErrorCode.ReviewNotFound,
        -> HttpStatusCode.NotFound

        ErrorCode.OutOfStock,
        ErrorCode.PromoAlreadyApplied,
        ErrorCode.CartEmpty,
        ErrorCode.SlotUnavailable,
        ErrorCode.IdempotencyKeyReused,
        ErrorCode.CartChanged,
        ErrorCode.CheckoutHeld,
        ErrorCode.ReviewExists,
        ErrorCode.OwnReview,
        ErrorCode.AlreadyReturned,
        ErrorCode.AlreadyMember,
        -> HttpStatusCode.Conflict

        ErrorCode.PromoExpired,
        ErrorCode.PromoNotApplicable,
        ErrorCode.PaymentMethodNotAllowed,
        ErrorCode.ReturnWindowClosed,
        ErrorCode.NotDelivered,
        -> HttpStatusCode.UnprocessableEntity

        ErrorCode.Unavailable -> HttpStatusCode.ServiceUnavailable

        ErrorCode.Internal -> HttpStatusCode.InternalServerError
    }
