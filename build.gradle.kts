import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
import org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinToolingSetupTask

// **No build logic here**, save the one rule below that can only live on the root project. A plugin a
// module names without a version has to already be on the build's plugin classpath, and this block is
// what puts it there; the Kotlin plugins are declared once for the same reason — a module asking for a
// versioned one would fail with "already on the classpath with an unknown version".
//
// Every module states its own targets: a target inherited from here is a target nobody argued for.
plugins {
    alias(wip.plugins.kotlinJvm) apply false
    alias(wip.plugins.kotlinMultiplatform) apply false
    alias(wip.plugins.kotlinSerialization) apply false
    alias(wip.plugins.composeMultiplatform) apply false
    alias(wip.plugins.composeCompiler) apply false
    alias(wip.plugins.ksp) apply false
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaKmp) apply false
    alias(libs.plugins.sborkaLint) apply false
    alias(libs.plugins.zavarnik) apply false
    alias(libs.plugins.viddik) apply false
}

// **One yarn at a time (B-141).** The wasm target runs yarn twice, from two root-project tasks the Kotlin
// plugin registers here: `kotlinWasmNpmInstall` (the bundle's packages, into build/wasm) and
// `kotlinWasmToolingSetup` (webpack and friends, into ~/.kotlin). Both pass yarn `--mutex network`, so
// when they overlap the second waits on a socket to the first — and Node's HTTP server drops a
// connection that sends no request after its headers timeout (60–90 s on the Node 26 the plugin
// installs). The waiter then exits 0 having installed nothing: no node_modules, no yarn.lock, and
// `kotlinWasmStoreYarnLock` fails with «build/wasm/yarn.lock doesn't exist». A hosted runner, with no
// ~/.kotlin and an empty yarn cache, takes longer than that to install the tooling. Holding the mutex in
// Gradle instead means yarn's is never contended within a build.
abstract class YarnLane : BuildService<BuildServiceParameters.None>

val yarnLane = gradle.sharedServices.registerIfAbsent("yarnLane", YarnLane::class) { maxParallelUsages.set(1) }
tasks.withType<KotlinNpmInstallTask>().configureEach { usesService(yarnLane) }
tasks.withType<KotlinToolingSetupTask>().configureEach { usesService(yarnLane) }
