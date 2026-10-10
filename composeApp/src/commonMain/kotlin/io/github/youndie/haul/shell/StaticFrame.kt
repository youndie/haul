package io.github.youndie.haul.shell

/**
 * The id of the static frame `index.html` draws while the bundle downloads and starts (B-80): the header's
 * shape and a progress line, in plain HTML and CSS. The entry point removes the element in the first frame
 * Compose draws; here, in common code, so the test that reads the page names the same id.
 */
public const val STATIC_FRAME_ID: String = "haul-frame"
