package com.simplemobiletools.smsmessenger.models

import com.google.gson.annotations.SerializedName

data class ExportedMessage(
    @SerializedName("threadId")
    val threadId: Long,
    @SerializedName("sms")
    val sms: List<Map<String, String>>,
    @SerializedName("mms")
    val mms: List<Map<String, Any>>,
)
