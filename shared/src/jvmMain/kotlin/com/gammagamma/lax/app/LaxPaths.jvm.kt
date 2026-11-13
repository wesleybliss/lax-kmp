package com.gammagamma.lax.app

import java.io.File
import java.nio.file.Files

private class JvmLaxFile(private val file: File) : LaxFile {
    override fun exists(): Boolean = file.isFile
    override fun readText(): String = file.readText()
    override fun writeText(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
    override fun readBytes(): ByteArray = file.readBytes()
}

private class JvmLaxPaths : LaxPaths {
    private val home = System.getProperty("user.home")
    private val xdgConfig = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
        ?: "$home/.config"
    private val xdgCache = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
        ?: "$home/.cache"
    private val xdgRuntime = System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }
        ?: System.getProperty("java.io.tmpdir")

    private val configRoot = File(xdgConfig, "lax")
    private val cacheRoot = File(xdgCache, "lax")

    override fun ensureConfigDir() {
        configRoot.mkdirs()
        File(configRoot, "themes").mkdirs()
        File(configRoot, "custom/themes").mkdirs()
        File(configRoot, "settings").mkdirs()
    }

    override fun ensureCacheDir() {
        cacheRoot.mkdirs()
        File(cacheRoot, "images").mkdirs()
        File(cacheRoot, "pasted").mkdirs()
    }

    override fun configFile(name: String): LaxFile = JvmLaxFile(File(configRoot, name))
    override fun configDir(name: String): String = File(configRoot, name).also { it.mkdirs() }.absolutePath
    override fun cacheDir(name: String): String = File(cacheRoot, name).also { it.mkdirs() }.absolutePath
    override fun runtimeTempDir(): String =
        Files.createTempDirectory(File(xdgRuntime).toPath(), "lax-").toAbsolutePath().toString()
}

actual fun createLaxPaths(): LaxPaths = JvmLaxPaths()
