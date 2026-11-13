package com.gammagamma.lax

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gammagamma.lax.app.NetCommand
import com.gammagamma.lax.app.NetSession
import com.gammagamma.lax.app.SettingsStore
import com.gammagamma.lax.app.coerceSidebar
import com.gammagamma.lax.app.createLaxPaths
import com.gammagamma.lax.model.Connection
import com.gammagamma.lax.slack.createCredentialSource
import com.gammagamma.lax.store.Store
import com.gammagamma.lax.ui.components.ChatPane
import com.gammagamma.lax.ui.components.ManualCredentialsDialog
import com.gammagamma.lax.ui.components.Sidebar
import com.gammagamma.lax.ui.components.WorkspaceRail
import com.gammagamma.lax.ui.theme.LaxTheme
import com.gammagamma.lax.ui.theme.LocalLaxColors

@Composable
fun App(
    teamHint: String? = null,
    channelHint: String? = null,
    debug: Boolean = false,
) {
    val paths = remember { createLaxPaths().also { it.ensureConfigDir(); it.ensureCacheDir() } }
    val settingsStore = remember { SettingsStore(paths) }
    val settings = remember { settingsStore.load() }
    val layout = remember { settingsStore.loadLayout() }
    var sidebarWidth by remember { mutableFloatStateOf(layout.sidebarWidth.coerceSidebar()) }

    val store = remember { Store() }
    val session = remember {
        NetSession(
            store = store,
            credentials = createCredentialSource(),
            teamHint = teamHint,
            debug = debug,
        )
    }

    DisposableEffect(Unit) {
        session.start()
        onDispose { session.stop() }
    }

    val ui by store.state.collectAsState()
    var draft by remember(ui.selectedChannelId) { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(channelHint, ui.space.channels) {
        if (channelHint != null && ui.space.channels.any { it.id == channelHint }) {
            session.send(NetCommand.Open(channelHint))
        }
    }

    LaunchedEffect(ui.toast) {
        ui.toast?.let {
            snackbar.showSnackbar(it)
            store.clearToast()
        }
    }

    LaxTheme(settings) {
        val colors = LocalLaxColors.current
        Box(Modifier.fillMaxSize().background(colors.background)) {
            Row(Modifier.fillMaxSize()) {
                WorkspaceRail(
                    teams = ui.teams,
                    activeTeamId = ui.activeTeamId,
                    onSelect = { session.send(NetCommand.SwitchTeam(it)) },
                )
                Sidebar(
                    teamName = ui.space.teamName.ifEmpty {
                        ui.teams.find { it.id == ui.activeTeamId }?.name.orEmpty()
                    },
                    channels = ui.space.channels.map { ch ->
                        if (ch.kind == com.gammagamma.lax.model.ChannelKind.Im) {
                            val name = ui.space.users[ch.userId]?.name ?: ch.name
                            ch.copy(name = name)
                        } else ch
                    },
                    users = ui.space.users,
                    selectedId = ui.selectedChannelId,
                    sidebarMode = ui.space.sidebarMode,
                    rowHeight = settings.sidebarRow.dp,
                    onSelect = { session.send(NetCommand.Open(it)) },
                    modifier = Modifier.width(sidebarWidth.dp),
                )
                Box(
                    Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(colors.border)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                sidebarWidth = (sidebarWidth + drag.x.toDp().value).coerceSidebar()
                                settingsStore.saveLayout(layout.copy(sidebarWidth = sidebarWidth))
                            }
                        },
                )
                ChatPane(
                    channel = ui.selectedChannel,
                    users = ui.space.users,
                    teamName = ui.space.teamName,
                    connectionLabel = when (ui.space.connection) {
                        Connection.Connecting -> "Connecting…"
                        Connection.Online -> ui.space.status.ifEmpty { "Online" }
                        Connection.Offline -> ui.space.status.ifEmpty { "Offline" }
                    },
                    chatSize = settings.chatSize,
                    draft = draft,
                    onDraftChange = {
                        draft = it
                        ui.selectedChannelId?.let { id -> session.send(NetCommand.Typing(id)) }
                    },
                    onSend = {
                        val id = ui.selectedChannelId ?: return@ChatPane
                        if (draft.isBlank()) return@ChatPane
                        session.send(NetCommand.Send(id, draft.trim()))
                        draft = ""
                    },
                    onLoadOlder = {
                        val ch = ui.selectedChannel ?: return@ChatPane
                        val before = ch.messages.firstOrNull()?.ts ?: return@ChatPane
                        session.send(NetCommand.Older(ch.id, before))
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))

            ui.needManualReason?.let { reason ->
                ManualCredentialsDialog(
                    reason = reason,
                    onSubmit = { url, token, cookie ->
                        session.send(NetCommand.Manual(url, token, cookie))
                    },
                    onDismiss = { /* keep prompting until connected */ },
                )
            }
        }
    }
}
