package com.simplemobiletools.smsmessenger

import android.app.Application
import com.klinker.android.send_message.ApnUtils
import com.simplemobiletools.commons.extensions.checkUseEnglish
import com.simplemobiletools.commons.helpers.isRPlus

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()

        if (isRPlus()) {
            ApnUtils.initDefaultApns(this) {}
        }
    }
}
