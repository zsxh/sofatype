// T13 · Base64 编码单元测试：RFC 4648 第 10 节测试向量（覆盖 0/1/2/3 字节余数分支）。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class Base64CodecTest {

    private fun encode(text: String): String = Base64Codec.encode(text.toByteArray(Charsets.US_ASCII))

    @Test
    fun emptyInputEncodesToEmptyString() {
        assertEquals("", Base64Codec.encode(ByteArray(0)))
    }

    @Test
    fun rfc4648Section10Vectors() {
        assertEquals("Zg==", encode("f"))
        assertEquals("Zm8=", encode("fo"))
        assertEquals("Zm9v", encode("foo"))
        assertEquals("Zm9vYg==", encode("foob"))
        assertEquals("Zm9vYmE=", encode("fooba"))
        assertEquals("Zm9vYmFy", encode("foobar"))
    }

    @Test
    fun binaryPayloadRoundTripsThroughDecoderExpectation() {
        // 0x00..0xFF 全量字节序列，期望值取自标准 Base64 实现（3 字节余数 0 的长序列）
        val all = ByteArray(256) { it.toByte() }
        assertEquals("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV5fYGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6PkJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uvs7e7v8PHy8/T19vf4+fr7/P3+/w==",
            Base64Codec.encode(all))
    }
}
