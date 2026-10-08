// The contract both halves read, and nothing else: the Haul components on the wire, the route
// classes, the request bodies of commands, the closed `ErrorCode` enum, money and time types.
//
// Not here: anything only one side reads — a default a screen depends on, the server's address, a
// business rule. jvm for the server and the desktop screenshots, wasmJs for the storefront.
plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
}

kotlin {
    jvm()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(wip.kotlinx.serialization.json)
            // `api`: the components are kompot components, so their supertypes are in the contract's
            // public signatures and every consumer needs the same version.
            api(libs.kompot.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
