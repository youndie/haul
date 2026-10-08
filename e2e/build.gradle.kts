// The whole path over HTTP — browse, cart, sign-in, checkout, delivered, return — against a composed
// stack: the server's image, PostgreSQL and shildik (B-26). It speaks the contract, so it compiles
// against `shared`; it never reaches into the server's code.
plugins {
    alias(wip.plugins.kotlinJvm)
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
}

dependencies {
    testImplementation(projects.shared)
    testImplementation(kotlin("test"))
}
