package com.gammagamma.lax.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammagamma.lax.model.Channel
import com.gammagamma.lax.model.ChannelKind
import com.gammagamma.lax.model.Message
import com.gammagamma.lax.model.User
import com.gammagamma.lax.slack.Mrkdwn
import com.gammagamma.lax.slack.MrkdwnStyle
import com.gammagamma.lax.slack.NameResolver
import com.gammagamma.lax.ui.theme.LocalLaxColors

@Composable
fun ChatPane(
    channel: Channel?,
    users: Map<String, User>,
    teamName: String,
    connectionLabel: String,
    chatSize: Int,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLaxColors.current
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(colors.surface)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = channelTitle(channel, users),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = connectionLabel.ifEmpty { teamName },
                    fontSize = 12.sp,
                    color = colors.muted,
                )
            }
        }

        if (channel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a conversation", color = colors.muted)
            }
            return
        }

        val listState = rememberLazyListState()
        LaunchedEffect(channel.id, channel.messages.lastOrNull()?.ts) {
            if (channel.messages.isNotEmpty()) {
                listState.animateScrollToItem(channel.messages.lastIndex)
            }
        }
        LaunchedEffect(listState.firstVisibleItemIndex) {
            if (listState.firstVisibleItemIndex == 0 && channel.hasOlder && channel.messages.isNotEmpty()) {
                onLoadOlder()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(channel.messages, key = { _, m -> m.ts }) { index, message ->
                val prev = channel.messages.getOrNull(index - 1)
                val grouped = prev != null &&
                    prev.user == message.user &&
                    !message.isThreadReply &&
                    tsClose(prev.ts, message.ts)
                MessageRow(
                    message = message,
                    users = users,
                    grouped = grouped,
                    chatSize = chatSize,
                )
            }
        }

        val typing = channel.typing.mapNotNull { users[it.user]?.name ?: it.user }
        if (typing.isNotEmpty()) {
            Text(
                text = when (typing.size) {
                    1 -> "${typing[0]} is typing…"
                    2 -> "${typing[0]} and ${typing[1]} are typing…"
                    else -> "Several people are typing…"
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = colors.muted,
                fontSize = 12.sp,
            )
        }

        Composer(
            value = draft,
            onValueChange = onDraftChange,
            onSend = onSend,
            placeholder = "Message ${channelTitle(channel, users)}",
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun MessageRow(
    message: Message,
    users: Map<String, User>,
    grouped: Boolean,
    chatSize: Int,
) {
    val colors = LocalLaxColors.current
    val author = message.botName
        ?: message.user?.let { users[it]?.name ?: it }
        ?: "system"
    val avatarColor = colors.avatars[
        (message.user ?: author).hashCode().and(0x7fffffff) % colors.avatars.size,
    ]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (grouped) 2.dp else 12.dp, bottom = 2.dp),
    ) {
        if (grouped) {
            Spacer(Modifier.width(48.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    author.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = colors.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!grouped) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(author, fontWeight = FontWeight.SemiBold, color = colors.text, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(formatTs(message.ts), color = colors.muted, fontSize = 12.sp)
                }
            }
            MrkdwnText(
                raw = message.text,
                users = users,
                fontSize = chatSize,
            )
            if (message.reactions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    message.reactions.forEach { r ->
                        Text(
                            text = ":${r.name}: ${r.count}",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (r.mine) colors.secondaryContainer else colors.rail)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 12.sp,
                            color = colors.text,
                        )
                    }
                }
            }
            if (message.replyCount > 0) {
                Text(
                    "${message.replyCount} ${if (message.replyCount == 1) "reply" else "replies"}",
                    color = colors.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MrkdwnText(raw: String, users: Map<String, User>, fontSize: Int) {
    val colors = LocalLaxColors.current
    val rendered = Mrkdwn.render(
        raw,
        object : NameResolver {
            override fun resolveUser(id: String) = users[id]?.name
            override fun resolveChannel(id: String) = id
        },
    )
    val annotated = buildAnnotatedString {
        append(rendered.text)
        rendered.runs.forEach { run ->
            val style = when (run.style) {
                MrkdwnStyle.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
                MrkdwnStyle.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
                MrkdwnStyle.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                MrkdwnStyle.Code, MrkdwnStyle.CodeBlock -> SpanStyle(
                    background = colors.rail,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                MrkdwnStyle.Link, MrkdwnStyle.Mention -> SpanStyle(color = colors.accent)
                MrkdwnStyle.Quote -> SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic)
            }
            addStyle(style, run.start, run.end.coerceAtMost(rendered.text.length))
        }
    }
    Text(annotated, color = colors.text, fontSize = fontSize.sp)
}

private fun channelTitle(channel: Channel?, users: Map<String, User>): String {
    if (channel == null) return "Lax"
    return when (channel.kind) {
        ChannelKind.Im -> users[channel.userId]?.name ?: channel.name
        ChannelKind.Mpim -> channel.name
        else -> "#${channel.name}"
    }
}

private fun tsClose(a: String, b: String): Boolean {
    val as_ = a.substringBefore('.').toLongOrNull() ?: return false
    val bs = b.substringBefore('.').toLongOrNull() ?: return false
    return kotlin.math.abs(as_ - bs) <= 300
}

private fun formatTs(ts: String): String {
    val secs = ts.substringBefore('.').toLongOrNull() ?: return ts
    val mins = (secs / 60) % 60
    val hours = (secs / 3600) % 24
    val mm = if (mins < 10) "0$mins" else "$mins"
    return "$hours:$mm"
}
