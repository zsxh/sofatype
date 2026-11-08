// T21 · 检查更新编排纯函数（FR-D4 三态 / 决策 17/18：纯手动触发，一次 check 一态）。
// fetch 以 lambda 注入：单元测试注入假实现，线上接线 UpdateHttp::fetchText。
package com.sofatype.ime.update

/** 检查结果三态（FR-D4）。 */
sealed class UpdateCheckResult {
    /** ① 已是最新版本（含 tag 旧于当前版本，不做降级）。 */
    object UpToDate : UpdateCheckResult()

    /** ② 发现新版本，[release] 携带新版本号 / 更新说明 / APK 下载地址。 */
    data class Newer(val release: UpdateRelease) : UpdateCheckResult()

    /** ③ 失败，[httpStatus] 仅 CHECK_HTTP / DOWNLOAD_NETWORK 非 2xx 时有值。 */
    data class Failed(
        val reason: UpdateFailureReason,
        val httpStatus: Int? = null,
    ) : UpdateCheckResult()
}

object UpdateCheck {

    /**
     * 一次完整检查：空地址 → EMPTY_ENDPOINT；取回失败 → 网络或状态码；
     * 解析失败 → PARSE / NO_APK_ASSET；tag 严格大于当前版本 → Newer，否则 UpToDate。
     */
    fun check(
        endpoint: String?,
        currentVersion: String,
        fetch: (String) -> String,
    ): UpdateCheckResult {
        val url = endpoint?.trim().orEmpty()
        if (url.isEmpty()) return UpdateCheckResult.Failed(UpdateFailureReason.EMPTY_ENDPOINT)
        val body = try {
            fetch(url)
        } catch (e: UpdateHttp.HttpException) {
            return UpdateCheckResult.Failed(e.reason, e.httpStatus)
        } catch (e: Exception) {
            return UpdateCheckResult.Failed(UpdateFailureReason.CHECK_NETWORK)
        }
        val release = try {
            UpdateReleaseParser.parse(body)
        } catch (e: UpdateReleaseParser.ParseException) {
            return UpdateCheckResult.Failed(e.reason)
        }
        return if (UpdateVersion.compare(currentVersion, release.tag) > 0) {
            UpdateCheckResult.Newer(release)
        } else {
            UpdateCheckResult.UpToDate
        }
    }
}
