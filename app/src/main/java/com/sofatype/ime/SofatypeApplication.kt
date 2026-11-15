// T71 · 决策 60 · 应用入口：进程启动即接线调试日志组件（配置缓存加载 / 未捕获异常钩子 /
// 按开关状态挂载 Timber 文件树；logcat 树 DebugTree 常驻，行为与既有 android.util.Log 口径一致）。
// IME 服务、设置页、远程服务共用本进程，Timber 全局单树集在此一处装配。
package com.sofatype.ime

import android.app.Application
import com.sofatype.ime.debug.DebugLogController
import timber.log.Timber

class SofatypeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // logcat 树常驻：DebugTree 输出走 Log.println，TAG 由 Timber.tag(既有 TAG) 显式指定，
        // 与迁移前 android.util.Log 直写行为一致（历轮走查取证链不变，决策 60）
        Timber.plant(Timber.DebugTree())
        // 调试日志组件：配置加载 + 崩溃钩子 + 按开关挂载文件树（开关默认关，release 与 debug 行为一致）
        DebugLogController.init(this)
    }
}
