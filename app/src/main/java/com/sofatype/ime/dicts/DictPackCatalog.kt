package com.sofatype.ime.dicts

import com.sofatype.ime.update.UpdateFailureReason
import com.sofatype.ime.update.UpdateHttp

enum class DictPackFailure {
    EMPTY_ENDPOINT,
    NETWORK,
    HTTP,
    PARSE,
    VERIFY,
    IO,
}

sealed class DictPackCatalogResult {

    data class Catalog(val entries: List<DictPackEntry>) : DictPackCatalogResult()

    data class Failed(val reason: DictPackFailure, val httpStatus: Int? = null) : DictPackCatalogResult()
}

object DictPackCatalog {

    fun fetch(url: String): DictPackCatalogResult {
        if (url.isBlank()) return DictPackCatalogResult.Failed(DictPackFailure.EMPTY_ENDPOINT)
        return try {
            DictPackCatalogResult.Catalog(DictPackManifestParser.parse(UpdateHttp.fetchText(url)))
        } catch (e: UpdateHttp.HttpException) {
            DictPackCatalogResult.Failed(mapReason(e.reason), e.httpStatus)
        } catch (e: DictPackManifestParser.ParseException) {
            DictPackCatalogResult.Failed(DictPackFailure.PARSE)
        }
    }

    private fun mapReason(reason: UpdateFailureReason): DictPackFailure =
        when (reason) {
            UpdateFailureReason.CHECK_HTTP -> DictPackFailure.HTTP
            UpdateFailureReason.DOWNLOAD_IO -> DictPackFailure.IO
            else -> DictPackFailure.NETWORK
        }
}
