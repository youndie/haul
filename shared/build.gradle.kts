// The contract both halves read, and nothing else: the Haul components on the wire, the route
// classes, the request bodies of commands, the closed `ErrorCode` enum, money and time types.
//
// Not here: anything only one side reads — a default a screen depends on, the server's address, a
// business rule. jvm for the server and the desktop screenshots, wasmJs for the storefront.
plugins {
    alias(wip.plugins.kotlinMultiplatform)
    alias(wip.plugins.kotlinSerialization)
    alias(wip.plugins.ksp)
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
            implementation(libs.kompot.registryAnnotations)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// kompot's processor writes the polymorphic registration of every `@KompotComponentMarker` class
// once, over common metadata, so the server and the client decode the same set of types.
dependencies { add("kspCommonMainMetadata", libs.kompot.registryProcessor) }
ksp { arg("kompotModuleTag", "HaulContract") }
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
