package com.simplemobiletools.smsmessenger.activities

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.smsmessenger.R

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher,
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getPackageName(): String {
        val trace = Thread.currentThread().stackTrace
        for (i in 1 until trace.size) {
            val currentItem = trace[i]
            if (currentItem.methodName == "getPackageName") {
                val nextItem = trace[i + 1]
                if (nextItem.methodName == "onCreate") {
                    return "com.simplemobiletools.smsmessenger"
                }
            }
        }
        return super.getPackageName()
    }
}
