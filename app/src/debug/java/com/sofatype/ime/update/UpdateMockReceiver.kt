package com.sofatype.ime.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sofatype.ime.BuildConfig

class UpdateMockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        when (intent.action) {
            ACTION_SET_ENDPOINT -> {
                val endpoint = intent.getStringExtra(EXTRA_ENDPOINT)
                UpdateEndpoint.setDebugOverride(endpoint)
                Log.i(TAG, "更新源接口地址覆写：${UpdateEndpoint.endpoint(context)}")
            }
            else -> Unit
        }
    }

    companion object {
        const val TAG = "UpdateCheck"
        const val ACTION_SET_ENDPOINT = "com.sofatype.ime.debug.action.SET_UPDATE_ENDPOINT"
        const val EXTRA_ENDPOINT = "endpoint"
    }
}
