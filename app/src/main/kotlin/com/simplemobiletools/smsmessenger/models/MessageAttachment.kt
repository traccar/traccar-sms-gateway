package com.simplemobiletools.smsmessenger.models

import android.net.Uri

data class MessageAttachment(val id: Int, var text: String, var uri: Uri?, var type: String)
