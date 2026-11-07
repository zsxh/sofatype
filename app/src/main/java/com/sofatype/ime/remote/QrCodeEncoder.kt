// T14 · 配对二维码编码（FR-B4；工具链拍板 2026-11-03：二维码用 ZXing core，纯 Java 无 android 依赖）。
// 产出 BitMatrix；位图渲染在 UI 层（com.sofatype.ime.PairingCard）。可被 JVM 单元测试覆盖
//（zxing core 同时含解码器，用例做编码 → 解码回环校验）。
package com.sofatype.ime.remote

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeEncoder {

    /**
     * 生成正方形二维码矩阵（宽高均 = sizePx，含 QR 规范默认 4 模块静区）。
     * 纠错级别 M（约 15%）：TV 屏幕经手机拍摄扫码比纸质打印更易引入损伤，取中档纠错；
     * 字符集固定 UTF-8。内容超容或 sizePx 非法时抛 IllegalArgumentException（调用方保证入参）。
     */
    fun encode(content: String, sizePx: Int): BitMatrix {
        require(sizePx > 0) { "二维码边长必须为正：$sizePx" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        return QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }
}
