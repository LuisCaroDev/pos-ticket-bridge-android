package com.luiscarodev.posticketbridge

import android.app.Application
import com.luiscarodev.posticketbridge.bridge.BridgeRuntimeRepository
import com.luiscarodev.posticketbridge.data.BridgeSettingsRepository

class BridgeApplication : Application() {
    val settingsRepository by lazy { BridgeSettingsRepository(applicationContext) }
    val runtimeRepository by lazy { BridgeRuntimeRepository() }
}
