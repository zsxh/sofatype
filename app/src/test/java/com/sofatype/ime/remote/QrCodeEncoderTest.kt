package com.sofatype.ime.remote

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeEncoderTest {

    private fun decode(matrix: BitMatrix): String {
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] =
                    if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        val bitmap = BinaryBitmap(GlobalHistogramBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    @Test
    fun encodesPairingUrlAndDecodesBackIdentically() {
        val url = "http://192.168.1.210:32923/?token=AbC-dEf_GhI-JkLmNo12"
        val matrix = QrCodeEncoder.encode(url, 376)
        assertEquals(url, decode(matrix))
    }

    @Test
    fun matrixIsSquareOfRequestedSize() {
        for (size in listOf(188, 376, 752)) {
            val matrix = QrCodeEncoder.encode("http://10.0.2.15:1/?token=t", size)
            assertEquals(size, matrix.width)
            assertEquals(size, matrix.height)
        }
    }

    @Test
    fun matrixHasQuietZoneAndDarkModules() {
        val matrix = QrCodeEncoder.encode("http://10.0.2.15:1/?token=t", 376)

        assertFalse(matrix.get(0, 0))
        assertFalse(matrix.get(matrix.width - 1, matrix.height - 1))

        var dark = 0
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) dark++
            }
        }
        assertTrue("应有实质深色模块：$dark", dark > matrix.width * matrix.height / 10)
    }

    @Test
    fun differentTokensProduceDifferentMatrices() {
        val a = QrCodeEncoder.encode("http://t:1/?token=aaa", 376)
        val b = QrCodeEncoder.encode("http://t:1/?token=bbb", 376)
        assertNotEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveSize() {
        QrCodeEncoder.encode("x", 0)
    }
}
