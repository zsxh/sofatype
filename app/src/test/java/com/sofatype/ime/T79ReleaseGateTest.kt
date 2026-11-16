// T79 · release 构建功能门控放开守卫单测（docs/issues/open/T79.md · 决策 65）：
// ① 远程服务启动入口（RemoteInputServerManager.start()）无构建类型门控——release 与 debug
//    均可启动；debug 专属走查通道门控保持不动（debugConnectedIdleExpire() /
//    debugNetworkChange() 的 BuildConfig.DEBUG 兜底 + debug 源集 RemoteServerDebugReceiver /
//    UpdateMockReceiver 兜底）。
// ② 启动失败 Toast 接线（enterRemoteMode() Failed 分支）+ strings 资源在位；文案为通用
//    常量（无动态拼接、不引用失败原因——reason 可能含配对地址与 token，隐私红线）。
// ③ 门控纯函数语义（TestTypingGate debug / release 均显示）锁定在 T63TestTypingPageTest。
// RemoteInputServerManager 为 Android 交互层（Context / 网络回调），纯 JVM 无法实例化，
// 接线以源码扫描锁定——先例：T54DefectFixTest / T63TestTypingPageTest / T71FileLogPrivacyGuardTest。
// 纯 JVM，不依赖 Android UI 与模拟器（仓库口径）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T79ReleaseGateTest {

    /** 路径口径 = app/ 模块目录（T54DefectFixTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    private val manager: String get() = mainSource("remote/RemoteInputServerManager.kt")
    private val service: String get() = mainSource("SofatypeImeService.kt")
    private val strings: String get() = File("src/main/res/values/strings.xml").readText()

    // ── ① 启动入口门控放开 + debug 走查通道保持 ──

    @Test
    fun `远程启动入口无构建类型门控`() {
        val startIdx = manager.indexOf("fun start(context: Context)")
        val internalIdx = manager.indexOf("private fun startInternal(context: Context)")
        assertTrue("start() 入口声明须存在", startIdx >= 0 && internalIdx > startIdx)
        val startBody = manager.substring(startIdx, internalIdx)
        assertFalse(
            "start() 入口不得再含 BuildConfig.DEBUG 门控（T79 · 决策 65：release 放开）",
            startBody.contains("BuildConfig.DEBUG"),
        )
        assertFalse(
            "start() 入口不得残留「仅 debug 构建可用」门控文案",
            startBody.contains("仅 debug 构建可用"),
        )
    }

    @Test
    fun `debug专属走查通道门控保持不动`() {
        // Manager 内 BuildConfig.DEBUG 兜底恰好两处：debugConnectedIdleExpire / debugNetworkChange
        //（start() 门控移除后不新增、不减少）
        assertEquals(
            "Manager 内 BuildConfig.DEBUG 兜底须恰为 2 处（两个 debug 走查通道）",
            2,
            Regex(Regex.escape("if (!BuildConfig.DEBUG)")).findAll(manager).count(),
        )
        assertTrue(
            "debugConnectedIdleExpire 门控文案须保留（决策 21 口径不动）",
            manager.contains("已连接无活动模拟入口仅 debug 构建可用"),
        )
        assertTrue(
            "debugNetworkChange 门控文案须保留（决策 21 口径不动）",
            manager.contains("网络变化模拟入口仅 debug 构建可用"),
        )
        // debug 源集两接收器兜底在位（release 合并不含该源集，防误删兜底）
        val debugReceiver = File("src/debug/java/com/sofatype/ime/remote/RemoteServerDebugReceiver.kt").readText()
        val mockReceiver = File("src/debug/java/com/sofatype/ime/update/UpdateMockReceiver.kt").readText()
        assertTrue(
            "RemoteServerDebugReceiver 兜底须保留",
            debugReceiver.contains("if (!BuildConfig.DEBUG) return"),
        )
        assertTrue(
            "UpdateMockReceiver 兜底须保留",
            mockReceiver.contains("if (!BuildConfig.DEBUG) return"),
        )
    }

    // ── ② 启动失败 Toast 反馈 ──

    @Test
    fun `启动失败Toast接线与通用文案落源码`() {
        val enterIdx = service.indexOf("private fun enterRemoteMode()")
        val exitIdx = service.indexOf("private fun exitRemoteMode()")
        assertTrue("enterRemoteMode 声明须存在", enterIdx >= 0 && exitIdx > enterIdx)
        val enterBody = service.substring(enterIdx, exitIdx)
        assertTrue(
            "Failed 分支须弹系统 Toast（T79 · 决策 65：启动失败用户可见反馈，不再静默）",
            enterBody.contains("Toast.makeText(this, R.string.remote_start_failed_hint, Toast.LENGTH_SHORT).show()"),
        )
        // 隐私红线：Toast 文案走通用常量资源，不得引用失败原因（reason 可能含配对地址与 token）
        val toastIdx = enterBody.indexOf("Toast.makeText")
        assertTrue("Toast 接线须存在", toastIdx >= 0)
        assertFalse(
            "Toast 文案不得引用失败原因（reason 可能含配对地址与 token）",
            enterBody.substring(toastIdx).contains("reason"),
        )
        val resourceLine = strings.lineSequence().firstOrNull { it.contains("remote_start_failed_hint") }
        assertTrue("strings 资源 remote_start_failed_hint 须存在", resourceLine != null)
        assertFalse(
            "Toast 文案须为常量（无格式化占位符，零动态内容）",
            resourceLine!!.contains("%1\$s") || resourceLine.contains("%1\$d"),
        )
    }
}
