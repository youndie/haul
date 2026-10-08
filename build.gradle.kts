// **No build logic here.** A plugin a module names without a version has to already be on the
// build's plugin classpath, and this block is what puts it there; the Kotlin plugins are declared
// once for the same reason — a module asking for a versioned one would fail with "already on the
// classpath with an unknown version".
//
// Every module states its own targets: a target inherited from here is a target nobody argued for.
plugins {
    alias(wip.plugins.kotlinJvm) apply false
    alias(wip.plugins.kotlinMultiplatform) apply false
    alias(wip.plugins.kotlinSerialization) apply false
    alias(wip.plugins.composeMultiplatform) apply false
    alias(wip.plugins.composeCompiler) apply false
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaKmp) apply false
    alias(libs.plugins.sborkaLint) apply false
    alias(libs.plugins.zavarnik) apply false
}
