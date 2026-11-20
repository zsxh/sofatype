package com.sofatype.ime

import android.view.View

object PerfTrace {

    fun markKeyDown() {}

    fun endKeyFeedbackOnDraw(view: View?) {}

    fun endKeyFeedback() {}

    fun endCandidate(totalCount: Int, pageCount: Int) {}

    fun markRemoteCommitFrame() {}

    fun endRemoteCommit(committed: Boolean) {}
}
