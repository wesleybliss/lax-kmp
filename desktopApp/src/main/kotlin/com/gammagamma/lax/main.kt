package com.gammagamma.lax

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gammagamma.lax.app.SettingsStore
import com.gammagamma.lax.app.createLaxPaths

fun main(args: Array<String>) {
    val teamHint = argValue(args, "--team")
    val channelHint = argValue(args, "--channel")
    val debug = System.getenv("LAX_DEBUG") == "1" || args.contains("--debug")

    application {
        val paths = remember { createLaxPaths() }
        val layout = remember { SettingsStore(paths).loadLayout() }
        var windowSize by remember {
            mutableStateOf(DpSize(layout.width.dp, layout.height.dp))
        }
        val state = rememberWindowState(size = windowSize)

        Window(
            onCloseRequest = {
                SettingsStore(paths).saveLayout(
                    layout.copy(
                        width = state.size.width.value.toInt().coerceAtLeast(800),
                        height = state.size.height.value.toInt().coerceAtLeast(600),
                    ),
                )
                exitApplication()
            },
            title = "Lax",
            state = state,
        ) {
            App(
                teamHint = teamHint,
                channelHint = channelHint,
                debug = debug,
            )
        }
    }
}

private fun argValue(args: Array<String>, name: String): String? {
    val i = args.indexOf(name)
    return if (i >= 0 && i + 1 < args.size) args[i + 1] else null
}
