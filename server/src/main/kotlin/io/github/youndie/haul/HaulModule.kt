package io.github.youndie.haul

import io.github.youndie.haul.db.Databases
import io.github.youndie.haul.feature.account.accountModule
import io.github.youndie.haul.feature.account.accountRouting
import io.github.youndie.haul.feature.cart.cartModule
import io.github.youndie.haul.feature.cart.cartRouting
import io.github.youndie.haul.feature.cart.domain.CartError
import io.github.youndie.haul.feature.catalog.catalogModule
import io.github.youndie.haul.feature.catalog.catalogRouting
import io.github.youndie.haul.feature.catalog.domain.CatalogError
import io.github.youndie.haul.feature.catalog.domain.DeliveryCalendar
import io.github.youndie.haul.feature.catalog.domain.PhotoStore
import io.github.youndie.haul.feature.catalog.domain.ProductPhotos
import io.github.youndie.haul.feature.checkout.checkoutModule
import io.github.youndie.haul.feature.checkout.checkoutRouting
import io.github.youndie.haul.feature.checkout.domain.CheckoutError
import io.github.youndie.haul.feature.fulfilment.FulfilmentRunner
import io.github.youndie.haul.feature.fulfilment.FulfilmentSettings
import io.github.youndie.haul.feature.fulfilment.fulfilmentModule
import io.github.youndie.haul.feature.identity.SignInConfig
import io.github.youndie.haul.feature.identity.customerIdentityRouting
import io.github.youndie.haul.feature.identity.domain.IdentityError
import io.github.youndie.haul.feature.identity.identityModule
import io.github.youndie.haul.feature.identity.identityRouting
import io.github.youndie.haul.feature.identity.installSignIn
import io.github.youndie.haul.feature.order.domain.OrderError
import io.github.youndie.haul.feature.order.orderModule
import io.github.youndie.haul.feature.order.orderRouting
import io.github.youndie.haul.feature.payment.paymentModule
import io.github.youndie.haul.feature.returns.domain.ReturnError
import io.github.youndie.haul.feature.returns.returnsModule
import io.github.youndie.haul.feature.returns.returnsRouting
import io.github.youndie.haul.feature.reviews.domain.ReviewError
import io.github.youndie.haul.feature.reviews.reviewsModule
import io.github.youndie.haul.feature.reviews.reviewsRouting
import io.github.youndie.haul.feature.search.customerSearchRouting
import io.github.youndie.haul.feature.search.domain.SearchError
import io.github.youndie.haul.feature.search.searchModule
import io.github.youndie.haul.feature.search.searchRouting
import io.github.youndie.haul.ops.ObservabilitySettings
import io.github.youndie.haul.ops.installObservability
import io.github.youndie.haul.ops.probes
import io.github.youndie.petich.PetichClock
import io.github.youndie.petich.SuspendedPetichSweeper
import io.github.youndie.shildik.oidc.JWT_AUTH_OIDC
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import java.io.File
import java.time.ZonedDateTime
import javax.sql.DataSource

/** «Now» for everything that shows a date, read in one place so a test can hold it still. */
internal fun interface StoreClock {
    fun now(): ZonedDateTime
}

/**
 * The application as both `main` and the tests assemble it: the agents that watch it, DI, error
 * answers, and every route under its tier. Nothing here reads the environment or the clock; [clock],
 * [dataSource], [observability], [web] and [signIn] come in.
 *
 * [web] is the browser bundle's directory, served at `/` beside the API when given ([webBundle]: its
 * precompressed files, its cache headers). Every API route is more specific than the static one, so a
 * screen route always wins. The page answers the storefront's own addresses ([StorefrontPage], B-36),
 * so a reloaded or shared `/p/...` opens; there is deliberately no catch-all fallback to `index.html` —
 * it would answer an unknown `/ui/...` with a page and a 200 instead of the 404 the client draws.
 *
 * [photoStore] is the object storage product photos are kept in (B-30); `null` — no storage
 * configured — serves no photos, and every tile is the placeholder.
 *
 * [sagaClock] is the order saga's clock (B-16): a wall clock, apart from [clock], because what it
 * stamps is compared across processes — the sweeper takes a saga untouched for a minute as abandoned
 * by a process that died — while [clock] is the store's «now», which the tests hold at the canvas's.
 *
 * [fulfilment] is the simulated world's pace and whether it runs here (B-17): `main` runs it at
 * `HAUL_FULFILMENT_SPEED`; a test gets [FulfilmentSettings.MANUAL] and moves shipments by calling the
 * simulator itself. Its clock is [sagaClock], for the same reason: its stamps outlive the process.
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
) {
    val reportFailure = installObservability(observability)
    installSignIn(signIn)
    val database = Databases.connect(dataSource)
    install(Koin) {
        modules(
            module {
                single { database }
                single { dataSource }
                single { clock }
                single { sagaClock }
                single { fulfilment.pace }
                single { DeliveryCalendar(clock::now) }
                single { ProductPhotos(photoStore) }
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
            accountModule,
            returnsModule,
        )
    }
    // Carries on what a process that died left mid-saga, from the first moment this one serves; it
    // stops with the application, whose scope it runs in.
    get<SuspendedPetichSweeper>().start(this)
    // The simulated world after placement, in the same scope: shipments move and are charged as they ship.
    fulfilment.interval?.let { FulfilmentRunner(get(), get(), it).start(this) }
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
        exception<IdentityError> { call, error -> call.respondError(error.code, error.message, error.field) }
        exception<CartError> { call, error -> call.respondError(error.code, error.message, error.field) }
        exception<OrderError> { call, error -> call.respondError(error.code, error.message, error.field) }
        exception<ReturnError> { call, error ->
            call.respondError(error.code, error.message, error.field, error.fields)
        }
        exception<ReviewError> { call, error ->
            call.respondError(error.code, error.message, error.field, error.fields)
        }
        exception<CheckoutError> {
            call,
            error,
            ->
            call.respondError(error.code, error.message, error.field, error.fields)
        }
        unexpectedFailures(report = reportFailure)
    }
    routing {
        probes(commit = commit, ready = { databaseAnswers(dataSource) })
        // Public: a guest, a customer or nobody. A token is optional, and one that does not verify is
        // refused rather than ignored — a shopper whose sign-in lapsed is told, not shown as a guest.
        authenticate(JWT_AUTH_OIDC, optional = true) {
            catalogRouting()
            searchRouting()
            identityRouting(signIn)
            cartRouting()
        }
        // Customer: a verified shildik token, or `401 unauthenticated`.
        authenticate(JWT_AUTH_OIDC) {
            customerIdentityRouting()
            customerSearchRouting()
            accountRouting()
            checkoutRouting()
            reviewsRouting()
            orderRouting()
            returnsRouting()
        }
        web?.let { webBundle(it) }
    }
}

/** The one shape of every refusal: [ErrorBody] with the status its [code] maps to. */
internal suspend fun ApplicationCall.respondError(
    code: ErrorCode,
    message: String,
    field: String? = null,
    fields: List<FieldError> = emptyList(),
) = respondText(
    haulWireJson.encodeToString(ErrorBody.serializer(), ErrorBody(code, message, field, fields)),
    ContentType.Application.Json,
    status(code),
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
