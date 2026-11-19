package com.sofatype.ime.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T71FileLogPrivacyGuardTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

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

    private val bannedIdentifiers = listOf(
        "composer",
        "candidate",
        "remoteText",
        "deviceName",
        "test_input",
        "commitText",
        "editText",
    )

    private val clientCodeBannedIdentifiers = listOf(
        "deviceKey",
        "devicekey",
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

        assertTrue(
            "trace 门控须为「BuildConfig.DEBUG || DebugLogStore.isEnabled」",
            traceBody.contains("BuildConfig.DEBUG || DebugLogStore.isEnabled"),
        )

        for (banned in bannedIdentifiers) {
            assertFalse(
                "trace 体不得引用输入内容面标识 $banned（红线）",
                traceBody.contains(banned, ignoreCase = true),
            )
        }
    }

    @Test
    fun `全部 Timber 调用带显式 TAG`() {

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

        val ime = mainSource("SofatypeImeService.kt")
        assertTrue(ime.contains("Timber.tag(TRACE_TAG).d(message)"))

        assertTrue(ime.contains("trace(\"pinyin engine ready scope="))
        assertTrue(ime.contains("trace(\"pinyin engine load failed scope="))
        assertTrue(ime.contains("\"远程上屏写入失败：\${t.javaClass.simpleName}\""))

        val manager = mainSource("remote/RemoteInputServerManager.kt")
        assertTrue(manager.contains("Timber.tag(TAG).i(\"远程输入服务已启动："))
        assertTrue(manager.contains("Timber.tag(TAG).i(\"远程输入服务已停止，监听已关闭，配对 token 已作废\")"))
        assertTrue(manager.contains("logEvent = { Timber.tag(TAG).i(it) }"))

        val testTyping = mainSource("TestTypingActivity.kt")
        assertTrue(testTyping.contains("Timber.tag(FOCUS_LOG_TAG).i(\"pre-IME BACK：返回设置页\")"))
    }

    @Test
    fun `新增日志点接线存在`() {

        val controller = mainSource("debug/DebugLogController.kt")
        assertTrue(controller.contains("\"调试日志已开启（档位"))
        assertTrue(controller.contains("\"调试日志已关闭\""))
        assertTrue(controller.contains("\"日志文件已清空\""))

        val writer = mainSource("debug/LogFileWriter.kt")
        assertTrue(writer.contains("\"文件超限轮转触发：丢弃最早 \${discardKb} KB，保留最新\""))
        assertTrue(writer.contains("\"日志文件写入失败，降级停写："))

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

        assertTrue(controller.contains("File(context.filesDir, \"logs\")"))

        val app = mainSource("SofatypeApplication.kt")
        assertTrue(app.contains("Timber.plant(Timber.DebugTree())"))
        assertTrue(app.contains("DebugLogController.init(this)"))
    }
}
