package com.gammagamma.lax.slack

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
