package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictHotRealDictTest {

    private fun loadRealData(): DictData =
        DictHotRealDictTest::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
            ?.use { DictFormat.read(it) }
            ?: throw AssertionError("词库产物不存在（classpath /dict/pinyin_dict.bin）。先运行：./gradlew :pinyin:buildDict")

    private fun sampleHot(entries: Int): DictData {
        val syllables = mutableListOf<String>()
        val words = mutableListOf<String>()
        val hotEntries = mutableListOf<DictData.Entry>()
        var serial = 0
        while (hotEntries.size < entries) {
            serial++
            val sylTexts = List(2 + (serial % 3)) { i -> "z" + "aeiou"[(serial + i) % 5] }
            val word = serial.toString() + "热词测"
            for (s in sylTexts) {
                if (s !in syllables) syllables.add(s)
            }
            words.add(word)
            hotEntries.add(
                DictData.Entry(
                    sylTexts.map { syllables.indexOf(it) }.toIntArray(),
                    words.size - 1,
                    100.0 + serial,
                ),
            )
        }
        return DictData(syllables, words, hotEntries)
    }

    @Test
    fun `真实主库加热词合并后_热词命中主库词序不受损`() {
        val real = loadRealData()
        val merged = DictHotMerge.mergeBaseWithHot(real, listOf(sampleHot(entries = 2000)))
        val engine = PinyinEngine.fromData(merged)

        assertEquals("合并后词条数 = 主库 + 热词去重后", real.entryCount + 2000, engine.entryCount)
        assertEquals("合并后主库高频词「这个」仍居 zg 前列", "这个", engine.query("zg", limit = 5).firstOrNull()?.word)
        assertTrue("合并后主库「你好」仍可查", "你好" in engine.query("nihao", limit = 50).map { it.word })
    }

    @Test
    fun `合并引擎查询性能量级佐证_单次查询不超过200毫秒`() {
        val real = loadRealData()
        val engine = PinyinEngine.fromData(
            DictHotMerge.mergeBaseWithHot(real, listOf(sampleHot(entries = 2000))),
        )
        val inputs = listOf(
            "k", "ke", "keti", "keting", "nihao", "xian", "zhongwen", "z", "zg", "nh",
            "zguo", "zhly", "zhli", "srf", "xianyanbao", "zundujiadu", "dazi",
        )

        repeat(30) { engine.query(inputs[it % inputs.size]) }

        var maxMs = 0.0
        var totalMs = 0.0
        var count = 0
        repeat(50) {
            for (input in inputs) {
                val t0 = System.nanoTime()
                engine.query(input)
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                maxMs = maxOf(maxMs, ms)
                totalMs += ms
                count++
            }
        }
        println("热词合并引擎查询 ${count} 次，平均 ${"%.3f".format(totalMs / count)} ms，最大 ${"%.3f".format(maxMs)} ms")
        assertTrue("单次查询最大耗时 ${"%.3f".format(maxMs)} ms 超出 200 ms", maxMs < 200.0)
    }
}
