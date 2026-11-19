package com.sofatype.ime

object VersionFormatter {

    fun format(versionName: String?): String = "v${versionName.orEmpty()}"
}
