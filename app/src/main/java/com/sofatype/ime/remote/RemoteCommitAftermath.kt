// T70 · commit 上屏处置模型（决策 59 · FR-B6 / FR-C6 修订）：
// 上屏写入成功不再停服收起——WS 连接与远程模式保持，手机端清草稿提示「已上屏」后可继续
// 编辑再次上屏；TV 端上屏成功即清大字区与远程缓冲（立即回等待输入态），状态行保持「已连接」。
// 写入失败路径不变：远程模式与缓冲保持，手机端提示失败可重试（FR-C6）。
// 决策 59 同时废止 T16 / R-4「上屏成功自动收起」口径：停服仅由显式退出（FR-B7）、
// 空闲到期（T25 无连接 30 分钟 / T70 已连接无活动 10 分钟）与网络重建触发。
// 纯 Kotlin，不依赖 Android 类：处置结论由本模型单点给出（单一事实源），接线在
// RemoteInputServerManager.onRemoteCommit（主线程写入 + teardown 线程回执，既有 T18 异步化口径不变），
// 单元测试锁定三要素；IME 写入本体在 SofatypeImeService.commitRemoteText（IC 光标处插入，R-3 不变）。
package com.sofatype.ime.remote

/**
 * 一次 commit 上屏指令的处置结论（T70 · 决策 59，三要素一次性给出）：
 * - [acknowledge]：回执 committed { ok } 给手机端（ok 值 = 写入结果，成功即清草稿提示「已上屏」）；
 * - [clearRemoteBuffer]：清空远程缓冲并经主线程通知 UI 清大字区（仅成功路径；
 *   失败路径保持缓冲——用户草稿不丢，可修正后重试）；
 * - [stopService]：是否停服——决策 59 后两个分支恒为 false（上屏不再触发停服收起）；
 *   保留为显式字段使停服决策收口于本模型（单一事实源），单元测试锁定不回退到 R-4 旧口径。
 */
class RemoteCommitAftermath private constructor(
    val acknowledge: Boolean,
    val stopService: Boolean,
    val clearRemoteBuffer: Boolean,
) {
    companion object {

        /**
         * 依上屏写入结果推导处置（唯一入口）：
         * - 写入成功：回执成功 + 清缓冲 + 不停服（保活连续上屏，决策 59）；
         * - 写入失败（无焦点输入框 / 写入异常 / 键盘侧未就绪）：回执失败 + 缓冲与远程模式保持，
         *   不停服（FR-C6 失败可重试，既有口径不变）。
         */
        fun of(commitOk: Boolean): RemoteCommitAftermath =
            if (commitOk) {
                RemoteCommitAftermath(acknowledge = true, stopService = false, clearRemoteBuffer = true)
            } else {
                RemoteCommitAftermath(acknowledge = false, stopService = false, clearRemoteBuffer = false)
            }
    }
}
