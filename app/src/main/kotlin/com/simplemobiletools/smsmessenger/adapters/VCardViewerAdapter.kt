package com.simplemobiletools.smsmessenger.adapters

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.SimpleContactsHelper
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.models.VCardPropertyWrapper
import com.simplemobiletools.smsmessenger.models.VCardWrapper
import kotlinx.android.synthetic.main.item_vcard_contact.view.*
import kotlinx.android.synthetic.main.item_vcard_contact_property.view.*

class VCardViewerAdapter(
    activity: SimpleActivity, private var items: MutableList<Any>, private val itemClick: (Any) -> Unit
) : RecyclerView.Adapter<VCardViewerAdapter.VCardViewHolder>() {

    private var fontSize = activity.getTextSize()
    private var textColor = activity.getProperTextColor()
    private val layoutInflater = activity.layoutInflater

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is VCardWrapper -> R.layout.item_vcard_contact
            is VCardPropertyWrapper -> R.layout.item_vcard_contact_property
            else -> throw IllegalArgumentException("Unexpected type: ${item::class.simpleName}")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VCardViewHolder {
        val view = layoutInflater.inflate(viewType, parent, false)
        return VCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: VCardViewerAdapter.VCardViewHolder, position: Int) {
        val item = items[position]
        val itemView = holder.bindView()
        when (item) {
            is VCardWrapper -> setupVCardView(itemView, item)
            is VCardPropertyWrapper -> setupVCardPropertyView(itemView, item)
            else -> throw IllegalArgumentException("Unexpected type: ${item::class.simpleName}")
        }
    }

    private fun setupVCardView(view: View, item: VCardWrapper) {
        val name = item.fullName
        view.apply {
            item_contact_name.apply {
                text = name
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.1f)
            }
            item_contact_image.apply {
                val photo = item.vCard.photos.firstOrNull()
                val placeholder = if (name != null) {
                    SimpleContactsHelper(context).getContactLetterIcon(name).toDrawable(resources)
                } else {
                    null
                }
                val roundingRadius = resources.getDimensionPixelSize(R.dimen.big_margin)
                val transformation = RoundedCorners(roundingRadius)
                val options = RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .placeholder(placeholder)
                    .transform(transformation)
                Glide.with(this)
                    .load(photo?.data ?: photo?.url)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(this)
            }
            expand_collapse_icon.apply {
                val expandCollapseDrawable = if (item.expanded) {
                    R.drawable.ic_collapse_up
                } else {
                    R.drawable.ic_expand_down
                }
                setImageResource(expandCollapseDrawable)
                applyColorFilter(textColor)
            }

            if (items.size > 1) {
                setOnClickListener {
                    expandOrCollapseRow(view, item)
                }
            }
            onGlobalLayout {
                if (items.size == 1) {
                    expandOrCollapseRow(view, item)
                    view.expand_collapse_icon.beGone()
                }
            }
        }
    }

    private fun setupVCardPropertyView(view: View, property: VCardPropertyWrapper) {
        view.apply {
            item_vcard_property_title.apply {
                text = property.value
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.1f)
            }
            item_vcard_property_subtitle.apply {
                text = property.type
                setTextColor(textColor)
            }
            view.setOnClickListener {
                itemClick(property)
            }
        }
    }

    private fun expandOrCollapseRow(view: View, item: VCardWrapper) {
        val properties = item.properties
        if (item.expanded) {
            collapseRow(view, properties, item)
        } else {
            expandRow(view, properties, item)
        }
    }

    private fun expandRow(view: View, properties: List<VCardPropertyWrapper>, vCardWrapper: VCardWrapper) {
        vCardWrapper.expanded = true
        val nextPosition = items.indexOf(vCardWrapper) + 1
        items.addAll(nextPosition, properties)
        notifyItemRangeInserted(nextPosition, properties.size)
        view.expand_collapse_icon.setImageResource(R.drawable.ic_collapse_up)
    }

    private fun collapseRow(view: View, properties: List<VCardPropertyWrapper>, vCardWrapper: VCardWrapper) {
        vCardWrapper.expanded = false
        val nextPosition = items.indexOf(vCardWrapper) + 1
        repeat(properties.size) {
            items.removeAt(nextPosition)
        }
        notifyItemRangeRemoved(nextPosition, properties.size)
        view.expand_collapse_icon.setImageResource(R.drawable.ic_expand_down)
    }

    inner class VCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView() = itemView
    }
}
