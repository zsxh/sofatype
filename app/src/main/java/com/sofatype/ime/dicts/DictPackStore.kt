package com.sofatype.ime.dicts

import com.sofatype.pinyin.DictData
import com.sofatype.pinyin.DictFormat
import java.io.File
import java.security.MessageDigest

class DictPackStore(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun packFile(name: String): File = File(dir, "$name$PACK_SUFFIX")

    fun downloadTarget(name: String): File = File(dir, "$name$DOWNLOAD_SUFFIX")

    fun installedEntry(name: String): DictPackEntry? {
        val meta = File(dir, "$name$META_SUFFIX")
        if (!meta.isFile) return null
        return runCatching {
            DictPackManifestParser.parse(meta.readText(Charsets.UTF_8)).singleOrNull()
        }.getOrNull()
    }

    fun installedEntries(): List<DictPackEntry> =
        dir.listFiles { file -> file.isFile && file.name.endsWith(META_SUFFIX) }
            ?.mapNotNull { file ->
                runCatching {
                    DictPackManifestParser.parse(file.readText(Charsets.UTF_8)).singleOrNull()
                }.getOrNull()
            }
            .orEmpty()
            .sortedBy { it.name }

    fun isVerified(name: String): Boolean {
        val entry = installedEntry(name) ?: return false
        val file = packFile(name)
        if (!file.isFile || file.length() != entry.bytes) return false
        return sha256Hex(file) == entry.sha256Hex
    }

    fun isEnabled(name: String): Boolean = readEnabledSet().contains(name)

    fun setEnabled(name: String, enabled: Boolean) {
        val set = readEnabledSet().toMutableSet()
        if (enabled) set.add(name) else set.remove(name)
        File(dir, ENABLED_FILE_NAME).writeText(set.sorted().joinToString("\n"), Charsets.UTF_8)
    }

    fun install(entry: DictPackEntry, downloaded: File): Boolean {
        try {
            val verified = try {
                downloaded.isFile && downloaded.length() == entry.bytes && sha256Hex(downloaded) == entry.sha256Hex
            } catch (t: Throwable) {
                false
            }
            if (!verified) return false
            val target = packFile(entry.name)
            val replaced = target.isFile
            if (replaced) target.delete()
            if (!downloaded.renameTo(target)) return false
            File(dir, entry.name + META_SUFFIX).writeText(
                DictPackManifestParser.writeEntryJson(entry),
                Charsets.UTF_8,
            )
            return true
        } finally {
            if (downloaded.exists()) downloaded.delete()
        }
    }

    fun loadDictData(name: String): DictData? {
        val entry = installedEntry(name) ?: return null
        val file = packFile(name)
        if (!file.isFile || file.length() != entry.bytes) return null
        return file.inputStream().use { DictFormat.read(it) }
    }

    private fun readEnabledSet(): Set<String> {
        val file = File(dir, ENABLED_FILE_NAME)
        if (!file.isFile) return emptySet()
        return file.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    companion object {

        const val PACK_SUFFIX: String = ".bin"

        const val DOWNLOAD_SUFFIX: String = ".download"

        const val META_SUFFIX: String = ".meta"

        const val ENABLED_FILE_NAME: String = "enabled.txt"

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString(separator = "") { "%02x".format(it) }
        }

        private const val BUFFER_BYTES = 8 * 1024
    }
}
