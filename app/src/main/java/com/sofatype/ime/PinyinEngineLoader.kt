package com.sofatype.ime

import com.sofatype.pinyin.PinyinEngine
import java.io.InputStream

class PinyinEngineLoader(
    private val openAsset: (String) -> InputStream,
    private val fullAssetPath: String,
    private val highFreqAssetPath: String,
    private val threadFactory: (Runnable) -> Thread = { Thread(it, PRELOAD_THREAD_NAME) },
    private val onEngineReady: (scope: String, entryCount: Int) -> Unit = { _, _ -> },
    private val onLoadFailure: (scope: String, error: Throwable) -> Unit = { _, _ -> },
    private val alwaysLog: (String) -> Unit = {},
) {

    @Volatile
    private var activeEngine: PinyinEngine? = null

    @Volatile
    private var fullReady: Boolean = false

    fun engineForQuery(): PinyinEngine? = activeEngine

    fun startPreload() {
        threadFactory { preloadSequence() }.start()
    }

    internal fun preloadSequence() {
        loadAndActivate(highFreqAssetPath, SCOPE_HIGH_FREQ)
        loadAndActivate(fullAssetPath, SCOPE_FULL)
        if (engineForQuery() == null) {
            alwaysLog(TERMINAL_FAILURE_MESSAGE)
        }
    }

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

        const val SCOPE_HIGH_FREQ = "highfreq"

        const val SCOPE_FULL = "full"

        const val PRELOAD_THREAD_NAME = "PinyinEnginePreload"

        internal const val TERMINAL_FAILURE_MESSAGE =
            "pinyin dict preload settled without engine (all segments failed)"

        internal fun failureMessage(scope: String, error: Throwable): String {
            val reason = error.message?.takeIf { it.isNotBlank() }
            return "pinyin dict load failed scope=$scope reason=${error.javaClass.simpleName}" +
                (reason?.let { ": $it" } ?: "")
        }
    }
}
