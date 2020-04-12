package com.simplemobiletools.smsmessenger.models

import android.net.Uri

data class Attachment(var uri: Uri, var mimetype: String, var width: Int, var height: Int, var filename: String)
