package com.gammagamma.lax.slack

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real-time socket for session (`xoxc`) tokens:
 * `wss://wss-primary.slack.com/?token=...&gateway_server=<TEAM>-1&slack_client=desktop&batch_presence_aware=1`
 * with the `d` cookie. No `rtm.connect` needed.
 */
class RealtimeSocket(
    private val client: HttpClient,
    private val teamId: String,
    private val token: String,
    private val cookie: String,
) {
    private var session: WebSocketSession? = null
    private var nextId = 1

    suspend fun connect() {
        val path =
            "/?token=$token&gateway_server=$teamId-1&slack_client=desktop&batch_presence_aware=1&no_query_on_subscribe=1"
        session = client.webSocketSession {
            method = HttpMethod.Get
            url("wss", "wss-primary.slack.com", 443, path)
            header("Origin", "https://app.slack.com")
            header("Cookie", "d=$cookie")
        }
    }

    suspend fun next(timeoutMs: Long = 1_000): String? {
        val s = session ?: return null
        val frame = withTimeoutOrNull(timeoutMs) { s.incoming.receive() } ?: return null
        return when (frame) {
            is Frame.Text -> frame.readText()
            is Frame.Close -> {
                close()
                null
            }
            else -> null
        }
    }

    suspend fun send(json: String) {
        session?.send(Frame.Text(json))
    }

    suspend fun ping() {
        send("""{"type":"ping","id":${nextId++}}""")
    }

    suspend fun close() {
        session?.close()
        session = null
    }
}
