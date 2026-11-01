package io.github.youndie.haul.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import io.github.youndie.haul.ui.DemoNote
import io.github.youndie.haul.ui.DemoNoteView

// The demo strip (B-160): one quiet line above every page of the storefront saying the stand is a demo built with
// Kotlin, with «About this demo» and «Source on GitHub», until the visitor closes it. The copy is the server's, a tree
// of its own (`GET /ui/demo`), asked for once the first page has drawn, so the first load waits for nothing more; it
// is drawn above whatever page is shown, the shell's own pages included. Closed, it stays closed for the session.
// Only the storefront's root draws it ([DemoStripped], `App`): a view drawn on its own — a golden, a fixture, the
// shell's tests of `Storefront` — has none.

/** Where the demo strip's copy is: a tree of its own, apart from any page. */
public const val DEMO_NOTE_TREE: String = "/ui/demo"

/**
 * Whether the visitor closed the demo strip, for as long as the session lasts: the browser's `sessionStorage`
 * (`BrowserDemoNoteMemory`), so a reload or another page of the tab keeps it closed and a new tab shows it again;
 * memory elsewhere.
 */
public interface DemoNoteMemory {
    public val closed: Boolean

    public fun close()
}

/** A [DemoNoteMemory] that lasts as long as the object: the desktop's and a test's. */
public class MemoryDemoNoteMemory(
    closed: Boolean = false,
) : DemoNoteMemory {
    override var closed: Boolean = closed
        private set

    override fun close() {
        closed = true
    }
}

/**
 * The demo strip as the storefront holds it: the [note] the server sent, once asked for, and whether the visitor has
 * [closed] it, as [memory] remembers. A closed strip asks for nothing; one whose answer did not arrive is asked for
 * again after the next page draws.
 */
@Stable
public class DemoStrip(
    private val transport: HaulTransport,
    private val memory: DemoNoteMemory,
) {
    internal var note: DemoNote? by mutableStateOf(null)
        private set

    internal var closed: Boolean by mutableStateOf(memory.closed)
        private set

    /** How tall the strip is drawn, in pixels; 0 while it is not. The line of a load on its way sits under the header. */
    internal var height: Int by mutableIntStateOf(0)

    private var asking = false

    /** A page has drawn: the strip is asked for, unless it is here, on its way or closed. */
    internal suspend fun pageDrawn() {
        if (closed || note != null || asking) return
        asking = true
        try {
            note = transport.demoNote()
        } finally {
            asking = false
        }
    }

    /** The close control: hidden at once, and for the rest of the session. */
    internal fun close() {
        memory.close()
        closed = true
    }
}

/** The demo strip of the storefront being drawn; `null` — a view drawn on its own, a golden — draws none. */
public val LocalDemoStrip: ProvidableCompositionLocal<DemoStrip?> = staticCompositionLocalOf { null }

/** [content] — the storefront — with the demo strip above its pages, its copy from [transport], its closing in [memory]. */
@Composable
public fun DemoStripped(
    transport: HaulTransport,
    memory: DemoNoteMemory,
    content: @Composable () -> Unit,
) {
    val strip = remember(transport, memory) { DemoStrip(transport, memory) }
    CompositionLocalProvider(LocalDemoStrip provides strip, content = content)
}

/**
 * The strip above a page, when there is one to draw. [drawn] is whether the page under it is one, not its
 * placeholder: the strip is asked for once such a page has drawn — a frame after it — so the first page's tree goes
 * first and alone.
 */
@Composable
internal fun DemoStripAbove(drawn: Boolean) {
    val strip = LocalDemoStrip.current ?: return
    if (drawn) {
        LaunchedEffect(strip) {
            withFrameNanos { }
            strip.pageDrawn()
        }
    }
    val note = strip.note
    if (note == null || strip.closed) return
    DisposableEffect(strip) { onDispose { strip.height = 0 } }
    DemoNoteView(note, onClose = strip::close, Modifier.onSizeChanged { strip.height = it.height })
}

/** The demo strip at [DEMO_NOTE_TREE], or `null` when it did not arrive or is not one: the page goes on without it. */
private suspend fun HaulTransport.demoNote(): DemoNote? =
    try {
        tree(DEMO_NOTE_TREE) as? DemoNote
    } catch (failed: ScreenFailed) {
        null
    }
