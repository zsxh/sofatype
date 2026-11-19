package com.sofatype.ime.integration

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.sofatype.ime.ChineseComposer
import com.sofatype.ime.KeyAction
import com.sofatype.ime.KeyboardLayout
import com.sofatype.ime.PinyinEngineLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import android.view.KeyEvent
import java.io.FileNotFoundException
import java.io.InputStream

class PinyinFullChainIntegrationTest {

    private class LoadedScope(val scope: String, val entryCount: Int)

    private fun openDictAsset(path: String): InputStream =
        PinyinFullChainIntegrationTest::class.java.classLoader?.getResourceAsStream(path)
            ?: throw FileNotFoundException("词库产物缺失（classpath $path）。先运行：./gradlew :pinyin:buildDict")

    private fun newLoader(scopes: MutableList<LoadedScope>): PinyinEngineLoader =
        PinyinEngineLoader(
            openAsset = ::openDictAsset,
            fullAssetPath = FULL_DICT_ASSET,
            highFreqAssetPath = HIGH_FREQ_ASSET,
            onEngineReady = { scope, entryCount -> scopes.add(LoadedScope(scope, entryCount)) },
        )

    private class FakeInputConnection : InputConnection {
        val committed = StringBuilder()
        val composing = StringBuilder()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = null
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null
        override fun getSelectedText(flags: Int): CharSequence? = null
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = null
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength) { if (committed.isNotEmpty()) committed.deleteCharAt(committed.length - 1) }
            return true
        }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
            deleteSurroundingText(beforeLength, afterLength)

        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

        override fun getHandler(): android.os.Handler? = null

        override fun closeConnection() = Unit

        override fun commitContent(
            inputContent: android.view.inputmethod.InputContentInfo,
            flags: Int,
            opts: android.os.Bundle?,
        ): Boolean = false

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing.clear()
            composing.append(text ?: "")
            return true
        }
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun finishComposingText(): Boolean {
            committed.append(composing)
            composing.clear()
            return true
        }
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            composing.clear()
            committed.append(text ?: "")
            return true
        }
        override fun commitCompletion(text: android.view.inputmethod.CompletionInfo?): Boolean = true
        override fun commitCorrection(correction: android.view.inputmethod.CorrectionInfo?): Boolean = true
        override fun setSelection(start: Int, end: Int): Boolean = true
        override fun performEditorAction(actionCode: Int): Boolean = true
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun beginBatchEdit(): Boolean = true
        override fun endBatchEdit(): Boolean = true
        override fun sendKeyEvent(event: android.view.KeyEvent?): Boolean = false
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean = true
    }

    private fun pressLetterKey(composer: ChineseComposer, ic: FakeInputConnection, keyCode: Int) {
        val letterId = KeyboardLayout.letterIdForKeyCode(keyCode)
        assertNotNull("键码 $keyCode 应映射字母键", letterId)
        val key = KeyboardLayout.keyById.getValue(letterId!!)
        val action = key.action as KeyAction.Text
        composer.appendLetter(action.value.first())
        ic.setComposingText(composer.composition, 1)
    }

    private fun finishComposition(composer: ChineseComposer, ic: FakeInputConnection) {
        if (composer.composition.isEmpty()) return
        ic.beginBatchEdit()
        ic.setComposingText(composer.committedText(), 1)
        ic.finishComposingText()
        ic.endBatchEdit()
        composer.reset()
    }

    @Test
    fun `真实词库产物预载_高频先行全量替换_规模与既有口径一致`() {
        val scopes = mutableListOf<LoadedScope>()
        val loader = newLoader(scopes)
        loader.preloadSequence()

        assertEquals(
            listOf(PinyinEngineLoader.SCOPE_HIGH_FREQ, PinyinEngineLoader.SCOPE_FULL),
            scopes.map { it.scope },
        )
        assertEquals(HIGH_FREQ_ENTRY_COUNT, scopes[0].entryCount)
        assertEquals(FULL_ENTRY_COUNT, scopes[1].entryCount)
        assertEquals(FULL_ENTRY_COUNT, loader.engineForQuery()?.entryCount)
    }

    @Test
    fun `组合态全链_键码到组合到候选序到选词上屏_真实词库`() {
        val loader = newLoader(mutableListOf())
        loader.preloadSequence()
        val engine = requireNotNull(loader.engineForQuery())

        val ic = FakeInputConnection()
        val composer = ChineseComposer { input -> engine.query(input, CANDIDATE_QUERY_LIMIT).map { it.word } }

        for (keyCode in intArrayOf(KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_O)) {
            pressLetterKey(composer, ic, keyCode)
        }
        assertEquals("nihao", composer.composition)
        assertEquals("nihao", ic.composing.toString())
        assertTrue("组合态应有候选", composer.hasCandidates)
        assertTrue("nihao 应命中「你好」（RealDictTest 口径）", "你好" in composer.candidates)

        assertEquals("你好", composer.firstCandidate())
        composer.reset()
        ic.commitText("你好", 1)
        assertEquals("你好", ic.committed.toString())
        assertEquals("", composer.composition)
        assertTrue("上屏后组合区应清空", ic.composing.isEmpty())
    }

    @Test
    fun `组合态增量查询与回退删除_真实词库`() {
        val loader = newLoader(mutableListOf())
        loader.preloadSequence()
        val engine = requireNotNull(loader.engineForQuery())
        val ic = FakeInputConnection()
        val composer = ChineseComposer { input -> engine.query(input, CANDIDATE_QUERY_LIMIT).map { it.word } }

        pressLetterKey(composer, ic, KeyEvent.KEYCODE_K)
        pressLetterKey(composer, ic, KeyEvent.KEYCODE_E)
        assertEquals("ke", ic.composing.toString())

        pressLetterKey(composer, ic, KeyEvent.KEYCODE_T)
        assertTrue("ket 应增量命中「客厅」", "客厅" in composer.candidates)
        assertEquals("ket", ic.composing.toString())

        pressLetterKey(composer, ic, KeyEvent.KEYCODE_I)
        assertTrue("keti 应增量命中「客厅」", "客厅" in composer.candidates)
        assertEquals("keti", ic.composing.toString())

        assertEquals(true, composer.deleteLastLetter())
        ic.setComposingText(composer.composition, 1)
        assertEquals("ket", composer.composition)
        assertTrue("回退到 ket 仍应命中「客厅」", "客厅" in composer.candidates)

        assertEquals(true, composer.deleteLastLetter())
        assertEquals(true, composer.deleteLastLetter())
        assertEquals(true, composer.deleteLastLetter())
        assertEquals("", composer.composition)
        assertTrue("清空后应无候选", composer.candidates.isEmpty())
    }

    @Test
    fun `完成键终结组合_原始拼音直落宿主_真实词库`() {
        val loader = newLoader(mutableListOf())
        loader.preloadSequence()
        val engine = requireNotNull(loader.engineForQuery())
        val ic = FakeInputConnection()
        val composer = ChineseComposer { input -> engine.query(input, CANDIDATE_QUERY_LIMIT).map { it.word } }

        pressLetterKey(composer, ic, KeyEvent.KEYCODE_N)
        pressLetterKey(composer, ic, KeyEvent.KEYCODE_I)
        assertTrue(composer.hasCandidates)
        finishComposition(composer, ic)

        assertEquals("ni", ic.committed.toString())
        assertEquals("", composer.composition)
    }

    @Test
    fun `分词符参与组合_提交串剔除分词符_真实词库`() {
        val loader = newLoader(mutableListOf())
        loader.preloadSequence()
        val engine = requireNotNull(loader.engineForQuery())
        val ic = FakeInputConnection()
        val composer = ChineseComposer { input -> engine.query(input, CANDIDATE_QUERY_LIMIT).map { it.word } }

        pressLetterKey(composer, ic, KeyEvent.KEYCODE_X)
        pressLetterKey(composer, ic, KeyEvent.KEYCODE_I)
        assertEquals(true, composer.appendSegment())
        ic.setComposingText(composer.composition, 1)
        assertEquals("xi'", composer.composition)
        assertEquals("xi'", ic.composing.toString())
        assertEquals("xi", composer.committedText())

        pressLetterKey(composer, ic, KeyEvent.KEYCODE_A)
        pressLetterKey(composer, ic, KeyEvent.KEYCODE_N)
        assertEquals("xi'an", composer.composition)
        assertEquals("xi'an", ic.composing.toString())
        assertEquals("xian", composer.committedText())
        assertTrue("xian 应命中「先」（jshell 实测 top24 第 2 位）", "先" in composer.candidates)

        finishComposition(composer, ic)
        assertEquals("xian", ic.committed.toString())
    }

    private companion object {
        const val FULL_DICT_ASSET = "dict/pinyin_dict.bin"
        const val HIGH_FREQ_ASSET = "dict/pinyin_dict_highfreq.bin"
        const val FULL_ENTRY_COUNT = 65_105
        const val HIGH_FREQ_ENTRY_COUNT = 4_000
        const val CANDIDATE_QUERY_LIMIT = 24
    }
}
