package com.yunfie.illustia.pallasync

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity

/** Debug-only empty host for real OS authentication tests; it has no account, relay or secret input UI. */
class PallaSyncRecoveryTestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
