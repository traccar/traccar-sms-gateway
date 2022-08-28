package com.simplemobiletools.smsmessenger.helpers

import android.content.Context
import android.net.Uri
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import ezvcard.Ezvcard
import ezvcard.VCard

fun parseVCardFromUri(context: Context, uri: Uri, callback: (vCards: List<VCard>) -> Unit) {
    ensureBackgroundThread {
        val inputStream = context.contentResolver.openInputStream(uri)
        val vCards = Ezvcard.parse(inputStream).all()
        callback(vCards)
    }
}
