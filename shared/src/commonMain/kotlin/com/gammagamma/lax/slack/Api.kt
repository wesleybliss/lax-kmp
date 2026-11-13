package com.gammagamma.lax.slack

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SlackApiException(
    val slackError: String,
    val status: Int = 0,
    val retryAfterSec: Int = 0,
) : Exception(slackError) {
    val isAuthError: Boolean
        get() = slackError in AUTH_ERRORS

    companion object {
        private val AUTH_ERRORS = setOf(
            "invalid_auth", "token_revoked", "token_expired",
            "not_authed", "account_inactive", "no_permission",
        )
    }
}

class SlackApi(
    private val client: HttpClient,
    baseUrl: String,
    private val token: String,
    private val cookie: String,
    private val userAgent: String = "lax-slack-clone/0.1.0",
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun call(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        var attempt = 0
        while (true) {
            val response = client.submitForm(
                url = "$base/api/$method",
                formParameters = Parameters.build {
                    append("token", token)
                    params.forEach { (k, v) -> append(k, v) }
                },
            ) {
                header("Cookie", "d=$cookie")
                header("User-Agent", userAgent)
            }
            val text = response.bodyAsText()
            if (response.status.value == 429) {
                val wait = response.headers["Retry-After"]?.toIntOrNull()?.coerceIn(1, 60) ?: 1
                if (attempt >= 2) throw SlackApiException("rate_limited", 429, wait)
                attempt++
                kotlinx.coroutines.delay(wait * 1000L)
                continue
            }
            if (!response.status.isSuccess()) {
                throw SlackApiException("http_${response.status.value}", response.status.value)
            }
            val root = json.parseToJsonElement(text).jsonObject
            val ok = root["ok"]?.jsonPrimitive?.booleanOrNull == true
            if (ok) return root
            val err = root["error"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            throw SlackApiException(err, response.status.value)
        }
    }

    suspend fun download(url: String, private: Boolean = false): ByteArray {
        val response = client.get(url) {
            if (private) {
                header("Cookie", "d=$cookie")
                header("Authorization", "Bearer $token")
                header("User-Agent", userAgent)
            }
        }
        if (!response.status.isSuccess()) error("download failed: ${response.status}")
        return response.bodyAsBytes()
    }
}

fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): List<JsonElement> =
    (this[key] as? kotlinx.serialization.json.JsonArray)?.toList().orEmpty()
