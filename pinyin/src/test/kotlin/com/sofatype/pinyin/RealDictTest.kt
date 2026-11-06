package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

/**
 * 真实词库产物验证（AOSP 谷歌拼音词库 SPD1 转换产物，决策 28）。
 *
 * 前提：产物已生成 —— `./gradlew :pinyin:buildDict`
 * （产物位于 pinyin/assets/dict/pinyin_dict.bin，经 classpath /dict/pinyin_dict.bin 读取）。
 */
class RealDictTest {

    private fun openProduct(): InputStream =
        RealDictTest::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
            ?: throw AssertionError(
                "词库产物不存在（classpath /dict/pinyin_dict.bin）。先运行：./gradlew :pinyin:buildDict",
            )

    private fun loadEngine(): PinyinEngine = openProduct().use { PinyinEngine.load(it) }

    @Test
    fun `产物加载成功且规模与源词库一致`() {
        val engine = loadEngine()
        // 源词库 65 105 行全部通过校验（2026-11-06 全量核对），词条数应一致
        assertEquals(65_105, engine.entryCount)
        // 音节表：416 个不同无调音节
        assertEquals(416, engine.syllableCount)
    }

    @Test
    fun `抽样整拼查询命中高频词`() {
        val engine = loadEngine()
        val samples = mapOf(
            "keting" to "客厅",
            "nihao" to "你好",
            "beijing" to "北京",
            "shanghai" to "上海",
            "xian" to "先", // 歧义串：整体音节「先」与 xi+an「西安」都应命中
            "xianwang" to null, // 4 音节长词组样例只验证可查询不抛错
        )
        for ((input, expect) in samples) {
            val result = engine.query(input, limit = 100)
            if (expect != null) {
                assertTrue("输入 $input 应命中「$expect」，实际前 10：${result.take(10).map { it.word }}", expect in result.map { it.word })
            }
        }
        // 歧义串 xian 的两读音来源同现
        val xianWords = engine.query("xian", limit = 200).map { it.word }
        assertTrue("先" in xianWords)
        assertTrue("西安" in xianWords)
    }

    @Test
    fun `组合态前缀查询命中`() {
        val engine = loadEngine()
        // 输入进行到一半时词组候选应已出现
        assertTrue("keti 应增量命中「客厅」", "客厅" in engine.query("keti", limit = 100).map { it.word })
        assertTrue("nih 应增量命中「你好」", "你好" in engine.query("nih", limit = 100).map { it.word })
    }

    @Test
    fun `单字词兜底`() {
        val engine = loadEngine()
        val ke = engine.query("ke", limit = 50).map { it.word }
        // 源库单字词条 17 037 条，输入单音节时单字候选应占多数
        assertTrue("输入 ke 应含单字候选，实际前 10：${ke.take(10)}", ke.take(10).any { it.length == 1 })
    }

    @Test
    fun `候选频次单调不升`() {
        val engine = loadEngine()
        for (input in listOf("ke", "keting", "xian", "zhongwen", "nihao")) {
            val freqs = engine.query(input, limit = 20).map { it.freq }
            assertEquals("输入 $input 频次应降序", freqs, freqs.sortedDescending())
        }
    }

    @Test
    fun `查询性能量级佐证_单次查询不超过200毫秒`() {
        val engine = loadEngine()
        val inputs = listOf("k", "ke", "ket", "keti", "keting", "n", "ni", "nihao", "x", "xi", "xian", "zhongwen", "zhuangzhuang")
        // 预热：触发 JIT 与常量池稳定
        repeat(30) { engine.query(inputs[it % inputs.size]) }
        // 计时：全部样例多轮查询，单次最大耗时受性能预算约束（PRD 第 8 章：中文候选刷新 ≤ 200ms）
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
        println("查询 ${count} 次，平均 ${"%.3f".format(totalMs / count)} ms，最大 ${"%.3f".format(maxMs)} ms")
        assertTrue("单次查询最大耗时 ${"%.3f".format(maxMs)} ms 超出 200 ms", maxMs < 200.0)
    }
}
