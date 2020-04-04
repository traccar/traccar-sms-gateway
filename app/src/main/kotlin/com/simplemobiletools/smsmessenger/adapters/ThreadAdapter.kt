package com.simplemobiletools.smsmessenger.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.helpers.THREAD_DATE_TIME
import com.simplemobiletools.smsmessenger.helpers.THREAD_RECEIVED_MESSAGE
import com.simplemobiletools.smsmessenger.helpers.THREAD_SENT_MESSAGE
import com.simplemobiletools.smsmessenger.models.Message
import com.simplemobiletools.smsmessenger.models.ThreadDateTime
import com.simplemobiletools.smsmessenger.models.ThreadItem
import kotlinx.android.synthetic.main.item_received_message.view.*
import kotlinx.android.synthetic.main.item_thread_date_time.view.*

class ThreadAdapter(
    activity: SimpleActivity, var messages: ArrayList<ThreadItem>,
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

    override fun getSelectableItemCount() = messages.filter { it is Message }.size

    override fun getIsItemSelectable(position: Int) = !isThreadDateTime(position)

    override fun getItemSelectionKey(position: Int) = (messages.getOrNull(position) as? Message)?.id

    override fun getItemKeyPosition(key: Int) = messages.indexOfFirst { (it as? Message)?.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            THREAD_DATE_TIME -> R.layout.item_thread_date_time
            THREAD_RECEIVED_MESSAGE -> R.layout.item_received_message
            else -> R.layout.item_sent_message
        }
        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = messages[position]
        holder.bindView(item, true, item is Message) { itemView, layoutPosition ->
            if (item is ThreadDateTime) {
                setupDateTime(itemView, item)
            } else {
                setupView(itemView, item as Message)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        val item = messages[position]
        return when {
            item is ThreadDateTime -> THREAD_DATE_TIME
            (messages[position] as? Message)?.isReceivedMessage() == true -> THREAD_RECEIVED_MESSAGE
            else -> THREAD_SENT_MESSAGE
        }
    }

    private fun isThreadDateTime(position: Int) = messages.getOrNull(position) is ThreadDateTime

    private fun setupView(view: View, message: Message) {
        view.apply {
            thread_message_body.text = message.body

            if (message.isReceivedMessage()) {
                thread_message_body.setTextColor(textColor)
            } else {
                val background = context.getAdjustedPrimaryColor()
                thread_message_wrapper.background.applyColorFilter(background.adjustAlpha(0.8f))
                thread_message_body.setTextColor(background.getContrastColor())
            }
        }
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        view.apply {
            thread_date_time.text = dateTime.date.formatDateOrTime(context, false)
            thread_date_time.setTextColor(textColor)
        }
    }
}
