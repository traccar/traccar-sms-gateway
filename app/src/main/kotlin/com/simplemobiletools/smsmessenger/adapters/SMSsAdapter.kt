package com.simplemobiletools.smsmessenger.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.models.SMS
import kotlinx.android.synthetic.main.item_sms.view.*

class SMSsAdapter(
    activity: SimpleActivity, var SMSs: ArrayList<SMS>, recyclerView: MyRecyclerView, fastScroller: FastScroller, itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_smss

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = SMSs.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = SMSs.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = SMSs.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_sms, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sms = SMSs[position]
        holder.bindView(sms, true, true) { itemView, layoutPosition ->
            setupView(itemView, sms)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = SMSs.size

    private fun getItemWithKey(key: Int): SMS? = SMSs.firstOrNull { it.id == key }

    private fun getSelectedItems() = SMSs.filter { selectedKeys.contains(it.id) } as ArrayList<SMS>

    private fun setupView(view: View, sms: SMS) {
        view.apply {
            view.sms_address.text = sms.address
            view.sms_message_short.text = sms.body
        }
    }
}
