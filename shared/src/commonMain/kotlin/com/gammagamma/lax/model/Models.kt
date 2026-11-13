package com.gammagamma.lax.model

enum class ChannelKind { Channel, Private, Im, Mpim }

enum class SectionPlace { None, Starred, Above, Below }

enum class SidebarMode {
    All, Active, Unread, Mentions;

    companion object {
        fun parse(value: String?): SidebarMode = when (value) {
            null, "" -> All
            "hide_inactive_channels" -> Active
            "show_mentions_only" -> Mentions
            "hide_read_channels", "hide_read_channels_unless_starred" -> Unread
            else -> Active
        }
    }
}

enum class NotifyMode {
    Everything, Mentions, Nothing;

    companion object {
        fun parse(value: String?): NotifyMode = when (value) {
            "everything" -> Everything
            "nothing" -> Nothing
            else -> Mentions
        }
    }
}

enum class Connection { Connecting, Online, Offline }

enum class FeedKind { Threads, Drafts, Saved }

data class User(
    val id: String,
    val name: String,
    val isBot: Boolean = false,
    val image: String = "",
    val online: Boolean = false,
)

data class Team(
    val id: String,
    val name: String,
    val url: String,
)

data class Reaction(
    val name: String,
    val count: Int,
    val mine: Boolean = false,
)

data class FileAttachment(
    val id: String,
    val name: String,
    val mimetype: String = "",
    val link: String = "",
    val url: String = "",
    val thumb: String = "",
    val thumbW: Int = 0,
    val thumbH: Int = 0,
)

data class Message(
    val ts: String,
    val user: String? = null,
    val botName: String? = null,
    val text: String = "",
    val threadTs: String? = null,
    val subtype: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val replyCount: Int = 0,
    val files: List<FileAttachment> = emptyList(),
    val hidden: Boolean = false,
) {
    val isThreadReply: Boolean
        get() = threadTs != null && threadTs != ts
}

data class Channel(
    val id: String,
    val name: String,
    val kind: ChannelKind,
    val userId: String? = null,
    val messages: List<Message> = emptyList(),
    val unread: Boolean = false,
    val mentions: Int = 0,
    val latest: String = "",
    val activity: String = "",
    val hasOlder: Boolean = false,
    val historyLoaded: Boolean = false,
    val listed: Boolean = true,
    val dormant: Boolean = false,
    val section: String = "",
    val place: SectionPlace = SectionPlace.None,
    val sectionRank: Int = 0,
    val typing: List<Typing> = emptyList(),
)

data class Typing(val user: String, val atMs: Long)

data class Thread(
    val channel: String,
    val ts: String,
    val messages: List<Message> = emptyList(),
    val loaded: Boolean = false,
)

data class FeedEntry(
    val channel: String,
    val ts: String,
    val threadTs: String = "",
    val user: String = "",
    val text: String = "",
    val note: String = "",
    val replyUser: String = "",
    val replyText: String = "",
    val unread: Boolean = false,
)

data class ChannelNotify(
    val desktop: NotifyMode? = null,
    val muted: Boolean = false,
    val mutedUntil: Long = 0,
    val quietBroadcast: Boolean = false,
)

data class NotifyPrefs(
    val desktop: NotifyMode = NotifyMode.Mentions,
    val group: NotifyMode = NotifyMode.Everything,
    val keywords: String = "",
    val hideText: Boolean = false,
    val channels: Map<String, ChannelNotify> = emptyMap(),
)

const val MAX_MESSAGES_PER_CHANNEL = 1000
const val TYPING_MS = 5_000L
