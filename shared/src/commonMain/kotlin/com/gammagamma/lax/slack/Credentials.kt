package com.gammagamma.lax.slack

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WorkspaceCred(
    val teamId: String,
    val name: String,
    val url: String,
    val token: String,
)

@Serializable
data class Credentials(
    val cookie: String,
    val workspaces: List<WorkspaceCred>,
) {
    fun wipe() {
        // Best-effort: strings are immutable on JVM; callers should drop references.
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): Credentials {
            val c = json.decodeFromString(serializer(), text)
            require(c.workspaces.isNotEmpty() && c.cookie.startsWith("xoxd-")) {
                "format changed"
            }
            return c
        }

        fun fromManual(url: String, token: String, cookie: String): Credentials {
            val teamId = Regex("""[T][A-Z0-9]+""").find(token)?.value ?: "Tmanual"
            val name = url.removePrefix("https://").removePrefix("http://")
                .substringBefore('.')
                .ifEmpty { "Workspace" }
            return Credentials(
                cookie = cookie.trim(),
                workspaces = listOf(
                    WorkspaceCred(
                        teamId = teamId,
                        name = name,
                        url = url.trim().trimEnd('/'),
                        token = token.trim(),
                    ),
                ),
            )
        }

        fun toJson(c: Credentials): String = Json.encodeToString(serializer(), c)
    }
}

sealed class CredentialResult {
    data class Ok(val credentials: Credentials) : CredentialResult()
    data class Err(val message: String) : CredentialResult()
}

interface CredentialSource {
    fun loadSavedSession(): CredentialResult
    fun saveSession(credentials: Credentials)
    fun clearSession()
    fun extractFromDesktop(): CredentialResult
}

expect fun createCredentialSource(): CredentialSource
