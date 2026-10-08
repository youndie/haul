// The server: owns all data, builds every screen as a kompot tree, takes every command, runs the
// order saga and simulates the world outside it (research D1–D4).
//
// On the JVM only — kompot's server module publishes no native target (research §1.2, D1). An
// `application`, because zavarnik trains the AOT cache through its start script (research §1.5);
// the cache itself arrives with the image (B-03).
plugins {
    alias(wip.plugins.kotlinJvm)
    alias(wip.plugins.kotlinSerialization)
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
    application
}

application {
    mainClass.set("io.github.youndie.haul.ApplicationKt")
}

dependencies {
    implementation(projects.shared)
    implementation(platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
    implementation(libs.ktor.server.core)
    // CIO, decided rather than defaulted: the handlers will block on JDBC (research D3), and CIO's
    // dispatch through `Dispatchers.IO` — 64 workers — is the slack a blocking handler needs; Netty's
    // call group defaults to the processor count, which is the worst choice for that shape.
    implementation(libs.ktor.server.cio)
    runtimeOnly(libs.logback.classic)

    testImplementation(kotlin("test"))
}
