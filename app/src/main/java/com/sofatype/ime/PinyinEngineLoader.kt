package com.sofatype.ime

import com.sofatype.pinyin.PinyinEngine
import java.io.InputStream

/**
 * 拼音引擎分段预加载器（T85 · 方向 C+A；T87 · 失败恒记日志）：
 * - A 进程级预加载：[startPreload] 在专用后台线程执行加载序列，主线程零磁盘 IO（StrictMode 口径）；
 * - C 高频分段：序列先装高频子集（首键即用），再装全量并整体替换（升级不回退）；
 * - 活动引擎只经 [engineForQuery] 暴露（volatile 读）；加载全部失败时引擎为 null，
 *   中文模式退化为无候选（既有可靠性口径：不崩溃、不阻塞宿主）。
 * - T87：单段失败与「序列结束仍无可用引擎」终态各有一条恒记日志（经 [alwaysLog]，
 *   与构建变体 / 日志开关无关）；成功路径不恒记；内容只含技术事实（scope / 异常类名 / message），零输入内容。
 */
class PinyinEngineLoader(
    private val openAsset: (String) -> InputStream,
    private val fullAssetPath: String,
    private val highFreqAssetPath: String,
    private val threadFactory: (Runnable) -> Thread = { Thread(it, PRELOAD_THREAD_NAME) },
    private val onEngineReady: (scope: String, entryCount: Int) -> Unit = { _, _ -> },
    private val onLoadFailure: (scope: String, error: Throwable) -> Unit = { _, _ -> },
    private val alwaysLog: (String) -> Unit = {},
) {

    /** 活动引擎（volatile：后台线程装载完成后整体发布，主线程只读） */
    @Volatile
    private var activeEngine: PinyinEngine? = null

    /** 全量引擎是否已就绪（子集迟到的升级抑制判据） */
    @Volatile
    private var fullReady: Boolean = false

    /** 主线程查询入口：只读活动引擎，不做任何 IO 与阻塞等待。 */
    fun engineForQuery(): PinyinEngine? = activeEngine

    /** 启动预加载序列（进程 onCreate 调用一次；后台线程，失败静默按降级口径） */
    fun startPreload() {
        threadFactory { preloadSequence() }.start()
    }

    /** 预加载序列：先高频子集（首键即用），后全量（后台补齐）；结束时无可用引擎则落一条恒记终态日志（T87） */
    internal fun preloadSequence() {
        loadAndActivate(highFreqAssetPath, SCOPE_HIGH_FREQ)
        loadAndActivate(fullAssetPath, SCOPE_FULL)
        if (engineForQuery() == null) {
            alwaysLog(TERMINAL_FAILURE_MESSAGE)
        }
    }

    /**
     * 装载单段并挂为活动引擎。
     * 挂载规则：全量就绪即置位 [fullReady]；迟到的子集不得回退覆盖已就绪的全量。
     * 失败沿：经 [onLoadFailure]（门控 trace 明细）+ [alwaysLog]（恒记，T87）双通道，内容只含技术事实。
     */
    internal fun loadAndActivate(assetPath: String, scope: String) {
        val engine = try {
            openAsset(assetPath).use { PinyinEngine.load(it) }
        } catch (t: Throwable) {
            onLoadFailure(scope, t)
            alwaysLog(failureMessage(scope, t))
            null
        } ?: return
        if (scope == SCOPE_FULL) {
            fullReady = true
        } else if (fullReady) {
            return
        }
        activeEngine = engine
        onEngineReady(scope, engine.entryCount)
    }

    companion object {
        /** 引擎作用域：高频子集 */
        const val SCOPE_HIGH_FREQ = "highfreq"

        /** 引擎作用域：全量 */
        const val SCOPE_FULL = "full"

        /** 预加载线程名 */
        const val PRELOAD_THREAD_NAME = "PinyinEnginePreload"

        /** 双段失败终态恒记文案（T87；只含技术事实，零输入内容） */
        internal const val TERMINAL_FAILURE_MESSAGE =
            "pinyin dict preload settled without engine (all segments failed)"

        /** 单段失败恒记文案（T87；scope + 异常类名 + message 有则带，零输入内容） */
        internal fun failureMessage(scope: String, error: Throwable): String {
            val reason = error.message?.takeIf { it.isNotBlank() }
            return "pinyin dict load failed scope=$scope reason=${error.javaClass.simpleName}" +
                (reason?.let { ": $it" } ?: "")
        }
    }
}
