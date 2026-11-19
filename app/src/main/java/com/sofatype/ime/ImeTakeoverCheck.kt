package com.sofatype.ime

object ImeTakeoverCheck {

    const val OWN_IME_ID = "com.sofatype.ime/.SofatypeImeService"

    fun needsPrompt(currentDefaultIme: String?): Boolean = currentDefaultIme != OWN_IME_ID
}
