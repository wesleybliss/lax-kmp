package com.gammagamma.lax.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gammagamma.lax.model.Channel
import com.gammagamma.lax.model.ChannelKind
import com.gammagamma.lax.model.SidebarMode
import com.gammagamma.lax.model.User
import com.gammagamma.lax.ui.theme.LocalLaxColors

@Composable
fun Sidebar(
    teamName: String,
    channels: List<Channel>,
    users: Map<String, User>,
    selectedId: String?,
    sidebarMode: SidebarMode,
    rowHeight: Dp,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLaxColors.current
    val visible = channels.filter { it.listed }.filter { ch ->
        when (sidebarMode) {
            SidebarMode.All -> true
            SidebarMode.Active -> !ch.dormant || ch.unread || ch.id == selectedId
            SidebarMode.Unread -> ch.unread || ch.id == selectedId
            SidebarMode.Mentions -> ch.mentions > 0 || ch.id == selectedId
        }
    }
    val starred = visible.filter { it.place.name == "Starred" || it.section.equals("starred", true) }
    val channelsList = visible.filter { it.kind == ChannelKind.Channel || it.kind == ChannelKind.Private }
        .sortedBy { it.name.lowercase() }
    val dms = visible.filter { it.kind == ChannelKind.Im || it.kind == ChannelKind.Mpim }
        .sortedByDescending { it.activity.ifEmpty { it.latest } }
    val apps = dms.filter { ch -> users[ch.userId]?.isBot == true }
    val people = dms.filter { ch -> users[ch.userId]?.isBot != true }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(colors.sidebar),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = teamName.ifEmpty { "Lax" },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            item { NavHint("Threads") }
            item { NavHint("Huddles") }
            item { NavHint("Drafts & sent") }
            item { NavHint("Saved") }

            if (channelsList.isNotEmpty()) {
                item { SectionLabel("Channels") }
                items(channelsList, key = { it.id }) { ch ->
                    ChannelRow(
                        title = "# ${ch.name}",
                        selected = ch.id == selectedId,
                        unread = ch.unread,
                        mentions = ch.mentions,
                        rowHeight = rowHeight,
                        onClick = { onSelect(ch.id) },
                    )
                }
            }
            if (people.isNotEmpty()) {
                item { SectionLabel("Direct messages") }
                items(people, key = { it.id }) { ch ->
                    val user = users[ch.userId]
                    ChannelRow(
                        title = user?.name ?: ch.name,
                        selected = ch.id == selectedId,
                        unread = ch.unread,
                        mentions = ch.mentions,
                        online = user?.online == true,
                        rowHeight = rowHeight,
                        onClick = { onSelect(ch.id) },
                    )
                }
            }
            if (apps.isNotEmpty()) {
                item { SectionLabel("Apps") }
                items(apps, key = { it.id }) { ch ->
                    val user = users[ch.userId]
                    ChannelRow(
                        title = user?.name ?: ch.name,
                        selected = ch.id == selectedId,
                        unread = ch.unread,
                        mentions = ch.mentions,
                        rowHeight = rowHeight,
                        onClick = { onSelect(ch.id) },
                    )
                }
            }
            if (starred.isNotEmpty()) {
                item { SectionLabel("Starred") }
                items(starred, key = { "star-${it.id}" }) { ch ->
                    ChannelRow(
                        title = displayName(ch, users),
                        selected = ch.id == selectedId,
                        unread = ch.unread,
                        mentions = ch.mentions,
                        rowHeight = rowHeight,
                        onClick = { onSelect(ch.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavHint(label: String) {
    val colors = LocalLaxColors.current
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = colors.muted,
        fontSize = 14.sp,
    )
}

@Composable
private fun SectionLabel(label: String) {
    val colors = LocalLaxColors.current
    Text(
        text = label,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp),
        color = colors.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ChannelRow(
    title: String,
    selected: Boolean,
    unread: Boolean,
    mentions: Int,
    rowHeight: Dp,
    onClick: () -> Unit,
    online: Boolean = false,
) {
    val colors = LocalLaxColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.secondaryContainer else colors.sidebar)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (online) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.success),
            )
        } else {
            Box(Modifier.width(8.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colors.text,
            fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (mentions > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.danger)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("$mentions", color = colors.onPrimary, fontSize = 11.sp)
            }
        } else if (unread) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
    }
}

private fun displayName(ch: Channel, users: Map<String, User>): String =
    when (ch.kind) {
        ChannelKind.Im, ChannelKind.Mpim -> users[ch.userId]?.name ?: ch.name
        else -> "# ${ch.name}"
    }
