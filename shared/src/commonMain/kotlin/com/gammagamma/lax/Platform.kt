package com.gammagamma.lax

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
