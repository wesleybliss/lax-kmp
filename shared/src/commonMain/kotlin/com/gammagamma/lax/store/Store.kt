package com.gammagamma.lax.store

import com.gammagamma.lax.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkspaceState(
    val selfId: String = "",
    val teamName: String = "",
    val users: Map<String, User> = emptyMap(),
    val channels: List<Channel> = emptyList(),
    val thread: Thread? = null,
    val connection: Connection = Connection.Connecting,
    val status: String = "",
    val lastSelected: String = "",
    val sidebarMode: SidebarMode = SidebarMode.Active,
    val notify: NotifyPrefs = NotifyPrefs(),
    val feeds: Map<FeedKind, List<FeedEntry>> = emptyMap(),
)

data class UiState(
    val teams: List<Team> = emptyList(),
    val activeTeamId: String = "",
    val spaces: Map<String, WorkspaceState> = emptyMap(),
    val selectedChannelId: String? = null,
    val needManualReason: String? = null,
    val toast: String? = null,
) {
    val space: WorkspaceState
        get() = spaces[activeTeamId] ?: WorkspaceState()

    val selectedChannel: Channel?
        get() = selectedChannelId?.let { id -> space.channels.find { it.id == id } }
}

class Store {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun selectChannel(id: String?) {
        _state.update { ui ->
            val team = ui.activeTeamId
            val spaces = if (team.isNotEmpty() && id != null) {
                ui.spaces + (team to ui.space.copy(lastSelected = id))
            } else {
                ui.spaces
            }
            ui.copy(selectedChannelId = id, spaces = spaces)
        }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    fun apply(item: StoreItem) {
        _state.update { ui -> applyItem(ui, item) }
    }

    private fun applyItem(ui: UiState, item: StoreItem): UiState {
        val teamHint = item.teamId.ifEmpty { ui.activeTeamId }
        return when (val u = item.update) {
            is Update.Teams -> ui.copy(
                teams = u.list,
                activeTeamId = u.active,
                selectedChannelId = ui.spaces[u.active]?.lastSelected?.ifEmpty { null }
                    ?: ui.selectedChannelId,
            )
            is Update.NeedManual -> ui.copy(needManualReason = u.reason)
            Update.ManualDone -> ui.copy(needManualReason = null)
            is Update.Toast -> ui.copy(toast = u.text)
            else -> {
                val team = teamHint.ifEmpty { return ui }
                val space = ui.spaces[team] ?: WorkspaceState()
                val next = applyToSpace(space, u, ui.selfFallback())
                val spaces = ui.spaces + (team to next)
                var selected = ui.selectedChannelId
                if (selected == null && next.channels.isNotEmpty()) {
                    selected = next.lastSelected.ifEmpty {
                        next.channels.sortedBy { it.name.lowercase() }.first().id
                    }
                }
                ui.copy(spaces = spaces, selectedChannelId = selected, activeTeamId = ui.activeTeamId.ifEmpty { team })
            }
        }
    }

    private fun UiState.selfFallback(): String = spaces[activeTeamId]?.selfId.orEmpty()

    private fun applyToSpace(space: WorkspaceState, u: Update, selfId: String): WorkspaceState = when (u) {
        is Update.Self -> space.copy(selfId = u.userId, teamName = u.teamName)
        is Update.Channels -> {
            val existing = space.channels.associateBy { it.id }
            val merged = u.channels.map { incoming ->
                val old = existing[incoming.id]
                if (old == null) incoming else incoming.copy(
                    messages = old.messages,
                    historyLoaded = old.historyLoaded,
                    hasOlder = old.hasOlder,
                    typing = old.typing,
                    unread = old.unread || incoming.unread,
                    mentions = maxOf(old.mentions, incoming.mentions),
                )
            }
            space.copy(channels = merged)
        }
        is Update.Users -> space.copy(
            users = space.users + u.users.associateBy { it.id },
        )
        is Update.History -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else mergeHistory(ch, u)
            },
        )
        is Update.MessageUpdate -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else upsertMessage(ch, u.message)
            },
        )
        is Update.Edited -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else ch.copy(
                    messages = ch.messages.map { m ->
                        if (m.ts != u.ts) m else m.copy(
                            text = u.text,
                            files = u.files.ifEmpty { m.files },
                        )
                    },
                )
            },
        )
        is Update.Deleted -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else ch.copy(
                    messages = ch.messages.filterNot { it.ts == u.ts },
                )
            },
        )
        is Update.ReactionUpdate -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else ch.copy(
                    messages = ch.messages.map { m ->
                        if (m.ts != u.ts) m else applyReaction(m, u, selfId.ifEmpty { space.selfId })
                    },
                )
            },
        )
        is Update.TypingUpdate -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else ch.copy(
                    typing = (ch.typing.filterNot { it.user == u.user } + Typing(u.user, u.atMs))
                        .filter { it.atMs + TYPING_MS > u.atMs },
                )
            },
        )
        is Update.Presence -> space.copy(
            users = space.users.mapValues { (id, user) ->
                if (id in u.users) user.copy(online = u.active) else user
            },
        )
        is Update.Marked -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else ch.copy(
                    unread = (u.unread ?: 0) > 0,
                    mentions = u.mentions ?: ch.mentions,
                )
            },
        )
        is Update.Unreads -> {
            val map = u.items.associateBy { it.id }
            space.copy(
                channels = space.channels.map { ch ->
                    val info = map[ch.id] ?: return@map ch
                    ch.copy(
                        unread = info.hasUnreads,
                        mentions = info.mentions,
                        activity = info.latest.ifEmpty { ch.activity },
                        listed = true,
                    )
                },
            )
        }
        is Update.Left -> space.copy(channels = space.channels.filterNot { it.id == u.channel })
        is Update.SidebarModeUpdate -> space.copy(sidebarMode = u.mode)
        is Update.Notify -> space.copy(notify = u.prefs)
        is Update.FeedUpdate -> space.copy(feeds = space.feeds + (u.kind to u.entries))
        is Update.ThreadHistory -> space.copy(
            thread = Thread(u.channel, u.threadTs, u.messages, loaded = true),
        )
        is Update.ThreadMessage -> {
            val t = space.thread
            if (t == null || t.channel != u.channel || t.ts != u.threadTs) space
            else space.copy(thread = t.copy(messages = upsertInList(t.messages, u.message)))
        }
        is Update.ReplyCount -> space.copy(
            channels = space.channels.map { ch ->
                if (ch.id != u.channel) ch else ch.copy(
                    messages = ch.messages.map { m ->
                        if (m.ts != u.ts) m else m.copy(replyCount = u.count)
                    },
                )
            },
        )
        is Update.ConnectionUpdate -> space.copy(connection = u.connection)
        is Update.Status -> space.copy(status = u.text)
        else -> space
    }

    private fun mergeHistory(ch: Channel, u: Update.History): Channel {
        val merged = if (u.older) {
            (u.messages + ch.messages).distinctBy { it.ts }.sortedBy { it.ts }
        } else {
            u.messages.sortedBy { it.ts }
        }.takeLast(MAX_MESSAGES_PER_CHANNEL)
        return ch.copy(
            messages = merged,
            historyLoaded = true,
            hasOlder = u.hasMore || (u.older && ch.hasOlder),
            latest = merged.lastOrNull()?.ts ?: ch.latest,
        )
    }

    private fun upsertMessage(ch: Channel, message: Message): Channel {
        if (message.hidden || message.isThreadReply) {
            return if (message.isThreadReply) {
                ch.copy(
                    messages = ch.messages.map { m ->
                        if (m.ts == message.threadTs) m.copy(replyCount = m.replyCount + 1) else m
                    },
                )
            } else ch
        }
        return ch.copy(
            messages = upsertInList(ch.messages, message).takeLast(MAX_MESSAGES_PER_CHANNEL),
            latest = maxOfTs(ch.latest, message.ts),
            unread = true,
            listed = true,
        )
    }

    private fun upsertInList(list: List<Message>, message: Message): List<Message> {
        val idx = list.indexOfFirst { it.ts == message.ts }
        return if (idx < 0) (list + message).sortedBy { it.ts }
        else list.toMutableList().also { it[idx] = message }
    }

    private fun applyReaction(m: Message, u: Update.ReactionUpdate, selfId: String): Message {
        val mine = u.user == selfId
        val existing = m.reactions.toMutableList()
        val i = existing.indexOfFirst { it.name == u.name }
        if (u.added) {
            if (i < 0) existing += Reaction(u.name, 1, mine)
            else existing[i] = existing[i].copy(
                count = existing[i].count + 1,
                mine = existing[i].mine || mine,
            )
        } else if (i >= 0) {
            val next = existing[i].count - 1
            if (next <= 0) existing.removeAt(i)
            else existing[i] = existing[i].copy(
                count = next,
                mine = if (mine) false else existing[i].mine,
            )
        }
        return m.copy(reactions = existing)
    }

    private fun maxOfTs(a: String, b: String): String =
        if (a.isEmpty()) b else if (b.isEmpty()) a else if (a >= b) a else b
}
