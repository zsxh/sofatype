package com.sofatype.pinyin.tools

import java.io.File
import java.security.MessageDigest

internal fun verifyHotSourceDir(dir: File) {
    val lockFile = File(dir, "sources.sha256")
    require(lockFile.isFile && lockFile.length() > 0) {
        "热词源 sha256 锁定文件缺失：${lockFile.absolutePath}"
    }

    val locked = mutableMapOf<String, String>()
    for (raw in lockFile.readLines()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val parts = line.split(Regex("\\s+"), limit = 2)
        require(parts.size == 2 && parts[0].length == 64) { "锁定行非法：$line" }
        locked[parts[1]] = parts[0]
    }
    require(locked.isNotEmpty()) { "锁定文件为空：${lockFile.absolutePath}" }

    for ((name, expected) in locked) {
        val file = File(dir, name)
        check(!name.contains('/') && !name.contains('\\') && !name.contains("..")) { "锁定文件名非法：$name" }
        if (!file.isFile || file.length() == 0L) {
            throw IllegalStateException(
                "热词源文件缺失：${file.absolutePath}\n" +
                    "锁定 sha256：$expected（sources.sha256）",
            )
        }
        val actual = sha256(file)
        if (actual != expected) {
            throw IllegalStateException(
                "热词源文件 sha256 校验不一致（文件可能被改动）：${file.absolutePath}\n" +
                    "期望 sha256：$expected\n" +
                    "实际 sha256：$actual\n" +
                    "文件大小：${file.length()} 字节",
            )
        }
        println("[verifyHotDict] 校验通过：$name（${file.length()} 字节）")
    }
    println("[verifyHotDict] 热词源校验全部通过：${locked.size} 个源文件")
}

fun main(args: Array<String>) {
    require(args.size == 1) { "用法：verifyHotDict <热词源目录>" }
    verifyHotSourceDir(File(args[0]))
}

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
