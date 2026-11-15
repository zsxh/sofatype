// T71 · 决策 60 · 日志大小限制档位（纯 Kotlin 模型，单元测试锁定 DebugLogOptionsTest）。
// 四档 1/2/5/10 MB，默认 2 MB；字节换算与越界钳制收口本类（UI 与文件树共用同一口径，
// 落盘轮转阈值与子页档位选择不漂移）。纯 JVM，无 Android 依赖。
package com.sofatype.ime.debug

object DebugLogOptions {

    /** 可选档位（MB），升序；UI 呈现与持久化取值都来自本集合。 */
    val MB_CHOICES: IntArray = intArrayOf(1, 2, 5, 10)

    /** 默认档位（决策 60：默认 2 MB）。 */
    const val DEFAULT_MB = 2

    /** MB → 字节（轮转阈值口径）。 */
    fun bytesFor(mb: Int): Long = mb.toLong() * 1024L * 1024L

    /** 越界/非法档位钳制回合法档位（持久化值损坏时兜底）。 */
    fun clampMb(rawMb: Int): Int = MB_CHOICES.firstOrNull { it >= rawMb } ?: MB_CHOICES.last()

    /** 档位展示文案（画板 14「1 MB / 2 MB / 5 MB / 10 MB」口径）。 */
    fun label(mb: Int): String = "$mb MB"
}
