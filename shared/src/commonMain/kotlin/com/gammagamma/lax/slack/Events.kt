package com.gammagamma.lax.slack

import com.gammagamma.lax.model.FileAttachment
import com.gammagamma.lax.model.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SlackEvent {
    data object Hello : SlackEvent
    data class MessageEvent(val message: Message, val channel: String) : SlackEvent
    data class MessageChanged(val channel: String, val ts: String, val text: String, val files: List<FileAttachment>) : SlackEvent
    data class MessageDeleted(val channel: String, val ts: String) : SlackEvent
    data class ReplyCount(val channel: String, val ts: String, val count: Int) : SlackEvent
    data class Typing(val channel: String, val user: String, val threadTs: String?) : SlackEvent
    data class Marked(val channel: String, val ts: String, val unread: Int?, val mentions: Int?) : SlackEvent
    data class Reaction(val added: Boolean, val user: String, val name: String, val channel: String, val ts: String) : SlackEvent
    data class Presence(val state: String, val users: List<String>) : SlackEvent
    data class UserInvalidated(val user: String) : SlackEvent
    data class Pong(val replyTo: Int) : SlackEvent
    data object ReconnectUrl : SlackEvent
    data class Unknown(val type: String) : SlackEvent
}

object Events {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(frame: String): SlackEvent {
        val root = runCatching { json.parseToJsonElement(frame).jsonObject }.getOrNull()
            ?: return SlackEvent.Unknown("bad_frame")
        val kind = root.str("type") ?: return SlackEvent.Unknown("missing_type")
        return when (kind) {
            "hello" -> SlackEvent.Hello
            "reconnect_url" -> SlackEvent.ReconnectUrl
            "pong" -> SlackEvent.Pong(root["reply_to"]?.jsonPrimitive?.intOrNull ?: 0)
            "user_typing" -> SlackEvent.Typing(
                channel = root.str("channel").orEmpty(),
                user = root.str("user").orEmpty(),
                threadTs = root.str("thread_ts"),
            )
            "presence_change" -> SlackEvent.Presence(
                state = root.str("presence").orEmpty(),
                users = root.str("user")?.let { listOf(it) }
                    ?: (root["users"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            )
            "user_invalidated" -> SlackEvent.UserInvalidated(root.str("user").orEmpty())
            "im_marked", "channel_marked", "group_marked", "mpim_marked" -> SlackEvent.Marked(
                channel = root.str("channel").orEmpty(),
                ts = root.str("ts").orEmpty(),
                unread = root["unread_count_display"]?.jsonPrimitive?.intOrNull,
                mentions = root["mention_count"]?.jsonPrimitive?.intOrNull
                    ?: root["mention_count_display"]?.jsonPrimitive?.intOrNull,
            )
            "reaction_added", "reaction_removed" -> {
                val item = root.obj("item")
                SlackEvent.Reaction(
                    added = kind == "reaction_added",
                    user = root.str("user").orEmpty(),
                    name = root.str("reaction").orEmpty(),
                    channel = item?.str("channel").orEmpty(),
                    ts = item?.str("ts").orEmpty(),
                )
            }
            "message" -> parseMessage(root)
            else -> SlackEvent.Unknown(kind)
        }
    }

    private fun parseMessage(o: JsonObject): SlackEvent {
        when (o.str("subtype")) {
            "message_changed" -> {
                val inner = o.obj("message") ?: return SlackEvent.Unknown("message_changed")
                return SlackEvent.MessageChanged(
                    channel = o.str("channel").orEmpty(),
                    ts = inner.str("ts").orEmpty(),
                    text = inner.str("text").orEmpty(),
                    files = parseFiles(inner),
                )
            }
            "message_replied" -> {
                val inner = o.obj("message") ?: return SlackEvent.Unknown("message_replied")
                return SlackEvent.ReplyCount(
                    channel = o.str("channel").orEmpty(),
                    ts = inner.str("ts").orEmpty(),
                    count = inner["reply_count"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
            "message_deleted" -> return SlackEvent.MessageDeleted(
                channel = o.str("channel").orEmpty(),
                ts = o.str("deleted_ts").orEmpty(),
            )
        }
        val msg = Message(
            ts = o.str("ts").orEmpty(),
            user = o.str("user") ?: o.str("bot_id"),
            botName = botName(o),
            text = o.str("text").orEmpty(),
            threadTs = o.str("thread_ts"),
            subtype = o.str("subtype"),
            replyCount = o["reply_count"]?.jsonPrimitive?.intOrNull ?: 0,
            files = parseFiles(o),
            hidden = o.bool("hidden") == true,
        )
        return SlackEvent.MessageEvent(msg, o.str("channel").orEmpty())
    }

    fun parseFiles(o: JsonObject): List<FileAttachment> =
        o.arr("files").mapNotNull { el ->
            val f = el as? JsonObject ?: return@mapNotNull null
            FileAttachment(
                id = f.str("id").orEmpty(),
                name = f.str("name") ?: f.str("title").orEmpty(),
                mimetype = f.str("mimetype").orEmpty(),
                link = f.str("permalink").orEmpty(),
                url = f.str("url_private") ?: f.str("url_private_download").orEmpty(),
                thumb = f.str("thumb_360") ?: f.str("thumb_160").orEmpty(),
                thumbW = f["thumb_360_w"]?.jsonPrimitive?.intOrNull
                    ?: f["thumb_160_w"]?.jsonPrimitive?.intOrNull ?: 0,
                thumbH = f["thumb_360_h"]?.jsonPrimitive?.intOrNull
                    ?: f["thumb_160_h"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }

    private fun botName(o: JsonObject): String? =
        o.str("username") ?: o.obj("bot_profile")?.str("name")
}
