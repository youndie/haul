// The storefront: renders the trees the server sends, owns navigation, Loading and Error, the
// browser sign-in and the guest id (haul-web in the documentation).
//
// wasmJs ships; `jvm("desktop")` exists so the same renderers can be photographed against the
// canvas — viddik takes screenshots of JVM targets — and is never shipped.
plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.composeMultiplatform)
    alias(wip.plugins.composeCompiler)
    alias(wip.plugins.kotlinSerialization)
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(wip.compose.runtime)
            implementation(wip.compose.foundation)
            implementation(wip.compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// `check` would compile only the desktop half; the bundle is what ships, so it is compiled too.
tasks.named("check") {
    dependsOn(tasks.named("compileKotlinWasmJs"))
}
