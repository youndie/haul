import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.time.Duration

// The whole path over HTTP — browse, cart, sign-in, checkout, delivered, return — against a composed
// stack: the server's image, PostgreSQL and shildik (B-26). It speaks the contract, so it compiles
// against `shared`; it never reaches into the server's code.
//
// **The image under test is named, never built here**: `-Phaul.e2e.image=<tag>`, which
// `scripts/e2e.sh` builds with `scripts/image-build.sh` — the recipe the image job checks — and passes
// on. Without it `test` is skipped, so `check` on a machine with no image stays what it was; the `e2e`
// job of `check.yaml` always names one.
plugins {
    alias(wip.plugins.kotlinJvm)
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
}

dependencies {
    testImplementation(projects.shared)
    testImplementation(wip.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    // The stack is started from the test itself, as the server's own suite starts PostgreSQL and
    // shildik: no compose file to keep beside the code that depends on it.
    testImplementation(libs.testcontainers.postgresql)
}

tasks.test {
    // A local, not a script property: the configuration cache cannot keep a lambda that holds the script.
    val image = providers.gradleProperty("haul.e2e.image")
    onlyIf("the image under test is named by -Phaul.e2e.image") { image.isPresent }
    image.orNull?.let { systemProperty("haul.e2e.image", it) }
    // The image can change under the same tag, and the stack is the input that matters: never up to date.
    outputs.upToDateWhen { false }
    // The hard ceiling over the whole run — three containers started, the path walked, the fast clock
    // waited out. The scenario's own waits are far shorter; this is for a run that hangs.
    timeout.set(Duration.ofMinutes(10))
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}
