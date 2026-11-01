package io.github.youndie.haul.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/**
 * The desktop app's history (B-176): a window has no address bar and no back button of its own, so the entries are
 * kept here, as a browser keeps a tab's. [push], [replace] and [back] are what the shell asks of a browser's history;
 * [forward] and [back] are the window's own Go menu, and each tells the shell where it arrived, as `popstate` does.
 */
internal class BackStackHistory(
    start: String = "/",
) : BrowserHistory {
    private val entries = mutableStateListOf(start)
    private var index by mutableIntStateOf(0)
    private val listeners = mutableListOf<(String) -> Unit>()

    override val location: String get() = entries[index]

    /** Whether [back] has an entry to go to: the Go menu's «Back» is enabled by it. */
    val canGoBack: Boolean get() = index > 0

    /** Whether [forward] has an entry to go to. */
    val canGoForward: Boolean get() = index < entries.lastIndex

    override fun push(location: String) {
        while (entries.lastIndex > index) entries.removeAt(entries.lastIndex)
        entries += location
        index = entries.lastIndex
    }

    override fun replace(location: String) {
        entries[index] = location
    }

    override fun back() {
        if (canGoBack) arrive(index - 1)
    }

    fun forward() {
        if (canGoForward) arrive(index + 1)
    }

    override fun listen(listener: (location: String) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private fun arrive(at: Int) {
        index = at
        listeners.toList().forEach { it(location) }
    }
}
