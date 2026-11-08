// T21 · 更新源接口地址读取（决策 17：接口地址可配置、不硬编码，正式地址后补）。
// 默认值 = strings 资源 update_endpoint_url（当前置空：检查返回「更新源未配置」）；
// debug 走查可经 UpdateMockReceiver 写进程内存覆写位（仅内存、不持久化，进程死亡即清）。
package com.sofatype.ime.update

import android.content.Context
import com.sofatype.ime.R

object UpdateEndpoint {

    @Volatile
    private var debugOverride: String? = null

    /** 当前生效的接口地址：debug 内存覆写优先，否则取资源默认值。 */
    fun endpoint(context: Context): String =
        debugOverride?.takeIf { it.isNotBlank() } ?: context.getString(R.string.update_endpoint_url)

    /** debug 走查通道写入覆写地址；传 null 或空白清除覆写。 */
    fun setDebugOverride(value: String?) {
        debugOverride = value?.takeIf { it.isNotBlank() }
    }
}
