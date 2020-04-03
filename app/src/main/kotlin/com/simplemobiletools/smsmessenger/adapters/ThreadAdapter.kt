package com.simplemobiletools.smsmessenger.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.models.Message
import kotlinx.android.synthetic.main.item_thread_message.view.*

class ThreadAdapter(
    activity: SimpleActivity, var messages: ArrayList<Message>,
    recyclerView: MyRecyclerView,
    fastScroller: FastScroller,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_messages

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = messages.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = messages.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = messages.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_thread_message, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.bindView(message, true, true) { itemView, layoutPosition ->
            setupView(itemView, message)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = messages.size

    private fun getItemWithKey(key: Int): Message? = messages.firstOrNull { it.id == key }

    private fun getSelectedItems() = messages.filter { selectedKeys.contains(it.id) } as ArrayList<Message>

    private fun setupView(view: View, message: Message) {
        view.apply {
            thread_message_body.text = message.body
            thread_message_body.setTextColor(textColor)
        }
    }
}
