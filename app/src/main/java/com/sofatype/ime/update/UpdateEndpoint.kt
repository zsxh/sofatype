package com.sofatype.ime.update

import android.content.Context
import com.sofatype.ime.R

object UpdateEndpoint {

    private const val PREFS = "update_endpoint"
    private const val KEY_PROXY_ENABLED = "proxy_enabled"
    private const val PROXY_DEFAULT = "https://gh-proxy.com/"

    @Volatile
    private var debugOverrideUrl: String? = null

    @Volatile
    private var debugOverrideAtom: Boolean = false

    /** 检查配置：默认真实 Atom 源 + 用户代理偏好；debug 注入（UpdateMockReceiver）覆写为 mock JSON 源。 */
    fun config(context: Context): UpdateCheckConfig {
        val url = debugOverrideUrl?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.update_atom_url)
        return UpdateCheckConfig(
            url = url,
            atom = debugOverrideUrl != null && debugOverrideAtom,
            proxyPrefix = proxyPrefix(context),
        )
    }

    /** 下载代理前缀：设置页开关（默认开，gh-proxy.com）。 */
    fun proxyPrefix(context: Context): String =
        if (proxyEnabled(context)) PROXY_DEFAULT else ""

    fun proxyEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROXY_ENABLED, true)

    fun setProxyEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROXY_ENABLED, enabled)
            .apply()
    }

    fun setDebugOverride(url: String?, atom: Boolean = false) {
        debugOverrideUrl = url?.takeIf { it.isNotBlank() }
        debugOverrideAtom = debugOverrideUrl != null && atom
    }
}
