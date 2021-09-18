package com.simplemobiletools.smsmessenger.models

import android.content.ContentValues
import android.provider.Telephony
import androidx.core.content.contentValuesOf
import com.google.gson.annotations.SerializedName

data class MmsPart(
    @SerializedName("cd")
    val contentDisposition: String?,
    @SerializedName("chset")
    val charset: String?,
    @SerializedName("cid")
    val contentId: String?,
    @SerializedName("cl")
    val contentLocation: String?,
    @SerializedName("ct")
    val contentType: String,
    @SerializedName("ctt_s")
    val ctStart: String?,
    @SerializedName("ctt_t")
    val ctType: String?,
    @SerializedName("_data")
    val `data`: String?,
    @SerializedName("fn")
    val filename: String?,
    @SerializedName("_id")
    val id: Long,
    @SerializedName("mid")
    val messageId: Long,
    @SerializedName("name")
    val name: String,
    @SerializedName("seq")
    val sequenceOrder: Int,
    @SerializedName("text")
    val text: String?,
    @SerializedName("mms_content")
    val mmsContent: String?,
) {

    fun toContentValues(): ContentValues {
        return contentValuesOf(
            Telephony.Mms.Part.CONTENT_DISPOSITION to contentDisposition,
            Telephony.Mms.Part.CHARSET to charset,
            Telephony.Mms.Part.CONTENT_ID to contentId,
            Telephony.Mms.Part.CONTENT_LOCATION to contentLocation,
            Telephony.Mms.Part.CONTENT_TYPE to contentType,
            Telephony.Mms.Part.CT_START to ctStart,
            Telephony.Mms.Part.CT_TYPE to ctType,
            Telephony.Mms.Part.FILENAME to filename,
            Telephony.Mms.Part.NAME to name,
            Telephony.Mms.Part.SEQ to sequenceOrder,
            Telephony.Mms.Part.TEXT to text,
        )
    }

    fun isNonText(): Boolean {
        return !(text != null || contentType.lowercase().startsWith("text") || contentType.lowercase() == "application/smil")
    }
}
