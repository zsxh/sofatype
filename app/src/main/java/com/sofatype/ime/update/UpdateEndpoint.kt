package com.sofatype.ime.update

import android.content.Context
import com.sofatype.ime.R

object UpdateEndpoint {

    @Volatile
    private var debugOverride: String? = null

    fun endpoint(context: Context): String =
        debugOverride?.takeIf { it.isNotBlank() } ?: context.getString(R.string.update_endpoint_url)

    fun setDebugOverride(value: String?) {
        debugOverride = value?.takeIf { it.isNotBlank() }
    }
}
