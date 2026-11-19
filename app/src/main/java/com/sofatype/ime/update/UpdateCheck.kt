package com.sofatype.ime.update

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
