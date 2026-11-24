package com.sofatype.ime.update

/** 单个检查候选：url + 形状（atom / JSON 数组）+ 该候选构造下载地址用的代理前缀。 */
data class UpdateCheckCandidate(
    val url: String,
    val atom: Boolean = false,
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

    /** 按候选顺序尝试，任一候选成功即返回；全部失败返回最后一个失败态。 */
    fun check(
        candidates: List<UpdateCheckCandidate>,
        currentVersion: String,
        fetch: (String) -> String,
    ): UpdateCheckResult {
        val usable = candidates.filter { it.url.trim().isNotEmpty() }
        if (usable.isEmpty()) return UpdateCheckResult.Failed(UpdateFailureReason.EMPTY_ENDPOINT)
        var lastFailed: UpdateCheckResult.Failed? = null
        for (candidate in usable) {
            // 代理前缀同时作用于检查请求与构造下载地址（gh 代理为整 URL 前缀形态）
            val fetchUrl = if (candidate.atom) candidate.proxyPrefix + candidate.url else candidate.url.trim()
            val body = try {
                fetch(fetchUrl)
            } catch (e: UpdateHttp.HttpException) {
                lastFailed = UpdateCheckResult.Failed(e.reason, e.httpStatus)
                continue
            } catch (e: Exception) {
                lastFailed = UpdateCheckResult.Failed(UpdateFailureReason.CHECK_NETWORK)
                continue
            }
            val release = try {
                if (candidate.atom) {
                    val entry = UpdateAtomParser.parse(body)
                    UpdateRelease(
                        tag = entry.tag,
                        notes = entry.notes,
                        apkUrl = UpdateAtomParser.downloadUrl(entry.tag, candidate.proxyPrefix),
                    )
                } else {
                    UpdateReleaseParser.parse(body)
                }
            } catch (e: UpdateAtomParser.ParseException) {
                lastFailed = UpdateCheckResult.Failed(e.reason)
                continue
            } catch (e: UpdateReleaseParser.ParseException) {
                lastFailed = UpdateCheckResult.Failed(e.reason)
                continue
            }
            return if (UpdateVersion.compare(currentVersion, release.tag) > 0) {
                UpdateCheckResult.Newer(release)
            } else {
                UpdateCheckResult.UpToDate
            }
        }
        return lastFailed ?: UpdateCheckResult.Failed(UpdateFailureReason.EMPTY_ENDPOINT)
    }
}
