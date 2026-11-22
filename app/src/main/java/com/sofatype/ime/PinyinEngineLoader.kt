package com.sofatype.ime

import com.sofatype.pinyin.DictData
import com.sofatype.pinyin.DictFormat
import com.sofatype.pinyin.DictPackMerge
import com.sofatype.pinyin.PinyinEngine
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class PinyinEngineLoader(
    private val openAsset: (String) -> InputStream,
    private val fullAssetPath: String,
    private val highFreqAssetPath: String,
    private val packDataProvider: (() -> List<DictData>)? = null,
    private val threadFactory: (Runnable) -> Thread = { Thread(it, PRELOAD_THREAD_NAME) },
    private val onEngineReady: (scope: String, entryCount: Int) -> Unit = { _, _ -> },
    private val onLoadFailure: (scope: String, error: Throwable) -> Unit = { _, _ -> },
    private val alwaysLog: (String) -> Unit = {},
) {

    private val loadExecutor: ThreadPoolExecutor = ThreadPoolExecutor(
        1,
        1,
        EXECUTOR_IDLE_KEEP_ALIVE_MS,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        threadFactory,
    ).apply { allowCoreThreadTimeOut(true) }

    @Volatile
    private var activeEngine: PinyinEngine? = null

    @Volatile
    private var fullReady: Boolean = false

    @Volatile
    private var fullData: DictData? = null

    fun engineForQuery(): PinyinEngine? = activeEngine

    fun startPreload() {
        loadExecutor.execute { preloadSequence() }
    }

    fun rebuild() {
        loadExecutor.execute {
            val base = fullData
            if (base == null) {
                preloadSequence()
            } else {
                activateFullWithPacks(base)
            }
        }
    }

    internal fun preloadSequence() {
        loadAndActivate(highFreqAssetPath, SCOPE_HIGH_FREQ)
        loadAndActivate(fullAssetPath, SCOPE_FULL)
        if (engineForQuery() == null) {
            alwaysLog(TERMINAL_FAILURE_MESSAGE)
        }
    }

    internal fun loadAndActivate(assetPath: String, scope: String) {
        val data = try {
            openAsset(assetPath).use { DictFormat.read(it) }
        } catch (t: Throwable) {
            onLoadFailure(scope, t)
            alwaysLog(failureMessage(scope, t))
            null
        } ?: return
        if (scope == SCOPE_FULL) {
            fullData = data
            fullReady = true
            activateFullWithPacks(data)
        } else if (fullReady) {
            return
        } else {
            val engine = PinyinEngine.fromData(data)
            activeEngine = engine
            onEngineReady(scope, engine.entryCount)
        }
    }

    private fun activateFullWithPacks(base: DictData) {
        val packs = loadPacks()
        val engine = if (packs.isEmpty()) {
            PinyinEngine.fromData(base)
        } else {
            PinyinEngine.fromData(
                DictPackMerge.mergeBaseWithPacks(base, packs, DictPackMerge.DEFAULT_PACK_FREQ_BOOST),
            )
        }
        activeEngine = engine
        onEngineReady(SCOPE_FULL, engine.entryCount)
    }

    private fun loadPacks(): List<DictData> {
        val provider = packDataProvider ?: return emptyList()
        return try {
            provider()
        } catch (t: Throwable) {
            alwaysLog(failureMessage(SCOPE_PACKS, t))
            emptyList()
        }
    }

    companion object {

        const val SCOPE_HIGH_FREQ = "highfreq"

        const val SCOPE_FULL = "full"

        const val SCOPE_PACKS = "packs"

        const val PRELOAD_THREAD_NAME = "PinyinEnginePreload"

        private const val EXECUTOR_IDLE_KEEP_ALIVE_MS = 30_000L

        internal const val TERMINAL_FAILURE_MESSAGE =
            "pinyin dict preload settled without engine (all segments failed)"

        internal fun failureMessage(scope: String, error: Throwable): String {
            val reason = error.message?.takeIf { it.isNotBlank() }
            return "pinyin dict load failed scope=$scope reason=${error.javaClass.simpleName}" +
                (reason?.let { ": $it" } ?: "")
        }
    }
}
