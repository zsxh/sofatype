// T82 · 决策 66 · 默认输入法夺回检测判定单元测试（JUnit 4，纯 Kotlin）。
// 锁定：默认态（default_input_method = 本 IME）不提示；夺回态（异读值 / null / 空串）出提示；
// 判定为精确全串比较；本 IME id 常量与清单声明 / `ime set` 写入值口径逐字一致（防笔误）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T82ImeTakeoverCheckTest {

    @Test
    fun `本IME在位为默认态不提示`() {
        assertFalse(ImeTakeoverCheck.needsPrompt(ImeTakeoverCheck.OWN_IME_ID))
    }

    @Test
    fun `被切到其他输入法为夺回态出提示`() {
        // 模拟器回落 Gboard / 真机回落搜狗两类夺回态读值形态（flattened ComponentName）
        assertTrue(
            ImeTakeoverCheck.needsPrompt(
                "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",
            ),
        )
        assertTrue(ImeTakeoverCheck.needsPrompt("com.iflytek.inputmethod/.FlyIME"))
    }

    @Test
    fun `读值为null或空串判夺回态`() {
        assertTrue(ImeTakeoverCheck.needsPrompt(null))
        assertTrue(ImeTakeoverCheck.needsPrompt(""))
    }

    @Test
    fun `判定为精确比较不做大小写与空白归一`() {
        assertTrue(ImeTakeoverCheck.needsPrompt("com.sofatype.ime/.sofatypeimeservice"))
        assertTrue(ImeTakeoverCheck.needsPrompt(" com.sofatype.ime/.SofatypeImeService"))
        assertTrue(ImeTakeoverCheck.needsPrompt("com.sofatype.ime/.SofatypeImeService "))
    }

    @Test
    fun `本IMEid常量与清单声明与ime-set口径逐字一致`() {
        assertEquals("com.sofatype.ime/.SofatypeImeService", ImeTakeoverCheck.OWN_IME_ID)
    }
}
