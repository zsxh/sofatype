package com.sofatype.ime

import android.os.SystemClock
import android.view.View
import com.sofatype.ime.BuildConfig
import timber.log.Timber
import java.util.Locale

object PerfTrace {

    private const val TRACE_TAG = "PerfTrace"

    private val lock = Any()

    private var keyFeedbackSeq = 0
    private var keyFeedbackStartNs = -1L
    private var keyFeedbackPending = false

    private var candidateSeq = 0
    private var candidateStartNs = -1L
    private var candidatePending = false

    private var remoteCommitSeq = 0
    private var remoteCommitStartNs = -1L
    private var remoteCommitPending = false

    fun markKeyDown() {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            if (keyFeedbackPending) trace("key_feedback seq=$keyFeedbackSeq dropped=true")
            if (candidatePending) trace("candidate_refresh seq=$candidateSeq dropped=true")
            val now = SystemClock.elapsedRealtimeNanos()
            keyFeedbackSeq += 1
            keyFeedbackStartNs = now
            keyFeedbackPending = true
            candidateSeq += 1
            candidateStartNs = now
            candidatePending = true
        }
    }

    fun endKeyFeedbackOnDraw(view: View?) {
        if (!BuildConfig.DEBUG) return
        if (view == null) {
            endKeyFeedback()
        } else {
            view.post { endKeyFeedback() }
        }
    }

    fun endKeyFeedback() {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            if (!keyFeedbackPending) return
            keyFeedbackPending = false
            emit("key_feedback", keyFeedbackSeq, keyFeedbackStartNs)
        }
    }

    fun endCandidate(totalCount: Int, pageCount: Int) {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            if (!candidatePending) return
            candidatePending = false
            emit(
                "candidate_refresh",
                candidateSeq,
                candidateStartNs,
                " total=$totalCount pages=$pageCount",
            )
        }
    }

    fun markRemoteCommitFrame() {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            if (remoteCommitPending) trace("ws_commit seq=$remoteCommitSeq dropped=true")
            remoteCommitSeq += 1
            remoteCommitStartNs = SystemClock.elapsedRealtimeNanos()
            remoteCommitPending = true
        }
    }

    fun endRemoteCommit(committed: Boolean) {
        if (!BuildConfig.DEBUG) return
        synchronized(lock) {
            if (!remoteCommitPending) return
            remoteCommitPending = false
            emit("ws_commit", remoteCommitSeq, remoteCommitStartNs, " committed=$committed")
        }
    }

    private fun emit(span: String, seq: Int, startNs: Long, suffix: String = "") {
        val durationMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        trace(String.format(Locale.US, "%s seq=%d durationMs=%.3f%s", span, seq, durationMs, suffix))
    }

    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Timber.tag(TRACE_TAG).d(message)
    }
}
