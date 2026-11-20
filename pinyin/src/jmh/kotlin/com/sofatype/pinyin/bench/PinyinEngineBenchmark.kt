package com.sofatype.pinyin.bench

import com.sofatype.pinyin.DictFormat
import com.sofatype.pinyin.PinyinEngine
import com.sofatype.pinyin.PinyinSegmenter
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class PinyinEngineBenchmark {

    private lateinit var engine: PinyinEngine

    private lateinit var segmenter: PinyinSegmenter

    private val fullPinyinInputs = listOf("nihao", "keting", "beijing", "shanghai", "xian")

    private val abbrInputs = listOf("zg", "bj", "sh", "zm")

    private val mixedInputs = listOf("zguo", "zhli", "zhil", "shrf")

    private val segmentInputs = listOf("nihao", "xian", "zhongguo", "jiujiang")

    @Setup(Level.Trial)
    fun setup() {
        engine = loadEngine()
        val data = DictFormat.read(
            PinyinEngineBenchmark::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
                ?: throw IllegalStateException("词库产物不在 classpath：先执行 ./gradlew :pinyin:buildDict"),
        )
        segmenter = PinyinSegmenter(data.syllables.toSet())
    }

    private fun loadEngine(): PinyinEngine {
        val stream = PinyinEngineBenchmark::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
            ?: throw IllegalStateException("词库产物不在 classpath：先执行 ./gradlew :pinyin:buildDict")
        return stream.use { PinyinEngine.load(it) }
    }

    @Benchmark
    fun fullPinyinQuery(bh: Blackhole) {
        for (input in fullPinyinInputs) bh.consume(engine.query(input, 24))
    }

    @Benchmark
    fun abbreviationQuery(bh: Blackhole) {
        for (input in abbrInputs) bh.consume(engine.query(input, 24))
    }

    @Benchmark
    fun mixedQuery(bh: Blackhole) {
        for (input in mixedInputs) bh.consume(engine.query(input, 24))
    }

    @Benchmark
    fun segmentation(bh: Blackhole) {
        for (input in segmentInputs) bh.consume(segmenter.segmentAll(input))
    }
}
