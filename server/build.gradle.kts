// The server: owns all data, builds every screen as a kompot tree, takes every command, runs the
// order saga and simulates the world outside it (research D1–D4).
//
// On the JVM only — kompot's server module publishes no native target (research §1.2, D1). An
// `application`, because zavarnik trains the AOT cache through its start script (research §1.5).
plugins {
    alias(wip.plugins.kotlinJvm)
    alias(wip.plugins.kotlinSerialization)
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
    application
    alias(libs.plugins.zavarnik)
}

application {
    mainClass.set("io.github.youndie.haul.ApplicationKt")
}

dependencies {
    implementation(projects.shared)
    implementation(platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
    implementation(libs.ktor.server.core)
    // CIO, decided rather than defaulted: the handlers block on JDBC (research D3), and CIO's dispatch
    // through `Dispatchers.IO` — 64 workers — is the slack a blocking handler needs; Netty's call
    // group defaults to the processor count, which is the worst choice for that shape.
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.kompot.ktor)
    // The order page's live updates (B-29): kompot's broadcaster hands each move's frame to the pages
    // watching that order, over Ktor's server-sent events.
    implementation(libs.kompot.realtimeServer)
    implementation(libs.ktor.server.sse)
    implementation(wip.kotlinx.serialization.json)
    implementation(platform(wip.koin.bom))
    implementation(wip.koin.core)
    implementation(libs.koin.ktor)
    runtimeOnly(libs.logback.classic)

    // Who watches it (B-27). Agents, not servers: where the data lands is a deployment, and each is
    // off until the environment names an endpoint and a key (`ServerConfig.observability`).
    //
    // metrik and tracy both publish a multiplatform `agent` whose JVM jar is `agent-jvm-<version>.jar`.
    // Two equal versions would be two files with one name in `lib/`, and `installDist` refuses that
    // rather than dropping one — keep it refusing; a `duplicatesStrategy` here would ship one agent
    // and silently lose the other.
    implementation(libs.metrik.agent)
    implementation(libs.tracy.agent)
    implementation(libs.katcher.client)

    // The customer tier (B-12): shildik's own token validator — signature against the realm's JWKS,
    // lifetime, issuer (research §1.4, consequence 5).
    implementation(libs.shildik.oidcAuthServer)

    // The order saga (B-16): the engine, its tables on the same Exposed database as everything else, and
    // petich's guard for placement's `Idempotency-Key` (research D3, D4).
    implementation(libs.petich.core)
    implementation(libs.petich.postgres)
    implementation(libs.petich.idempotency)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.javaTime)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.exposed.migrationJdbc)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(wip.koin.test)
}

// **The browser bundle ships inside the server's distribution**, under `web/`, and the image points
// `HAUL_WEB_DIR` at it (haul-web: «served by haul-server»). One image, one origin: the page and the API
// it calls cannot be deployed at two different versions, and the client needs no base URL.
//
// Without `composeApp.js.map` (1.45 MB, B-34): the page never asks for it, and a server that publishes
// the directory as it is would publish the source map with it. The `.br`/`.gz` beside each file are
// written by the image (`docker/Dockerfile`), where the brotli encoder is.
val webBundle = project(":composeApp").tasks.named("wasmJsBrowserDistribution")
distributions {
    main {
        contents {
            from(webBundle) {
                into("web")
                exclude("**/*.map")
            }
        }
    }
}

// The client's wire bodies (`composeApp/src/desktopTest/resources/bodies`), which carry the canvas's
// copy: a route test compares what the server sends against them (B-33), so they are the test's input.
tasks.test {
    val bodies = layout.projectDirectory.dir("../composeApp/src/desktopTest/resources/bodies")
    inputs.dir(bodies).withPropertyName("clientBodies").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("haul.clientBodies", bodies.asFile.absolutePath)
}

// **The cache is trained in the image, not here.** The JVM accepts a cache only from the build that
// wrote it, down to the size of `lib/modules`, so a cache trained by this machine's JDK is refused by
// the image's JRE. And training starts the server, which will not start without PostgreSQL. So the
// build packs no cache (`onAssemble = false`), and `docker/Dockerfile` trains one with the runner the
// distribution carries, against a throw-away PostgreSQL inside the build, on the JRE that runs it
// (research risk 4). The block below is what that runner reads.
zavarnik {
    // `check` would otherwise train through `aotVerify`, on a runner with no database; the image's own
    // verification (`scripts/image-check.sh`, the `image` job) is the one that counts.
    verify {
        onCheck = false
    }
    training {
        onAssemble = false
        readyWhen.url("http://127.0.0.1:8080/readyz")
        // The hot path, such as it is before the first feature: the probes and the seed the
        // training run performs on start. Each feature that adds a screen adds its route here.
        workload {
            get("http://127.0.0.1:8080/readyz")
            get("http://127.0.0.1:8080/version")
            get("http://127.0.0.1:8080/")
            // The page at a storefront address (B-36), as a reloaded product link asks for it.
            get("http://127.0.0.1:8080/p/p-sony-wh-1000xm6")
            get("http://127.0.0.1:8080/ui/home")
            get("http://127.0.0.1:8080/ui/c/electronics/audio/headphones?brand=Sony&feature=Noise%20cancelling")
            get("http://127.0.0.1:8080/ui/p/p-sony-wh-1000xm6")
            get("http://127.0.0.1:8080/ui/search/suggest?q=running%20sh")
            get("http://127.0.0.1:8080/ui/search?q=running%20shoes")
            get("http://127.0.0.1:8080/ui/search?q=xqzt")
            // The cart needs a guest, and the workload has no PUT to fill one: an empty cart is what
            // trains here, with the guest's creation in front of it.
            post("http://127.0.0.1:8080/api/v1/guests", "application/json", "") { capture("guest", "id") }
            get("http://127.0.0.1:8080/ui/cart") { header("X-Haul-Guest", "{{guest}}") }
        }
    }
}
