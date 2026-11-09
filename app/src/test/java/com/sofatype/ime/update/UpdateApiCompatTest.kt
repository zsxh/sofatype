// T46 · 更新模块 API 级别守卫单元测试（JUnit 4，源码扫描口径）：
// minSdk 23（决策 13 验收基线）下,HttpURLConnection.getContentLengthLong() 为 API 24+ 方法,
// 基线设备必抛 NoSuchMethodError 且为 Error 不入调用方异常链（真机 Android 6.0.1 100% 崩溃,
// 2026-11-09 T27 Round 1 实证,崩溃栈 t27-tvbox-switch.txt）。修复采用 int 版 getContentLength()
//（API 9+,2GB 内等价）。本测试直读源码锁定回归:该模块不得再引用任何 API>23 的
// HttpURLConnection/URLConnection 长度方法。JVM 桌面端两类方法都存在,行为级测试无法区分,
// 源码扫描是本回归的唯一可靠守卫（源码直读先例:AccessibilityLabelsTest 直读 assets 源文件）。
package com.sofatype.ime.update

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateApiCompatTest {

    /** API 24+ 才有的 URL 连接长度方法（minSdk 23 基线禁用清单）。 */
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
