package com.yunfie.illustia.account

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PalleriaAuthenticatorService : Service() {
    private val authenticator by lazy { PalleriaAuthenticator(this) }
    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder
}

class PalleriaSyncService : Service() {
    private val adapter by lazy { PalleriaSyncAdapter(this) }
    override fun onBind(intent: Intent?): IBinder = adapter.syncAdapterBinder
}
