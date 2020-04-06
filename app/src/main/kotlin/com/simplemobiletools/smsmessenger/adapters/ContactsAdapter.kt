package com.simplemobiletools.smsmessenger.adapters

import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.models.Contact
import kotlinx.android.synthetic.main.item_contact_with_number.view.*
import java.util.*

class ContactsAdapter(
    activity: SimpleActivity, var contacts: ArrayList<Contact>, recyclerView: MyRecyclerView,
    fastScroller: FastScroller?, itemClick: (Any) -> Unit
) :
    MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    private lateinit var contactDrawable: Drawable
    private lateinit var businessContactDrawable: Drawable

    init {
        initDrawables()
    }

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = contacts.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = contacts.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = contacts.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_contact_with_number, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bindView(contact, true, false) { itemView, layoutPosition ->
            setupView(itemView, contact)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = contacts.size

    private fun initDrawables() {
        contactDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.ic_person_vector, textColor)
        businessContactDrawable = activity.resources.getColoredDrawableWithColor(R.drawable.ic_business_vector, textColor)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.contact_tmb)
        }
    }

    private fun setupView(view: View, contact: Contact) {
        view.apply {
            contact_name.text = contact.name
            contact_name.setTextColor(textColor)

            contact_number.text = contact.phoneNumber
            contact_number.setTextColor(textColor)

            val placeholder = if (contact.isOrganization) {
                businessContactDrawable
            } else {
                contactDrawable
            }

            contact.updateImage(context, contact_tmb, placeholder)
        }
    }
}
