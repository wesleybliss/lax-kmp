package com.gammagamma.lax.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

enum class Appearance { System, Light, Dark }

@Serializable
data class Settings(
    val appearance: Appearance = Appearance.System,
    val theme: String = "",
    val uiFont: String = "",
    val chatFont: String = "",
    val interfaceSize: Int = 100,
    val chatSize: Int = 15,
    val sidebarRow: Int = ROW_COMFORTABLE,
) {
    fun clamped(): Settings = copy(
        interfaceSize = interfaceSize.coerceIn(INTERFACE_MIN, INTERFACE_MAX),
        chatSize = chatSize.coerceIn(CHAT_MIN, CHAT_MAX),
        sidebarRow = sidebarRow.coerceIn(ROW_MIN, ROW_MAX),
    )

    companion object {
        const val INTERFACE_MIN = 85
        const val INTERFACE_MAX = 125
        const val CHAT_MIN = 12
        const val CHAT_MAX = 22
        const val ROW_MIN = 28
        const val ROW_MAX = 56
        const val ROW_COMPACT = 32
        const val ROW_COMFORTABLE = 40
        const val ROW_COZY = 48
    }
}

@Serializable
data class LayoutState(
    val width: Int = 1280,
    val height: Int = 800,
    val sidebarWidth: Float = 272f,
)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

class SettingsStore(private val paths: LaxPaths) {
    fun load(): Settings {
        val file = paths.configFile("settings.json")
        if (!file.exists()) return Settings()
        return runCatching {
            json.decodeFromString<Settings>(file.readText()).clamped()
        }.getOrDefault(Settings())
    }

    fun save(settings: Settings) {
        paths.ensureConfigDir()
        paths.configFile("settings.json").writeText(json.encodeToString(Settings.serializer(), settings.clamped()))
    }

    fun loadLayout(): LayoutState {
        val file = paths.configFile("layout.json")
        if (!file.exists()) return LayoutState()
        return runCatching {
            json.decodeFromString<LayoutState>(file.readText())
        }.getOrDefault(LayoutState())
    }

    fun saveLayout(layout: LayoutState) {
        paths.ensureConfigDir()
        paths.configFile("layout.json").writeText(json.encodeToString(LayoutState.serializer(), layout))
    }
}

interface LaxFile {
    fun exists(): Boolean
    fun readText(): String
    fun writeText(text: String)
    fun readBytes(): ByteArray
}

interface LaxPaths {
    fun ensureConfigDir()
    fun ensureCacheDir()
    fun configFile(name: String): LaxFile
    fun configDir(name: String): String
    fun cacheDir(name: String): String
    fun runtimeTempDir(): String
}

expect fun createLaxPaths(): LaxPaths

fun Float.coerceSidebar(): Float = max(200f, min(520f, this))
