package com.sofatype.pinyin.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class HotDictSourceLockTest {

    private val hotDictDir = File("dictsrc/hotwords")

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `锁定文件存在且逐源 sha256 与入库文件一致`() {
        val lock = File(hotDictDir, "sources.sha256")
        assertTrue("热词源锁定文件缺失：${lock.absolutePath}", lock.isFile && lock.length() > 0)

        val locked = linkedMapOf<String, String>()
        for (raw in lock.readLines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"), limit = 2)
            assertTrue("锁定行非法：$line", parts.size == 2 && parts[0].length == 64)
            locked[parts[1]] = parts[0]
        }
        assertTrue("锁定文件应覆盖全部入库热词源", locked.size >= 3)
        assertTrue("THUOCL 开源源应在锁内", "thuocl-open.txt" in locked)

        for ((name, expected) in locked) {
            val file = File(hotDictDir, name)
            assertTrue("热词源文件缺失：$name", file.isFile && file.length() > 0)
            assertEquals("热词源 sha256 与锁定值不一致：$name", expected, sha256(file))
        }
    }

    @Test
    fun `目录内源文件全部在锁内_无未登记源`() {
        val lock = File(hotDictDir, "sources.sha256")
        val lockedNames = lock.readLines()
            .map { it.trim().split(Regex("\\s+"), limit = 2).lastOrNull() ?: "" }
            .filter { it.isNotEmpty() }
            .toSet()
        val actual = hotDictDir.listFiles { f -> f.isFile && f.name != "sources.sha256" }
            ?.map { it.name }?.toSet() ?: emptySet()
        assertEquals("目录内文件应与锁定清单一致", lockedNames, actual)
    }
}
