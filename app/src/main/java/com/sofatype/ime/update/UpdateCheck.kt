package com.sofatype.ime.update

data class UpdateCheckConfig(

    /** 检查源地址：默认为 Releases Atom（真实源）；debug 注入 mock 时为 mock JSON 地址。 */
    val url: String,

    /** true = Atom 形状（真实源，下载地址按 T100 口径构造）；false = JSON 数组形状（debug mock）。 */
    val atom: Boolean = false,

    /** 下载代理前缀（仅 atom 模式生效）：形如 "https://gh-proxy.com/"，空 = 直连。 */
    val proxyPrefix: String = "",
)

sealed class UpdateCheckResult {

    object UpToDate : UpdateCheckResult()

    data class Newer(val release: UpdateRelease) : UpdateCheckResult()

    data class Failed(
        val reason: UpdateFailureReason,
        val httpStatus: Int? = null,
    ) : UpdateCheckResult()
}

object UpdateCheck {

    fun check(
        config: UpdateCheckConfig,
        currentVersion: String,
        fetch: (String) -> String,
    ): UpdateCheckResult {
        val url = config.url.trim()
        if (url.isEmpty()) return UpdateCheckResult.Failed(UpdateFailureReason.EMPTY_ENDPOINT)
        val body = try {
            fetch(url)
        } catch (e: UpdateHttp.HttpException) {
            return UpdateCheckResult.Failed(e.reason, e.httpStatus)
        } catch (e: Exception) {
            return UpdateCheckResult.Failed(UpdateFailureReason.CHECK_NETWORK)
        }
        val release = try {
            if (config.atom) {
                val entry = UpdateAtomParser.parse(body)
                UpdateRelease(
                    tag = entry.tag,
                    notes = entry.notes,
                    apkUrl = UpdateAtomParser.downloadUrl(entry.tag, config.proxyPrefix),
                )
            } else {
                UpdateReleaseParser.parse(body)
            }
        } catch (e: UpdateAtomParser.ParseException) {
            return UpdateCheckResult.Failed(e.reason)
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
