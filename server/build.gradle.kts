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
    implementation(wip.kotlinx.serialization.json)
    runtimeOnly(libs.logback.classic)

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
}

// **The cache is trained in the image, not here.** The JVM accepts a cache only from the build that
// wrote it, down to the size of `lib/modules`, so a cache trained by this machine's JDK is refused by
// the image's JRE. And training starts the server, which will not start without PostgreSQL. So the
// build packs no cache (`onAssemble = false`), and `docker/Dockerfile` trains one with the runner the
// distribution carries, against a throw-away PostgreSQL inside the build, on the JRE that runs it
// (research risk 4). The block below is what that runner reads.
zavarnik {
    training {
        onAssemble = false
        readyWhen.url("http://127.0.0.1:8080/readyz")
        // The hot path, such as it is before the first feature: the probes and the seed the
        // training run performs on start. Each feature that adds a screen adds its route here.
        workload {
            get("http://127.0.0.1:8080/readyz")
            get("http://127.0.0.1:8080/version")
        }
    }
}
