// T86 · 隐藏态确认键唤起分层兜底（PRD 决策 67，docs/issues/open/T86.md）。
// MiTV4 真机 API 23 框架对 IME 的 requestShowSelf(int) 不可达（抛 IllegalAccessError，
// crash 缓冲 10 条同签名 FATAL 在案）；本类把「隐藏态确认键唤起」做成纯 Kotlin 分层策略：
// 第一层保留 requestShowSelf（标准 API，system server 侧状态同步 + onShowInputRequested
// veto 链原样，正常框架走本层、行为零变化），调用点窄捕获固件缺陷型 LinkageError
//（IllegalAccessError 为其直接子类，单 catch 覆盖拍板两型，不做宽泛 catch）；
// 第二层 veto 复检（与框架路径同口径的 T38 收起抑制窗判定，「完成」收起后立即 OK
// 不唤起语义不变）通过后走 SDK 公开本地唤起通道 showWindow(true)（免 IPC 免反射）；
// 第三层再失败 trace 留痕 + 不崩安全降级。
// 动作以函数注入（服务层接线 SystemClock 时钟与框架调用，本类不持时钟、不依赖
// Android 框架，ImeShowGuardTest 同口径），层序与「每层降级写 ImeKeyTrace」由单测锁定。
package com.sofatype.ime

/**
 * 隐藏态确认键唤起分层兜底（T86 · 决策 67）。
 *
 * [wake] 对固件缺陷型 LinkageError（含 IllegalAccessError）保证不外抛（两层降级承接，
 * 第三层失败只留痕不崩）；其余异常（如 RuntimeException）按既有语义原样外抛，不做宽泛 catch。
 *
 * @param requestShowSelf 第一层：标准唤起通道（服务层接 `requestShowSelf(0)`）。
 * @param vetoed 第二层复检：T38 收起抑制窗判定（服务层接 `showGuard.shouldVetoShowRequest`，
 *   与 onShowInputRequested 框架裁决同口径）；true = 窗内不唤起（既有收起语义，非故障）。
 * @param showWindow 第二层通道：SDK 公开本地唤起（服务层接 `showWindow(true)`）。
 * @param trace 留痕通道：每层降级经此写 ImeKeyTrace。
 */
class HiddenConfirmWake(
    private val requestShowSelf: () -> Unit,
    private val vetoed: () -> Boolean,
    private val showWindow: () -> Unit,
    private val trace: (String) -> Unit,
) {

    /** 唤起结果（单测断言用；服务层不据此分支，吞键返回语义不变）。 */
    enum class Result {
        /** 第一层 requestShowSelf 成功（正常框架路径，行为零变化）。 */
        REQUEST_SHOW_SELF,

        /** 第一层不可达，veto 复检通过后第二层 showWindow 唤起成功。 */
        SHOW_WINDOW,

        /** 第一层不可达，T38 收起抑制窗内 veto 拦下（不唤起 = 既有语义）。 */
        VETOED,

        /** 全层兜底失败，trace 留痕后不崩安全降级。 */
        DEGRADED,
    }

    /**
     * 分层唤起。层序见类注释；每层降级写 trace；固件缺陷型 LinkageError（含
     * IllegalAccessError）不外抛（第三层兜底），其余异常原样外抛。
     */
    fun wake(): Result {
        // 第一层：标准通道保留原样；仅窄捕获固件缺陷型 LinkageError
        //（真机 IllegalAccessError 为其子类），其余异常按既有语义外抛。
        try {
            requestShowSelf()
            return Result.REQUEST_SHOW_SELF
        } catch (firmware: LinkageError) {
            trace("requestShowSelf 不可达（${firmware.javaClass.simpleName}）-> 降级第二层 showWindow")
        }
        // 第二层：veto 复检——与 onShowInputRequested 同口径的 T38 收起抑制窗判定，
        // 窗内不唤起（「完成」收起后立即 OK 不唤起语义零回归；这是设计内分支非故障）。
        if (vetoed()) {
            trace("隐藏态唤起 veto（T38 收起抑制窗内，不唤起）")
            return Result.VETOED
        }
        // 第二层通道：SDK 公开本地唤起，免 IPC 免反射；再失败落第三层。
        return try {
            showWindow()
            trace("showWindow 兜底唤起成功（第二层通道）")
            Result.SHOW_WINDOW
        } catch (firmware: LinkageError) {
            // 第三层：全层兜底失败——trace 留痕 + 不崩安全降级（进程存活，键盘不来）。
            trace("showWindow 兜底亦失败（${firmware.javaClass.simpleName}）-> 安全降级不崩（第三层）")
            Result.DEGRADED
        }
    }
}
