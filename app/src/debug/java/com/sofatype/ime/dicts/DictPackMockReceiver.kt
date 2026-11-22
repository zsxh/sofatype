package com.sofatype.ime.dicts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sofatype.ime.BuildConfig

class DictPackMockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        when (intent.action) {
            ACTION_SET_CATALOG -> {
                val endpoint = intent.getStringExtra(EXTRA_ENDPOINT)
                DictPackEndpoint.setDebugOverride(endpoint)
                Log.i(TAG, "词包目录地址覆写：${DictPackEndpoint.catalogUrl()}")
            }
            else -> Unit
        }
    }

    companion object {
        const val TAG = "DictPack"
        const val ACTION_SET_CATALOG = "com.sofatype.ime.debug.action.SET_DICT_CATALOG"
        const val EXTRA_ENDPOINT = "endpoint"
    }
}
