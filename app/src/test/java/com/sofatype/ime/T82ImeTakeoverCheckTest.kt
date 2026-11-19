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
