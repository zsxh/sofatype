package com.sofatype.ime.update

import android.content.Context
import com.sofatype.ime.R

object UpdateEndpoint {

    private const val PREFS = "update_endpoint"
    private const val KEY_PROXY_ENABLED = "proxy_enabled"

    /** 顺序回退链的代理前缀（T104 拍板：gh-proxy.com → v6.gh-proxy.org → 直连）。 */
    const val PROXY_GH_PROXY_COM = "https://gh-proxy.com/"
    const val PROXY_V6_GH_PROXY_ORG = "https://v6.gh-proxy.org/"

    @Volatile
    private var debugOverrideUrl: String? = null

    @Volatile
    private var debugOverrideAtom: Boolean = false

    /** 检查候选：默认真实 Atom 源按代理偏好展开回退链；debug 注入（UpdateMockReceiver）覆写为 mock JSON 源。 */
    fun candidates(context: Context): List<UpdateCheckCandidate> {
        debugOverrideUrl?.takeIf { it.isNotBlank() }?.let { url ->
            return listOf(UpdateCheckCandidate(url = url, atom = debugOverrideAtom))
        }
        val atomUrl = context.getString(R.string.update_atom_url)
        return if (proxyEnabled(context)) {
            listOf(
                UpdateCheckCandidate(atomUrl, atom = true, proxyPrefix = PROXY_GH_PROXY_COM),
                UpdateCheckCandidate(atomUrl, atom = true, proxyPrefix = PROXY_V6_GH_PROXY_ORG),
                UpdateCheckCandidate(atomUrl, atom = true),
            )
        } else {
            listOf(UpdateCheckCandidate(atomUrl, atom = true))
        }
    }

    /** 主代理域名展示（设置页小字）。 */
    fun primaryProxyHost(): String =
        PROXY_GH_PROXY_COM.removePrefix("https://").removeSuffix("/")

    /** 下载代理前缀：设置页开关（默认开，gh-proxy.com）。 */
    fun proxyPrefix(context: Context): String =
        if (proxyEnabled(context)) PROXY_GH_PROXY_COM else ""

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
