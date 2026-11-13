package com.gammagamma.lax.slack.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class ParseLocalConfigTest {
    @Test
    fun extractsXoxcTeams() {
        val ws = DesktopCredentialSource().parseLocalConfig(
            """{"teams":{"T1":{"name":"Acme","url":"https://acme.slack.com/","token":"xoxc-1-2-3-abc"},"T2":{"name":"NoTok"}}}""",
        )
        assertEquals(1, ws.size)
        assertEquals("T1", ws[0].teamId)
        assertEquals("Acme", ws[0].name)
    }

    @Test
    fun reportsFormatChanges() {
        assertFails {
            DesktopCredentialSource().parseLocalConfig("[]")
        }
    }
}
