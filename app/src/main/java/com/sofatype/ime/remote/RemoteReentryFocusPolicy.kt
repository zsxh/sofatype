// T78 · 决策 64：远程保活收起后自动重显沿的默认焦点策略（纯 Kotlin 状态机，无 Android 依赖）。
// 缺陷链（设备档案坑 25 / closed/T74 真机复验节）：已连接态 BACK 保活收起（不开抑制窗）→
// 宿主编辑器间 DPAD 切框沿 show 请求自动重显远程（T74/T17 设计内重入路径）→ 重显沿
// 「退出远程」键默认持焦（决策 50-①）→ 用户下一发 OK 命中退出按钮误杀会话（MiTV4 API 23
// trace 留证）。户主拍板修复口径 = 方向 B（决策 64）：保活收起后的自动重显沿默认焦点
// 不落「退出远程」键（本模型判 NONE），首发 OK 不触发退出；决策 50-① 其余场景不变——
// 首次进入远程模式与显式唤起路径（隐藏态确认键 requestShowSelf 唤醒护栏）仍默认持焦
//（本模型判 EXIT_BUTTON）；「退出远程」仍可 DPAD 导航到达（FR-B7 显式退出通道不变）。
// 方向 A（保活分支开抑制窗拦 show）未采用（决策 64 明示）。
// 状态机单点事实：pending = 「保活收起已发生、重显沿尚未消费」；一次性消费（首个 show 沿
// 取走 NONE 后回默认），远程模式退出沿 reset（防陈旧 pending 跨越完整远程生命周期）。
// 接线在 SofatypeImeService：onKeepAliveHide（BACK HIDE_KEEP_ALIVE 分支）/ onExplicitWake
//（隐藏态确认键 requestShowSelf 分支）/ resolveOnShow（onStartInputView 远程分支）/
// reset（renderMainCardMode REMOTE→KEYBOARD 转换沿）。单元测试锁定全部转移。
package com.sofatype.ime.remote

/**
 * 保活收起后重显沿默认焦点决策（T78 · 决策 64）。
 */
class RemoteReentryFocusPolicy {

    /** 重显沿默认焦点结论。 */
    enum class ReentryFocus {
        /** 「退出远程」键默认持焦（决策 50-①：首次进入 / 显式唤起路径，OK 一击即退）。 */
        EXIT_BUTTON,

        /** 无默认焦点（决策 64：保活收起后自动重显沿，首发 OK 不命中退出按钮）。 */
        NONE,
    }

    /** 保活收起标记：true = 收起沿已标记、重显沿未消费（服务层在 BACK HIDE_KEEP_ALIVE 分支置位）。 */
    private var pending = false

    /** 保活收起沿置位（幂等；重复 BACK 收起不叠加）。 */
    fun onKeepAliveHide() {
        pending = true
    }

    /** 显式唤起沿清除（隐藏态确认键 requestShowSelf 唤醒护栏：决策 50-① 默认持焦不变）。 */
    fun onExplicitWake() {
        pending = false
    }

    /**
     * show 沿裁决（一次性消费）：pending 在 → 判 NONE 并消费（保活重显沿无默认焦点）；
     * pending 不在 → 判 EXIT_BUTTON（决策 50-① 既有口径）。同一保活周期只消费一次，
     * 其后 show 沿（会话重开 / 键盘重建补渲染）回归默认持焦，不吞后续沿的焦点迁移。
     */
    fun resolveOnShow(): ReentryFocus {
        if (!pending) return ReentryFocus.EXIT_BUTTON
        pending = false
        return ReentryFocus.NONE
    }

    /** 远程模式退出沿复位（REMOTE→KEYBOARD 转换沿接线；防陈旧 pending 跨完整远程生命周期）。 */
    fun reset() {
        pending = false
    }
}
