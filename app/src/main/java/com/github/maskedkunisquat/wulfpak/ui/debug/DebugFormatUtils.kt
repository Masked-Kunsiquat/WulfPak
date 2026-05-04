package com.github.maskedkunisquat.wulfpak.ui.debug

internal fun fmtMs(ms: Long): String = when {
    ms < 1_000  -> "${ms}ms"
    ms < 60_000 -> "${"%.1f".format(ms / 1000.0)}s"
    else        -> "${"%.1f".format(ms / 60_000.0)}m"
}
