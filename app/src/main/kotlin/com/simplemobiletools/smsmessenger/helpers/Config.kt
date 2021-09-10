package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit().putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit().putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var enableDeliveryReports: Boolean
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, true)
        set(enableDeliveryReports) = prefs.edit().putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit().putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_1_MB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()
}
