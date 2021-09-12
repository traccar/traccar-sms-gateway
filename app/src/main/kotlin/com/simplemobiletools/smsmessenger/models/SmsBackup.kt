package com.simplemobiletools.smsmessenger.models


import android.provider.Telephony
import com.google.gson.annotations.SerializedName

data class SmsBackup(
    @SerializedName(Telephony.Sms._ID)
    val id: Long,
    @SerializedName(Telephony.Sms.ADDRESS)
    val address: String,
    @SerializedName(Telephony.Sms.BODY)
    val body: String,
    @SerializedName(Telephony.Sms.CREATOR)
    val creator: String,
    @SerializedName(Telephony.Sms.DATE)
    val date: Long,
    @SerializedName(Telephony.Sms.DATE_SENT)
    val dateSent: Int,
    @SerializedName(Telephony.Sms.ERROR_CODE)
    val errorCode: Int,
    @SerializedName(Telephony.Sms.LOCKED)
    val locked: Int,
    @SerializedName(Telephony.Sms.PERSON)
    val person: String,
    @SerializedName(Telephony.Sms.PROTOCOL)
    val protocol: String,
    @SerializedName("read")
    val read: Int,
    @SerializedName("reply_path_present")
    val replyPathPresent: Any,
    @SerializedName("seen")
    val seen: Int,
    @SerializedName("service_center")
    val serviceCenter: Any,
    @SerializedName("status")
    val status: Int,
    @SerializedName("sub_id")
    val subId: Int,
    @SerializedName("subject")
    val subject: Any,
    @SerializedName("thread_id")
    val threadId: Long,
    @SerializedName("type")
    val type: Int
)
