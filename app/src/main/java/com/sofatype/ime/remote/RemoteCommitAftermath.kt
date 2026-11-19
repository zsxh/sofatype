package com.sofatype.ime.remote

class RemoteCommitAftermath private constructor(
    val acknowledge: Boolean,
    val stopService: Boolean,
    val clearRemoteBuffer: Boolean,
) {
    companion object {

        fun of(commitOk: Boolean): RemoteCommitAftermath =
            if (commitOk) {
                RemoteCommitAftermath(acknowledge = true, stopService = false, clearRemoteBuffer = true)
            } else {
                RemoteCommitAftermath(acknowledge = false, stopService = false, clearRemoteBuffer = false)
            }
    }
}
