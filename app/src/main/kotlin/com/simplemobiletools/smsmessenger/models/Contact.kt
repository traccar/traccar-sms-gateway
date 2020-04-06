package com.simplemobiletools.smsmessenger.models

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions

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
            val options = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .error(placeholder)
                .centerCrop()

            Glide.with(context)
                .load(photoUri)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(placeholder)
                .apply(options)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView)
        }
    }
}
