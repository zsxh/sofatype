// T71 · 决策 60 · 文件日志隐私红线守卫（源码扫描口径，ManualCheckLogTest 先例）：
// ① 文件日志链（debug 包全部文件）零输入内容面引用（组合串/候选词/远程文本/deviceName/输入框）；
// ② ImeKeyTrace 门控放宽为「debug 构建 或 开关开启」且 trace 体不触碰输入面；
// ③ 全部 Timber 调用带显式 TAG（logcat 通道 TAG 与迁移前一致，历轮取证链不变）；
// ④ 现有日志点文案迁移后原样保留（机械迁移锁定）；
// ⑤ 新增日志点接线存在（开关切换/轮转触发/清空动作/写失败降级/IME onCreate·onDestroy）；
// ⑥ 未捕获异常栈写文件后交回系统默认 handler（崩溃行为不变）；
// ⑦ token 仅出现在远程服务启动/重建行配对地址（T27 拍板不打码），文件日志链零 token 引用；
// ⑧ T72 · 决策 61 扩展：文件日志链零 deviceKey / 序号（连接代号）标识引用。
package com.sofatype.ime.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T71FileLogPrivacyGuardTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    /** 文件日志链文件全集（决策 60 落盘链路 + 配置 + 查看读取，全部须零输入内容面）。 */
    private val fileLogChainFiles = listOf(
        "debug/LogFileTree.kt",
        "debug/LogFileWriter.kt",
        "debug/LogFileSupport.kt",
        "debug/LogRotation.kt",
        "debug/DebugLogStore.kt",
        "debug/DebugLogController.kt",
        "debug/CrashFileLogger.kt",
        "debug/LogViewerContent.kt",
        "debug/LogUiText.kt",
        "debug/DebugLogOptions.kt",
    )

    /** 输入内容面/隐私面标识（红线：文件日志零输入内容/组合串/候选词/远程文本/deviceName）。 */
    private val bannedIdentifiers = listOf(
        "composer",
        "candidate",
        "remoteText",
        "deviceName",
        "test_input",
        "commitText",
        "editText",
    )

    /**
     * T72 · 决策 61 扩展标识（红线：文件日志零 deviceKey / 序号）：
     * deviceKey = 保号键、clientCode = 连接代号（含序号），两者仅 TV 内存驻留、不入日志不持久化。
     */
    private val clientCodeBannedIdentifiers = listOf(
        "deviceKey",
        "devicekey", // 显式小写变体（防止键名字符串拼接绕过大小写变体）
        "clientCode",
        "clientcode",
    )

    @Test
    fun `文件日志链零输入内容面引用`() {
        for (name in fileLogChainFiles) {
            val text = mainSource(name)
            for (banned in bannedIdentifiers) {
                assertFalse(
                    "文件日志链 $name 不得引用输入内容面标识 $banned（决策 60 红线）",
                    text.contains(banned, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `文件日志链零 token 引用`() {
        // T27 拍板：token 仅出现在服务启动/重建行配对地址（RemoteInputServerManager），
        // 文件日志链（落盘侧）不得出现 token 取值或引用
        for (name in fileLogChainFiles) {
            val text = mainSource(name)
            assertFalse(
                "文件日志链 $name 不得引用 token（T27 口径）",
                text.contains("token", ignoreCase = true),
            )
        }
    }

    @Test
    fun `文件日志链零 deviceKey 与序号引用`() {
        // T72 · 决策 61：deviceKey 与序号（连接代号）仅 TV 内存驻留，不入日志不持久化
        for (name in fileLogChainFiles) {
            val text = mainSource(name)
            for (banned in clientCodeBannedIdentifiers) {
                assertFalse(
                    "文件日志链 $name 不得引用连接代号标识 $banned（决策 61 红线）",
                    text.contains(banned, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun `ImeKeyTrace 门控放宽为 debug 或开关开启`() {
        val text = mainSource("SofatypeImeService.kt")
        val traceStart = text.indexOf("private fun trace(message: String)")
        assertTrue("trace 定义缺失", traceStart >= 0)
        val traceBody = text.substring(traceStart, text.indexOf("}", traceStart) + 1)
        // 门控放宽（决策 60）：release 开关即记键控轨迹
        assertTrue(
            "trace 门控须为「BuildConfig.DEBUG || DebugLogStore.isEnabled」",
            traceBody.contains("BuildConfig.DEBUG || DebugLogStore.isEnabled"),
        )
        // trace 体零输入内容面引用
        for (banned in bannedIdentifiers) {
            assertFalse(
                "trace 体不得引用输入内容面标识 $banned（红线）",
                traceBody.contains(banned, ignoreCase = true),
            )
        }
    }

    @Test
    fun `全部 Timber 调用带显式 TAG`() {
        // logcat 通道 TAG 与迁移前一致（ImeKeyTrace/RemoteInputServer/UpdateCheck/SettingsFocus/DebugLog）：
        // 一律 Timber.tag(常量).级别(...) 形态，禁止裸 Timber.i(...)（DebugTree 推断 TAG 会漂移）
        val productionFiles = listOf(
            "SofatypeImeService.kt",
            "SettingsActivity.kt",
            "TestTypingActivity.kt",
            "remote/RemoteInputServerManager.kt",
            "debug/DebugLogController.kt",
        )
        val bareCall = Regex("""Timber\.(v|d|i|w|e|wtf)\(""")
        for (name in productionFiles) {
            val text = mainSource(name)
            assertFalse(
                "$name 存在未显式指定 TAG 的 Timber 调用（须 Timber.tag(...).级别）",
                bareCall.containsMatchIn(text),
            )
        }
    }

    @Test
    fun `现有日志点文案迁移后原样保留`() {
        // ImeKeyTrace 结构行（SofatypeImeService）
        val ime = mainSource("SofatypeImeService.kt")
        assertTrue(ime.contains("Timber.tag(TRACE_TAG).d(message)"))
        // T85 · 方向 C+A：词库失败日志点由 lazy 段内 Timber.w 迁移为加载器失败回调 trace（文案变更在此锁定）
        assertTrue(ime.contains("trace(\"pinyin engine ready scope="))
        assertTrue(ime.contains("trace(\"pinyin engine load failed scope="))
        assertTrue(ime.contains("\"远程上屏写入失败：\${t.javaClass.simpleName}\""))
        // 远程服务（TAG=RemoteInputServer 既有口径）
        val manager = mainSource("remote/RemoteInputServerManager.kt")
        assertTrue(manager.contains("Timber.tag(TAG).i(\"远程输入服务已启动："))
        assertTrue(manager.contains("Timber.tag(TAG).i(\"远程输入服务已停止，监听已关闭，配对 token 已作废\")"))
        assertTrue(manager.contains("logEvent = { Timber.tag(TAG).i(it) }"))
        // 测试打字页（TAG=SettingsFocus 既有口径）
        val testTyping = mainSource("TestTypingActivity.kt")
        assertTrue(testTyping.contains("Timber.tag(FOCUS_LOG_TAG).i(\"pre-IME BACK：返回设置页\")"))
    }

    @Test
    fun `新增日志点接线存在`() {
        // 开关切换 / 清空动作（DebugLogController）
        val controller = mainSource("debug/DebugLogController.kt")
        assertTrue(controller.contains("\"调试日志已开启（档位"))
        assertTrue(controller.contains("\"调试日志已关闭\""))
        assertTrue(controller.contains("\"日志文件已清空\""))
        // 轮转触发 / 写失败降级（LogFileWriter，降级直写 android.util.Log 防文件树递归）
        val writer = mainSource("debug/LogFileWriter.kt")
        assertTrue(writer.contains("\"文件超限轮转触发：丢弃最早 \${discardKb} KB，保留最新\""))
        assertTrue(writer.contains("\"日志文件写入失败，降级停写："))
        // IME onCreate/onDestroy（SofatypeImeService）
        val ime = mainSource("SofatypeImeService.kt")
        assertTrue(ime.contains("trace(\"onCreate\")"))
        assertTrue(ime.contains("trace(\"onDestroy\")"))
    }

    @Test
    fun `崩溃栈写文件后交回系统默认handler`() {
        val crash = mainSource("debug/CrashFileLogger.kt")
        assertTrue("崩溃钩子须按开关旁路（开关关闭直通零旁路）", crash.contains("if (DebugLogStore.isEnabled)"))
        assertTrue(
            "写完文件后必须交回系统默认 handler（崩溃行为不变，决策 60）",
            crash.contains("previous?.uncaughtException(thread, throwable)"),
        )
    }

    @Test
    fun `文件树挂载随开关与进程启动接线`() {
        val controller = mainSource("debug/DebugLogController.kt")
        assertTrue("init 须按开关挂载文件树", controller.contains("if (DebugLogStore.isEnabled)"))
        assertTrue("开关开启须挂载文件树", controller.contains("LogFileTree.mount(logsDir)"))
        assertTrue("开关关闭须卸载文件树", controller.contains("LogFileTree.unmount()"))
        // 落盘目录 = 应用私有 filesDir/logs/（决策 60）
        assertTrue(controller.contains("File(context.filesDir, \"logs\")"))
        // 应用入口接线（Application.onCreate 调 init + 常驻 logcat 树）
        val app = mainSource("SofatypeApplication.kt")
        assertTrue(app.contains("Timber.plant(Timber.DebugTree())"))
        assertTrue(app.contains("DebugLogController.init(this)"))
    }
}
