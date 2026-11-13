package com.gammagamma.lax.slack.desktop

/**
 * OS keyring via `secret-tool` (Linux Secret Service).
 *
 * Cookie decryption passwords, in order:
 * 1. Classic Chromium item `application=Slack`
 * 2. Flatpak XDG portal item `app_id=com.slack.Slack`
 * 3. Chromium's historical plaintext fallback `"peanuts"`
 */
internal object Keyring {
    fun cookiePasswords(): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        fun add(bytes: ByteArray?) {
            if (bytes == null || bytes.isEmpty()) return
            if (out.any { it.contentEquals(bytes) }) return
            out += bytes
        }
        add(runSecretToolBytes("lookup", "application", "Slack"))
        add(
            runSecretToolBytes(
                "lookup",
                "xdg:schema", "org.freedesktop.portal.Secret",
                "app_id", "com.slack.Slack",
            ),
        )
        add("peanuts".toByteArray())
        return out
    }

    fun loadSession(): String? {
        val out = runSecretToolBytes("lookup", "application", "lax-slack-clone", "kind", "session")
        return out?.toString(Charsets.UTF_8)?.takeIf { it.isNotEmpty() }
    }

    fun storeSession(json: String) {
        val pb = ProcessBuilder(
            "secret-tool", "store", "--label=Lax Slack session",
            "application", "lax-slack-clone", "kind", "session",
        )
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        val proc = pb.start()
        proc.outputStream.use { it.write(json.toByteArray()) }
        val code = proc.waitFor()
        require(code == 0) { "keyring unavailable" }
    }

    fun clearSession() {
        runSecretToolBytes("clear", "application", "lax-slack-clone", "kind", "session")
    }

    private fun runSecretToolBytes(vararg args: String): ByteArray? = try {
        val pb = ProcessBuilder(listOf("secret-tool") + args)
        pb.redirectError(ProcessBuilder.Redirect.DISCARD)
        val proc = pb.start()
        val out = proc.inputStream.readBytes()
        val code = proc.waitFor()
        if (code != 0 || out.isEmpty()) null else out
    } catch (_: Exception) {
        null
    }
}
