rootProject.name = "haul"

// `projects.shared` instead of `project(":shared")`: a typo in the second is a runtime failure naming
// a path, in the first a compile error naming a symbol.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Written out by hand: `pluginManagement` is evaluated before any settings plugin is applied,
        // the conventions' own included, and they are published here. Filtered, because an
        // unfiltered repository is asked for every plugin, and the day its host is unreachable Gradle
        // disables it and fails plugins that live elsewhere.
        maven("https://reposilite.kotlin.website/snapshots") {
            name = "wip-snapshots"
            content { includeGroupByRegex("io\\.github\\.youndie.*") }
        }
    }
}

plugins {
    // Fetches the JDK 25 toolchain itself, so the build does not depend on one installed by hand.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Repositories with their content filters, the shared `wip` catalog (Kotlin, Compose
    // Multiplatform, Ktor, Koin), and the check that `.editorconfig` is the portfolio's.
    id("io.github.youndie.sborka.settings") version "0.5.0.113"
}

// The contract both halves read: the Haul components on the wire, routes, error codes.
include(":shared")
// The server: every screen as a kompot tree, every command, the order saga, the simulators.
include(":server")
// The storefront in the browser, plus a desktop target that draws the screenshots.
include(":composeApp")
// The whole path over HTTP, against a composed stack.
include(":e2e")
