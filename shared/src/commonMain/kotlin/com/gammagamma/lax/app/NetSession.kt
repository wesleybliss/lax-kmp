package com.gammagamma.lax.app

import com.gammagamma.lax.model.*
import com.gammagamma.lax.slack.CredentialResult
import com.gammagamma.lax.slack.CredentialSource
import com.gammagamma.lax.slack.Credentials
import com.gammagamma.lax.slack.Events
import com.gammagamma.lax.slack.RealtimeSocket
import com.gammagamma.lax.slack.SlackApi
import com.gammagamma.lax.slack.SlackApiException
import com.gammagamma.lax.slack.SlackEvent
import com.gammagamma.lax.slack.WorkspaceCred
import com.gammagamma.lax.slack.arr
import com.gammagamma.lax.slack.bool
import com.gammagamma.lax.slack.createHttpClient
import com.gammagamma.lax.slack.obj
import com.gammagamma.lax.slack.str
import com.gammagamma.lax.store.Store
import com.gammagamma.lax.store.StoreItem
import com.gammagamma.lax.store.UnreadInfo
import com.gammagamma.lax.store.Update
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel as CommandChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min

sealed interface NetCommand {
    data class Open(val channel: String) : NetCommand
    data class Send(val channel: String, val text: String, val threadTs: String? = null) : NetCommand
    data class Older(val channel: String, val before: String) : NetCommand
    data class OpenThread(val channel: String, val ts: String) : NetCommand
    data class React(val channel: String, val ts: String, val name: String, val add: Boolean) : NetCommand
    data class Mark(val channel: String, val ts: String) : NetCommand
    data class Typing(val channel: String) : NetCommand
    data class SwitchTeam(val teamId: String) : NetCommand
    data class Manual(val url: String, val token: String, val cookie: String) : NetCommand
    data object Quit : NetCommand
}

class NetSession(
    private val store: Store,
    private val credentials: CredentialSource,
    private val teamHint: String? = null,
    private val debug: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commands = CommandChannel<NetCommand>(CommandChannel.UNLIMITED)
    private var job: Job? = null
    private var client: HttpClient? = null
    private var creds: Credentials? = null
    private var activeTeamId: String? = null
    private var preferredTeam: String? = null
    private var activeChannel: String? = null

    fun start() {
        if (job != null) return
        job = scope.launch { run() }
    }

    fun stop() {
        commands.trySend(NetCommand.Quit)
        job?.cancel()
        job = null
        scope.launch { client?.close() }
    }

    fun send(cmd: NetCommand) {
        commands.trySend(cmd)
    }

    private fun emit(update: Update, teamId: String = activeTeamId.orEmpty()) {
        store.apply(StoreItem(update, teamId))
    }

    private suspend fun run() {
        emit(Update.ConnectionUpdate(Connection.Connecting))
        creds = resolveCredentials() ?: return
        val http = createHttpClient().also { client = it }
        var backoff = 1L
        while (scope.isActive) {
            try {
                val team = pickTeam(creds!!) ?: break
                activeTeamId = team.teamId
                emit(
                    Update.Teams(
                        creds!!.workspaces.map { Team(it.teamId, it.name, it.url) },
                        team.teamId,
                    ),
                )
                emit(Update.ConnectionUpdate(Connection.Connecting), team.teamId)
                val api = SlackApi(http, team.url, team.token, creds!!.cookie)
                bootstrap(api, team)
                val socket = RealtimeSocket(http, team.teamId, team.token, creds!!.cookie)
                socket.connect()
                emit(Update.ConnectionUpdate(Connection.Online), team.teamId)
                backoff = 1
                sessionLoop(api, socket, team)
            } catch (e: SlackApiException) {
                if (e.isAuthError) {
                    credentials.clearSession()
                    val extracted = credentials.extractFromDesktop()
                    if (extracted is CredentialResult.Ok) {
                        creds = extracted.credentials
                        runCatching { credentials.saveSession(extracted.credentials) }
                        continue
                    }
                    emit(Update.NeedManual("Session expired: ${e.slackError}"))
                    waitForManual()
                    continue
                }
                emit(Update.Status(e.message ?: "API error"))
                emit(Update.ConnectionUpdate(Connection.Offline))
                delay(backoff * 1000)
                backoff = min(backoff * 2, 30)
            } catch (e: Exception) {
                if (debug) println("net: ${e::class.simpleName}")
                emit(Update.ConnectionUpdate(Connection.Offline))
                emit(Update.Status(e.message ?: "Disconnected"))
                delay(backoff * 1000)
                backoff = min(backoff * 2, 30)
            }
        }
    }

    private suspend fun resolveCredentials(): Credentials? {
        when (val saved = credentials.loadSavedSession()) {
            is CredentialResult.Ok -> return saved.credentials
            is CredentialResult.Err -> Unit
        }
        when (val extracted = credentials.extractFromDesktop()) {
            is CredentialResult.Ok -> {
                runCatching { credentials.saveSession(extracted.credentials) }
                return extracted.credentials
            }
            is CredentialResult.Err -> {
                emit(Update.NeedManual(extracted.message))
                return waitForManual()
            }
        }
    }

    private suspend fun waitForManual(): Credentials? {
        while (true) {
            when (val cmd = commands.receive()) {
                is NetCommand.Manual -> {
                    val c = Credentials.fromManual(cmd.url, cmd.token, cmd.cookie)
                    creds = c
                    runCatching { credentials.saveSession(c) }
                    emit(Update.ManualDone)
                    return c
                }
                NetCommand.Quit -> return null
                else -> Unit
            }
        }
    }

    private fun pickTeam(c: Credentials): WorkspaceCred? {
        preferredTeam?.let { id -> c.workspaces.find { it.teamId == id } }?.let { return it }
        teamHint?.let { hint ->
            c.workspaces.find {
                it.name.contains(hint, ignoreCase = true) ||
                    it.url.contains(hint, ignoreCase = true)
            }
        }?.let { return it }
        return c.workspaces.firstOrNull()
    }

    private suspend fun bootstrap(api: SlackApi, team: WorkspaceCred) {
        val auth = api.call("auth.test")
        val selfId = auth.str("user_id").orEmpty()
        emit(Update.Self(selfId, team.name), team.teamId)

        val users = api.call("users.list", mapOf("limit" to "200"))
        val userList = users.arr("members").mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if (o.bool("deleted") == true) return@mapNotNull null
            User(
                id = o.str("id").orEmpty(),
                name = o.obj("profile")?.str("display_name")?.takeIf { it.isNotEmpty() }
                    ?: o.str("real_name")
                    ?: o.str("name").orEmpty(),
                isBot = o.bool("is_bot") == true || o.str("id") == "USLACKBOT",
                image = o.obj("profile")?.str("image_48").orEmpty(),
            )
        }
        emit(Update.Users(userList), team.teamId)

        val conv = api.call(
            "conversations.list",
            mapOf(
                "types" to "public_channel,private_channel,mpim,im",
                "limit" to "500",
                "exclude_archived" to "true",
            ),
        )
        val channels = conv.arr("channels").mapNotNull { parseChannel(it as? JsonObject ?: return@mapNotNull null) }
        emit(Update.Channels(channels), team.teamId)

        runCatching {
            val counts = api.call("client.counts")
            val unreads = buildList {
                for (key in listOf("channels", "mpims", "ims")) {
                    for (el in counts.arr(key)) {
                        val o = el as? JsonObject ?: continue
                        add(
                            UnreadInfo(
                                id = o.str("id").orEmpty(),
                                hasUnreads = (o["has_unreads"]?.jsonPrimitive?.content == "true") ||
                                    (o.bool("has_unreads") == true),
                                mentions = o["mention_count"]?.jsonPrimitive?.intOrNull ?: 0,
                                latest = o.str("latest").orEmpty(),
                            ),
                        )
                    }
                }
            }
            emit(Update.Unreads(unreads), team.teamId)
        }

        runCatching {
            val prefs = api.call("users.prefs.get")
            val p = prefs.obj("prefs")
            val mode = SidebarMode.parse(p?.str("sidebar_behavior"))
            emit(Update.SidebarModeUpdate(mode), team.teamId)
        }
    }

    private fun parseChannel(o: JsonObject): Channel {
        val isIm = o.bool("is_im") == true
        val isMpim = o.bool("is_mpim") == true
        val isPrivate = o.bool("is_private") == true && !isIm && !isMpim
        val kind = when {
            isIm -> ChannelKind.Im
            isMpim -> ChannelKind.Mpim
            isPrivate -> ChannelKind.Private
            else -> ChannelKind.Channel
        }
        val name = when (kind) {
            ChannelKind.Im -> o.str("user") ?: o.str("id").orEmpty()
            else -> o.str("name") ?: o.str("name_normalized").orEmpty()
        }
        return Channel(
            id = o.str("id").orEmpty(),
            name = name,
            kind = kind,
            userId = o.str("user"),
            dormant = o.obj("properties")?.bool("is_dormant") == true,
        )
    }

    private suspend fun sessionLoop(api: SlackApi, socket: RealtimeSocket, team: WorkspaceCred) {
        var lastTraffic = epochMs()
        var lastPing = 0L
        while (scope.isActive) {
            while (true) {
                val cmd = commands.tryReceive().getOrNull() ?: break
                when (cmd) {
                    NetCommand.Quit -> {
                        socket.close()
                        return
                    }
                    is NetCommand.SwitchTeam -> {
                        preferredTeam = cmd.teamId
                        socket.close()
                        return
                    }
                    is NetCommand.Manual -> {
                        creds = Credentials.fromManual(cmd.url, cmd.token, cmd.cookie)
                        runCatching { credentials.saveSession(creds!!) }
                        emit(Update.ManualDone)
                        socket.close()
                        return
                    }
                    is NetCommand.Open -> openChannel(api, cmd.channel, team.teamId)
                    is NetCommand.Send -> sendMessage(api, cmd)
                    is NetCommand.Older -> loadOlder(api, cmd, team.teamId)
                    is NetCommand.OpenThread -> openThread(api, cmd, team.teamId)
                    is NetCommand.React -> react(api, cmd)
                    is NetCommand.Mark -> mark(api, cmd)
                    is NetCommand.Typing -> {
                        runCatching {
                            socket.send(
                                """{"type":"typing","channel":"${cmd.channel}"}""",
                            )
                        }
                    }
                }
            }

            val now = epochMs()
            if (now - lastPing > 20_000) {
                socket.ping()
                lastPing = now
            }
            if (now - lastTraffic > 60_000) {
                socket.close()
                error("stale socket")
            }

            val frame = socket.next(500) ?: continue
            lastTraffic = epochMs()
            when (val ev = Events.parse(frame)) {
                SlackEvent.Hello, is SlackEvent.Pong, SlackEvent.ReconnectUrl -> Unit
                is SlackEvent.MessageEvent -> {
                    emit(Update.MessageUpdate(ev.channel, ev.message), team.teamId)
                }
                is SlackEvent.MessageChanged -> emit(
                    Update.Edited(ev.channel, ev.ts, ev.text, ev.files),
                    team.teamId,
                )
                is SlackEvent.MessageDeleted -> emit(Update.Deleted(ev.channel, ev.ts), team.teamId)
                is SlackEvent.ReplyCount -> emit(
                    Update.ReplyCount(ev.channel, ev.ts, ev.count),
                    team.teamId,
                )
                is SlackEvent.Typing -> emit(
                    Update.TypingUpdate(ev.channel, ev.user, epochMs()),
                    team.teamId,
                )
                is SlackEvent.Marked -> emit(
                    Update.Marked(ev.channel, ev.ts, ev.unread, ev.mentions),
                    team.teamId,
                )
                is SlackEvent.Reaction -> emit(
                    Update.ReactionUpdate(ev.channel, ev.ts, ev.name, ev.user, ev.added),
                    team.teamId,
                )
                is SlackEvent.Presence -> emit(
                    Update.Presence(ev.users, ev.state == "active"),
                    team.teamId,
                )
                is SlackEvent.UserInvalidated, is SlackEvent.Unknown -> Unit
            }
        }
    }

    private suspend fun openChannel(api: SlackApi, channel: String, teamId: String) {
        activeChannel = channel
        store.selectChannel(channel)
        val history = api.call(
            "conversations.history",
            mapOf("channel" to channel, "limit" to "40"),
        )
        val messages = history.arr("messages").mapNotNull { parseMessage(it as? JsonObject ?: return@mapNotNull null) }
            .reversed()
        emit(
            Update.History(channel, messages, hasMore = history.bool("has_more") == true),
            teamId,
        )
        messages.lastOrNull()?.let { mark(api, NetCommand.Mark(channel, it.ts)) }
    }

    private suspend fun loadOlder(api: SlackApi, cmd: NetCommand.Older, teamId: String) {
        val history = api.call(
            "conversations.history",
            mapOf("channel" to cmd.channel, "latest" to cmd.before, "limit" to "40", "inclusive" to "false"),
        )
        val messages = history.arr("messages").mapNotNull { parseMessage(it as? JsonObject ?: return@mapNotNull null) }
            .reversed()
        emit(
            Update.History(cmd.channel, messages, hasMore = history.bool("has_more") == true, older = true),
            teamId,
        )
    }

    private suspend fun openThread(api: SlackApi, cmd: NetCommand.OpenThread, teamId: String) {
        val replies = api.call(
            "conversations.replies",
            mapOf("channel" to cmd.channel, "ts" to cmd.ts, "limit" to "100"),
        )
        val messages = replies.arr("messages").mapNotNull { parseMessage(it as? JsonObject ?: return@mapNotNull null) }
        emit(Update.ThreadHistory(cmd.channel, cmd.ts, messages), teamId)
    }

    private suspend fun sendMessage(api: SlackApi, cmd: NetCommand.Send) {
        val params = buildMap {
            put("channel", cmd.channel)
            put("text", cmd.text)
            put("as_user", "true")
            cmd.threadTs?.let { put("thread_ts", it) }
        }
        val res = api.call("chat.postMessage", params)
        val msg = res.obj("message")?.let { parseMessage(it) } ?: return
        emit(Update.MessageUpdate(cmd.channel, msg), activeTeamId.orEmpty())
    }

    private suspend fun react(api: SlackApi, cmd: NetCommand.React) {
        val method = if (cmd.add) "reactions.add" else "reactions.remove"
        api.call(method, mapOf("channel" to cmd.channel, "timestamp" to cmd.ts, "name" to cmd.name))
    }

    private suspend fun mark(api: SlackApi, cmd: NetCommand.Mark) {
        runCatching {
            api.call("conversations.mark", mapOf("channel" to cmd.channel, "ts" to cmd.ts))
            emit(Update.Marked(cmd.channel, cmd.ts, unread = 0, mentions = 0), activeTeamId.orEmpty())
        }
    }

    private fun parseMessage(o: JsonObject): Message = Message(
        ts = o.str("ts").orEmpty(),
        user = o.str("user") ?: o.str("bot_id"),
        botName = o.str("username") ?: o.obj("bot_profile")?.str("name"),
        text = o.str("text").orEmpty(),
        threadTs = o.str("thread_ts"),
        subtype = o.str("subtype"),
        replyCount = o["reply_count"]?.jsonPrimitive?.intOrNull ?: 0,
        files = Events.parseFiles(o),
        reactions = o.arr("reactions").mapNotNull { el ->
            val r = el as? JsonObject ?: return@mapNotNull null
            Reaction(
                name = r.str("name").orEmpty(),
                count = r["count"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        },
        hidden = o.bool("hidden") == true,
    )
}
