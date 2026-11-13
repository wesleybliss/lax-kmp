package com.gammagamma.lax.slack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MrkdwnTest {
    @Test
    fun boldAndMention() {
        val r = Mrkdwn.render("hello *world* <@U123>", NameResolver { "Ada" })
        assertEquals("hello world @Ada", r.text)
        assertTrue(r.runs.any { it.style == MrkdwnStyle.Bold })
        assertTrue(r.runs.any { it.style == MrkdwnStyle.Mention })
    }

    @Test
    fun linkWithLabel() {
        val r = Mrkdwn.render("<https://example.com|Example>")
        assertEquals("Example", r.text)
        assertEquals("https://example.com", r.runs.single().target)
    }
}
