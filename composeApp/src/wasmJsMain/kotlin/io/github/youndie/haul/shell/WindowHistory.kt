@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.youndie.haul.shell

/**
 * The page's own history: `pushState` out, `popstate` in. The listener hears the
 * address the browser arrived at — back and forward both — not an instruction to pop.
 */
internal object WindowHistory : BrowserHistory {
    private val listeners = mutableListOf<(String) -> Unit>()
    private var listening = false

    override val location: String get() = currentLocation()

    override fun push(location: String) = pushLocation(location)

    override fun listen(listener: (location: String) -> Unit): () -> Unit {
        if (!listening) {
            listening = true
            onPopState { location -> listeners.toList().forEach { it(location.toString()) } }
        }
        listeners += listener
        return { listeners -= listener }
    }
}

@JsFun("() => window.location.pathname + window.location.search")
private external fun currentLocation(): String

@JsFun("(location) => window.history.pushState(null, '', location)")
private external fun pushLocation(location: String)

@JsFun(
    "(listener) => window.addEventListener('popstate', () => listener(window.location.pathname + window.location.search))",
)
private external fun onPopState(listener: (JsString) -> Unit)
