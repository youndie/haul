package io.github.youndie.haul.testing

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * Every line logged to [loggers] while [block] runs, as logback received it — the level, the message and whether a
 * stack came with it, which is what a reader of the pod's log and tracy (from `warn`) sees.
 */
internal fun <T> capturingLog(
    vararg loggers: String,
    block: (lines: List<ILoggingEvent>) -> T,
): T {
    val appender = ListAppender<ILoggingEvent>().apply { start() }
    val attached = loggers.map { LoggerFactory.getLogger(it) as Logger }
    attached.forEach { it.addAppender(appender) }
    try {
        return block(appender.list)
    } finally {
        attached.forEach { it.detachAppender(appender) }
        appender.stop()
    }
}

/** The logger the catch-all writes through (`ErrorAnswers.kt`). */
internal const val ERROR_ANSWERS_LOG = "io.github.youndie.haul.ErrorAnswers"
