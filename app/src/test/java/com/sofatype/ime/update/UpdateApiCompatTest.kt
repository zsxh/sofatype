package com.sofatype.ime.update

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateApiCompatTest {

    private val bannedApi24PlusMembers = listOf("contentLengthLong", "getLastModifiedLong", "getHeaderFieldLong")

    @Test
    fun updateSourcesDoNotReferenceApi24PlusContentLengthMethods() {
        val sourceRoot = File("src/main/java/com/sofatype/ime/update")
        val sources = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("update 源码目录不应为空（路径口径 = app/ 模块目录）", sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            for (member in bannedApi24PlusMembers) {
                assertTrue(
                    "${file.name} 引用了 API 24+ 方法 $member（minSdk 23 基线禁用,真机必崩,见 T46）",
                    !text.contains(member),
                )
            }
        }
    }

    @Test
    fun downloadContentLengthUsesIntApiOnHttpURLConnection() {
        val text = File("src/main/java/com/sofatype/ime/update/UpdateHttp.kt").readText()
        assertTrue(
            "UpdateHttp 下载总长应取 int 版 conn.contentLength（API 9+,T46 修复口径）",
            text.contains("conn.contentLength"),
        )
    }
}
