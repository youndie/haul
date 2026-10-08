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
    alias(wip.plugins.ksp)
    alias(libs.plugins.viddik)
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
            // The three fonts of the canvas ship as Compose resources (research §1.6, D7).
            implementation(
                "org.jetbrains.compose.components:components-resources:${wip.versions.composeMultiplatform.get()}",
            )
            implementation(wip.kotlinx.serialization.json)
            api(libs.kompot.client)
            implementation(libs.kompot.registryAnnotations)
            // Product photos (B-30): Coil draws them, over a Ktor client the entry point supplies; the
            // storefront's own requests — guests, the screens' trees, the cart merge (B-12) — go over the
            // same client. The Ktor the server runs, so the two halves move together.
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.coil.compose)
            implementation(libs.coil.networkKtor)
            implementation(libs.ktor.client.core)
        }
        wasmJsMain.dependencies {
            // The browser's sign-in: the code flow with PKCE in a popup, against shildik (research
            // risk 2, decided in B-12). Only the shipped target needs it; the desktop one draws.
            implementation(libs.oidc.appsupport)
            // The browser's fetch, for the photos' and the storefront's Ktor client.
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
            implementation(wip.compose.ui.test)
            implementation(project.dependencies.platform("io.ktor:ktor-bom:${wip.versions.ktor.get()}"))
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.resources {
    // Pinned, so a group set later does not rename the generated `Res` under every import.
    packageOfResClass = "io.github.youndie.haul.resources"
    publicResClass = false
}

// The renderers' registry, generated over common metadata from `@KompotComponentMarker`.
dependencies { add("kspCommonMainMetadata", libs.kompot.registryProcessor) }
ksp { arg("kompotModuleTag", "HaulApp") }
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"))
}
tasks
    .matching {
        it.name != "kspCommonMainKotlinMetadata" &&
            (
                it.name.startsWith(
                    "compile",
                ) || it.name.startsWith("ksp") || it.name.contains("ktlint", ignoreCase = true)
            )
    }.configureEach { dependsOn("kspCommonMainKotlinMetadata") }
ktlint {
    filter { exclude { it.file.path.contains("/generated/") } }
}

viddik {
    verifyOnCheck = true
}

// `check` would compile only the desktop half; the bundle is what ships, so it is compiled too.
tasks.named("check") {
    dependsOn(tasks.named("compileKotlinWasmJs"))
}
