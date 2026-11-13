package com.gammagamma.lax.slack.desktop

import com.gammagamma.lax.slack.CredentialResult
import com.gammagamma.lax.slack.CredentialSource
import com.gammagamma.lax.slack.Credentials
import com.gammagamma.lax.slack.WorkspaceCred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.sql.DriverManager

/**
 * Reads the Slack desktop session the same way the Zig Lax client does:
 * Local Storage LevelDB for `xoxc-` tokens, Cookies sqlite for the `d` cookie.
 */
class DesktopCredentialSource : CredentialSource {
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadSavedSession(): CredentialResult {
        val text = try {
            Keyring.loadSession()
        } catch (_: Exception) {
            null
        } ?: return CredentialResult.Err("No saved session")
        return try {
            CredentialResult.Ok(Credentials.fromJson(text))
        } catch (e: Exception) {
            CredentialResult.Err(e.message ?: "Bad saved session")
        }
    }

    override fun saveSession(credentials: Credentials) {
        Keyring.storeSession(Credentials.toJson(credentials))
    }

    override fun clearSession() {
        Keyring.clearSession()
    }

    override fun extractFromDesktop(): CredentialResult = try {
        val configDir = findConfigDir()
            ?: return CredentialResult.Err(
                "Slack desktop profile not found (tried ~/.config/Slack and Flatpak ~/.var/app/com.slack.Slack)",
            )
        snapshot(configDir).use { snap ->
            val workspaces = readWorkspaces(snap.dir)
            val cookie = try {
                readCookie(snap.dir)
            } catch (e: Exception) {
                val where = configDir.absolutePath
                return CredentialResult.Err(
                    "Found ${workspaces.size} workspace(s) in $where but could not unlock the " +
                        "session cookie (${e.message}). Open the Slack desktop app while signed in " +
                        "so it recreates its keyring entry, or paste a token and cookie below.",
                )
            }
            CredentialResult.Ok(Credentials(cookie = cookie, workspaces = workspaces))
        }
    } catch (e: Exception) {
        CredentialResult.Err(e.message ?: "Desktop extract failed")
    }

    private fun candidateDirs(): List<File> {
        val home = System.getProperty("user.home")
        val list = ArrayList<File>()
        // Flatpak first: that's the usual Linux install now
        list += File(home, ".var/app/com.slack.Slack/config/Slack")
        System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let {
            list += File(it, "Slack")
        }
        list += File(home, ".config/Slack")
        list += File(home, "snap/slack/current/.config/Slack")
        return list
    }

    private fun findConfigDir(): File? = candidateDirs().firstOrNull { dir ->
        dir.isDirectory && File(dir, "Local Storage/leveldb").isDirectory
    }

    private class Snapshot(val dir: File) : AutoCloseable {
        override fun close() {
            dir.deleteRecursively()
        }
    }

    private fun snapshot(configDir: File): Snapshot {
        val base = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("java.io.tmpdir")
        val dest = Files.createTempDirectory(File(base).toPath(), "lax-slack-").toFile()
        dest.setReadable(true, true)
        dest.setWritable(true, true)
        dest.setExecutable(true, true)
        val levelSrc = File(configDir, "Local Storage/leveldb")
        if (!levelSrc.isDirectory) {
            dest.deleteRecursively()
            error("Not signed in")
        }
        val levelDst = File(dest, "leveldb").also { it.mkdirs() }
        levelSrc.listFiles()?.forEach { f ->
            if (f.isFile && f.name != "LOCK") {
                f.copyTo(File(levelDst, f.name), overwrite = true)
            }
        }
        for (name in listOf("Network/Cookies", "Cookies")) {
            val src = File(configDir, name)
            if (src.isFile) {
                src.copyTo(File(dest, "Cookies"), overwrite = true)
                for (side in listOf("-wal", "-shm", "-journal")) {
                    val s = File(configDir, name + side)
                    if (s.isFile) s.copyTo(File(dest, "Cookies$side"), overwrite = true)
                }
                break
            }
        }
        return Snapshot(dest)
    }

    private fun readWorkspaces(snap: File): List<WorkspaceCred> {
        val scanner = LevelDbScanner("localConfig_v2")
        val level = File(snap, "leveldb")
        level.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val name = f.name
            val bytes = f.readBytes()
            when {
                name.endsWith(".ldb") || name.endsWith(".sst") ->
                    runCatching { scanner.scanTable(bytes) }
                name.endsWith(".log") ->
                    runCatching { scanner.scanLog(bytes) }
            }
        }
        val entry = scanner.take() ?: error("Not signed in")
        val jsonText = decodeLocalStorageValue(entry.second)
        return parseLocalConfig(jsonText)
    }

    internal fun parseLocalConfig(text: String): List<WorkspaceCred> {
        val root = json.parseToJsonElement(text).jsonObject
        val teams = root["teams"]?.jsonObject ?: error("format changed")
        val list = ArrayList<WorkspaceCred>()
        for ((id, value) in teams) {
            val t = value as? JsonObject ?: continue
            val token = t["token"]?.jsonPrimitive?.contentOrNull ?: continue
            if (!token.startsWith("xoxc-")) continue
            list += WorkspaceCred(
                teamId = id,
                name = t["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                url = t["url"]?.jsonPrimitive?.contentOrNull.orEmpty().trimEnd('/'),
                token = token,
            )
        }
        if (list.isEmpty()) error("Not signed in")
        return list
    }

    private fun decodeLocalStorageValue(raw: ByteArray): String {
        require(raw.isNotEmpty()) { "format changed" }
        return when (raw[0].toInt() and 0xff) {
            1 -> latin1ToUtf8(raw.copyOfRange(1, raw.size))
            0 -> {
                require(raw.size % 2 == 1) { "format changed" } // leading type byte + even UTF-16
                val units = CharArray((raw.size - 1) / 2)
                var i = 1
                var u = 0
                while (i + 1 < raw.size) {
                    units[u++] = ((raw[i].toInt() and 0xff) or ((raw[i + 1].toInt() and 0xff) shl 8)).toChar()
                    i += 2
                }
                String(units)
            }
            else -> error("format changed")
        }
    }

    private fun latin1ToUtf8(bytes: ByteArray): String =
        String(bytes, Charset.forName("ISO-8859-1"))

    private fun readCookie(snap: File): String {
        val cookies = File(snap, "Cookies")
        require(cookies.isFile) { "Not signed in" }
        Class.forName("org.sqlite.JDBC")
        val passwords = Keyring.cookiePasswords()
        if (passwords.isEmpty()) error("keyring not found")

        val url = "jdbc:sqlite:${cookies.absolutePath}"
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT host_key, name, encrypted_value, value FROM cookies " +
                        "WHERE name = 'd' AND host_key LIKE '%slack.com'",
                ).use { rs ->
                    var sawEncrypted = false
                    while (rs.next()) {
                        val host = rs.getString(1) ?: continue
                        val value = rs.getString(4)
                        if (value != null && value.startsWith("xoxd-")) return value
                        val enc = rs.getBytes(3)
                        if (enc == null || enc.isEmpty()) continue
                        sawEncrypted = true
                        for (password in passwords) {
                            val plain = CookieDecrypt.decryptWithPasswordBytes(enc, host, password)
                            if (plain != null) return plain
                        }
                    }
                    if (sawEncrypted) error("keyring not found")
                }
            }
        }
        error("Not signed in")
    }
}
