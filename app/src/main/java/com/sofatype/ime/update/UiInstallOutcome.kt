// T47 · UI 渠道安装结果判定纯模型（决策 36）：UI 渠道无会话回执，
// 回设置页比对自身 versionCode——版本升 = 成功；未升 = 用户在安装器取消或安装失败，
// 入「失败可重试」态（T21 口径）。判定时机（Activity onResume 重进前台）与标记存取
// （Android SharedPreferences）在 SettingsActivity 接线，本文件保持无 Android 依赖。
// 标记须持久化：升级成功时系统替换包必杀应用进程，内存标记活不到「返回设置页」。
package com.sofatype.ime.update

/** UI 渠道安装结果判定（versionCode 比对）。 */
enum class UiInstallDecision {

    /** 安装后 versionCode 上升：升级成功。 */
    UPDATED,

    /** 未升级（用户取消或安装失败）：失败红点 + 原因文案 + 可重试（T21 口径）。 */
    NOT_UPDATED,
}

object UiInstallReceipt {

    /**
     * versionCode 比对判定：after 严格大于 before（且 before 为合法正值）→ UPDATED，
     * 其余（相等=取消、下降=降级异常、before 非法）一律 NOT_UPDATED 归入失败可重试。
     */
    fun decisionFor(versionCodeBefore: Int, versionCodeAfter: Int): UiInstallDecision =
        if (versionCodeBefore > 0 && versionCodeAfter > versionCodeBefore) {
            UiInstallDecision.UPDATED
        } else {
            UiInstallDecision.NOT_UPDATED
        }
}

/** UI 渠道安装 in-flight 标记载荷编解码（SharedPreferences 单字符串值，消费即清）。 */
object UiInstallMarker {

    private const val PREFIX = "before="

    /** 编码为可持久化字符串载荷：`before=<versionCode>`。 */
    fun encode(versionCodeBefore: Int): String = "$PREFIX$versionCodeBefore"

    /** 解码载荷；缺失 / 格式不符 / 非正数一律返回 null（视为无 in-flight 标记）。 */
    fun decode(raw: String?): Int? {
        if (raw == null || !raw.startsWith(PREFIX)) return null
        val value = raw.substringAfter(PREFIX).toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
    }
}
