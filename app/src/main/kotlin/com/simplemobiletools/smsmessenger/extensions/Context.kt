package com.simplemobiletools.smsmessenger.extensions

import android.content.Context
import com.simplemobiletools.smsmessenger.helpers.Config

val Context.config: Config get() = Config.newInstance(applicationContext)
