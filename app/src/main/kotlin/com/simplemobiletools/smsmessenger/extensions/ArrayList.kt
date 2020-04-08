package com.simplemobiletools.smsmessenger.extensions

import android.text.TextUtils
import com.simplemobiletools.smsmessenger.models.Contact

fun ArrayList<Contact>.getThreadTitle() = TextUtils.join(", ", map { it.name }.toTypedArray())
