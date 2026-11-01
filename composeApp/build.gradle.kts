import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The storefront: renders the trees the server sends, owns navigation, Loading and Error, the
// browser sign-in and the guest id (haul-web in the documentation).
//
// wasmJs ships in the browser. `jvm("desktop")` photographs the same renderers for the goldens —
// viddik takes screenshots of JVM targets — and is the second client (B-176): a desktop app that
// browses a server's trees in a window, packaged below, never published.
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
            // The seed's pictures are SVG (B-78), drawn by Skia's own SVG renderer through Coil.
            implementation(libs.coil.svg)
            // The screens' trees and the suggest panel come over the same client (B-35).
            implementation(libs.ktor.client.core)
        }
        wasmJsMain.dependencies {
            // The browser's sign-in: the code flow with PKCE in a popup, against shildik (research
            // risk 2, decided in B-12). Only the shipped target needs it; the desktop one draws.
            implementation(libs.oidc.appsupport)
            // The browser's fetch, for the photos' and the storefront's Ktor client.
            implementation(libs.ktor.client.js)
        }
        getByName("desktopMain").dependencies {
            // The window, on the operating system the build runs on: jpackage cannot cross-build, so each
            // package is made on its own system (B-176).
            implementation(compose.desktop.currentOs)
            // The desktop app's requests — the trees, the commands, the photos, the stream — over Ktor's own engine.
            implementation(libs.ktor.client.cio)
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

// The desktop app (B-176): `run` opens it against the stand, `-Pserver=<origin>` (or `HAUL_SERVER`) against another
// server; `packageDistributionForCurrentOS` builds the installer of the system it runs on. Nothing is published.
compose.desktop {
    application {
        mainClass = "io.github.youndie.haul.DesktopMainKt"
        providers.gradleProperty("server").orNull?.let { args("--server=$it") }
        nativeDistributions {
            // One format per system: jpackage builds only its host's, and two installers for one system is a
            // choice nobody asked for.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Haul"
            description = "The Haul storefront, drawn from the server's trees"
            vendor = "youndie"
            // Fixed, and at least 1: macOS refuses an app version whose first number is 0, and nothing here is
            // released, so no installed copy has to be upgraded from it.
            packageVersion = "1.0.0"
            // jlink keeps only what is named: these four are what `suggestRuntimeModules` (jdeps over the app's
            // jars) names beside Compose's own — `java.prefs` is where the guest is kept. A module missing here fails
            // only in the installed app, never in `run`, which runs on the whole JDK.
            modules("java.instrument", "java.management", "java.prefs", "jdk.unsupported")
            linux {
                // `.deb` refuses to build without one.
                debMaintainer = "youndie@users.noreply.github.com"
                menuGroup = "Office"
                appCategory = "Office"
            }
            macOS {
                bundleID = "io.github.youndie.haul"
            }
            windows {
                // The identity an `.msi` upgrades along; never changed once set.
                upgradeUuid = "6b0f3c55-3a39-4d0e-9a51-2f6f1c1e7b26"
                menuGroup = "Haul"
                perUserInstall = true
            }
        }
    }
}

// `check` would compile only the desktop half; the bundle is what ships, so it is compiled too.
tasks.named("check") {
    dependsOn(tasks.named("compileKotlinWasmJs"))
}

// StaticFrameTest reads the shipped page, which no desktop source set carries (B-80): without it as an input,
// a changed page would leave the test up to date. WiredArmTest likewise reads the entry point and the script
// that patches it (B-90).
tasks.named<Test>("desktopTest") {
    inputs
        .file("src/wasmJsMain/resources/index.html")
        .withPropertyName("shippedPage")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .files("src/wasmJsMain/kotlin/io/github/youndie/haul/Main.kt", "../scripts/measure-first-load.sh")
        .withPropertyName("wiredArm")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // BundledGlyphsTest reads the server's strings, which reach the page in its trees but are no part of this
    // module's classpath (B-128).
    inputs
        .dir("../server/src/main/kotlin")
        .withPropertyName("serverStrings")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
