package com.sofatype.pinyin.tools

import java.io.File
import java.security.MessageDigest

/**
 * 仓库内词库源文件校验（T30，PRD 决策 29：源文件入库，构建零网络）。
 *
 * 构建期门禁：源文件存在且 sha256 与锁定值一致才放行转换；
 * 缺失或被改动时报错退出，报错信息含期望 sha256。
 */
internal fun verifySourceFile(path: String) {
    val source = File(path)

    if (!source.exists() || source.length() == 0L) {
        throw IllegalStateException(
            "词库源文件缺失：${source.absolutePath}\n" +
                "期望文件：pinyin/dictsrc/rawdict_utf16_65105_freq.txt（$EXPECTED_SOURCE_BYTES 字节，随仓库入库）\n" +
                "期望 sha256：$EXPECTED_SHA256",
        )
    }

    val actual = sha256(source)
    if (actual != EXPECTED_SHA256) {
        throw IllegalStateException(
            "词库源文件 sha256 校验不一致（文件可能被改动或损坏）：${source.absolutePath}\n" +
                "期望 sha256：$EXPECTED_SHA256\n" +
                "实际 sha256：$actual\n" +
                "文件大小：${source.length()} 字节（期望 $EXPECTED_SOURCE_BYTES 字节）",
        )
    }

    println("[verifyDict] 源文件校验通过：${source.absolutePath}")
    println("[verifyDict] sha256：$actual（${source.length()} 字节）")
}

/** JavaExec 任务入口 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "用法：verifyDict <源文件路径>" }
    verifySourceFile(args[0])
}

/** 流式计算文件 sha256（3.5 MB 量级，一次读入亦可，流式避免大文件内存峰值） */
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

/** 入库基线 sha256（PRD 决策 29 锁定） */
internal const val EXPECTED_SHA256: String =
    "408700f28a56091fa07f3b849a0f134fbfc71e6b2ae9b3f52973a5b076f599ff"

/** 入库基线文件字节数（PRD 决策 29 锁定，与 sha256 一并维护） */
internal const val EXPECTED_SOURCE_BYTES: Long = 3_570_346L
