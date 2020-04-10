package com.simplemobiletools.smsmessenger.models

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.simplemobiletools.smsmessenger.extensions.loadImage

data class Contact(
    val id: Int,
    var name: String,
    var photoUri: String,
    var phoneNumber: String,
    var isOrganization: Boolean
) {
    fun updateImage(context: Context, imageView: ImageView, placeholder: Drawable) {
        if (photoUri.isEmpty()) {
            imageView.setImageDrawable(placeholder)
        } else {
            context.loadImage(photoUri, imageView, placeholder)
        }
    }
}
