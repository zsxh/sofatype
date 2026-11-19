package com.sofatype.ime

class HiddenConfirmWake(
    private val requestShowSelf: () -> Unit,
    private val vetoed: () -> Boolean,
    private val showWindow: () -> Unit,
    private val trace: (String) -> Unit,
) {

    enum class Result {

        REQUEST_SHOW_SELF,

        SHOW_WINDOW,

        VETOED,

        DEGRADED,
    }

    fun wake(): Result {

        try {
            requestShowSelf()
            return Result.REQUEST_SHOW_SELF
        } catch (firmware: LinkageError) {
            trace("requestShowSelf 不可达（${firmware.javaClass.simpleName}）-> 降级第二层 showWindow")
        }

        if (vetoed()) {
            trace("隐藏态唤起 veto（T38 收起抑制窗内，不唤起）")
            return Result.VETOED
        }

        return try {
            showWindow()
            trace("showWindow 兜底唤起成功（第二层通道）")
            Result.SHOW_WINDOW
        } catch (firmware: LinkageError) {

            trace("showWindow 兜底亦失败（${firmware.javaClass.simpleName}）-> 安全降级不崩（第三层）")
            Result.DEGRADED
        }
    }
}
