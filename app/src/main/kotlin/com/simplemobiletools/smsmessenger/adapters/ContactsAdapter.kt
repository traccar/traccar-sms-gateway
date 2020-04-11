package com.simplemobiletools.smsmessenger.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.loadImage
import com.simplemobiletools.smsmessenger.models.Contact
import kotlinx.android.synthetic.main.item_contact_with_number.view.*
import java.util.*

class ContactsAdapter(
    activity: SimpleActivity, var contacts: ArrayList<Contact>, recyclerView: MyRecyclerView,
    fastScroller: FastScroller?, itemClick: (Any) -> Unit
) :
    MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

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

            context.loadImage(contact.photoUri, contact_tmb, contact.name)
        }
    }
}
