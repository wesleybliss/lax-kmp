package com.gammagamma.lax.store

import com.gammagamma.lax.model.*

sealed interface Update {
    data class Self(val userId: String, val teamName: String) : Update
    data class Channels(val channels: List<Channel>) : Update
    data class Users(val users: List<User>) : Update
    data class History(
        val channel: String,
        val messages: List<Message>,
        val hasMore: Boolean = false,
        val older: Boolean = false,
    ) : Update

    data class ThreadHistory(
        val channel: String,
        val threadTs: String,
        val messages: List<Message>,
    ) : Update

    data class ThreadMessage(
        val channel: String,
        val threadTs: String,
        val message: Message,
    ) : Update

    data class ReplyCount(val channel: String, val ts: String, val count: Int) : Update
    data class ReactionUpdate(
        val channel: String,
        val ts: String,
        val name: String,
        val user: String,
        val added: Boolean,
    ) : Update

    data class TypingUpdate(val channel: String, val user: String, val atMs: Long) : Update
    data class Presence(val users: List<String>, val active: Boolean) : Update
    data class MessageUpdate(val channel: String, val message: Message) : Update
    data class Edited(
        val channel: String,
        val ts: String,
        val text: String,
        val files: List<FileAttachment> = emptyList(),
    ) : Update

    data class Deleted(val channel: String, val ts: String) : Update
    data class Marked(
        val channel: String,
        val ts: String,
        val unread: Int? = null,
        val mentions: Int? = null,
    ) : Update

    data class Unreads(val items: List<UnreadInfo>) : Update
    data class Left(val channel: String) : Update
    data class SidebarModeUpdate(val mode: SidebarMode) : Update
    data class Notify(val prefs: NotifyPrefs) : Update
    data class FeedUpdate(val kind: FeedKind, val entries: List<FeedEntry>) : Update
    data class Teams(val list: List<Team>, val active: String) : Update
    data class NeedManual(val reason: String) : Update
    data object ManualDone : Update
    data class ConnectionUpdate(val connection: Connection) : Update
    data class Status(val text: String) : Update
    data class Toast(val text: String) : Update
}

data class UnreadInfo(
    val id: String,
    val hasUnreads: Boolean,
    val mentions: Int = 0,
    val latest: String = "",
)

data class StoreItem(val update: Update, val teamId: String = "")
