package com.sofatype.ime.update

enum class UpdateFailureReason {

    EMPTY_ENDPOINT,

    CHECK_NETWORK,

    CHECK_HTTP,

    PARSE,

    NO_APK_ASSET,

    DOWNLOAD_NETWORK,

    DOWNLOAD_IO,

    INSTALL_PREPARE,

    INSTALL_COMMIT,

    UI_INSTALL_NOT_UPDATED,
}
