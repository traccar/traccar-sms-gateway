package com.simplemobiletools.smsmessenger.models

import android.net.Uri

data class Attachment(var uriString: String, var mimetype: String, var width: Int, var height: Int, var filename: String) {
    fun getUri() = Uri.parse(uriString)
}
