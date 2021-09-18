package com.simplemobiletools.smsmessenger.models

import com.google.gson.annotations.SerializedName

data class ExportedMessage(
    @SerializedName("threadId")
    val threadId: Long,
    @SerializedName("sms")
    val sms: List<SmsBackup>,
    @SerializedName("mms")
    val mms: List<MmsBackup>,
)
