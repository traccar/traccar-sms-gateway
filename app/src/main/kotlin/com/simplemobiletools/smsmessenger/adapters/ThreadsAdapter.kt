package com.simplemobiletools.smsmessenger.adapters

import android.graphics.Typeface
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.bumptech.glide.Glide
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.formatDateOrTime
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.deleteThread
import com.simplemobiletools.smsmessenger.extensions.loadImage
import com.simplemobiletools.smsmessenger.helpers.refreshMessages
import com.simplemobiletools.smsmessenger.models.Message
import kotlinx.android.synthetic.main.item_thread.view.*

class ThreadsAdapter(
    activity: SimpleActivity, var threads: ArrayList<Message>,
    recyclerView: MyRecyclerView,
    fastScroller: FastScroller,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_threads

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_select_all -> selectAll()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = threads.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = threads.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = threads.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_thread, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = threads[position]
        holder.bindView(message, true, true) { itemView, layoutPosition ->
            setupView(itemView, message)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = threads.size

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteThreads()
            }
        }
    }

    private fun deleteThreads() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val threadsToRemove = threads.filter { selectedKeys.contains(it.id) } as ArrayList<Message>
        val positions = getSelectedItemPositions()
        threadsToRemove.forEach {
            activity.deleteThread(it.thread)
        }
        threads.removeAll(threadsToRemove)

        activity.runOnUiThread {
            if (threadsToRemove.isEmpty()) {
                refreshMessages()
                finishActMode()
            } else {
                removeSelectedItems(positions)
            }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            Glide.with(activity).clear(holder.itemView.thread_image)
        }
    }

    private fun setupView(view: View, message: Message) {
        view.apply {
            thread_frame.isSelected = selectedKeys.contains(message.id)

            thread_address.text = message.getThreadTitle()
            thread_body_short.text = message.body
            thread_date.text = message.date.formatDateOrTime(context, true)

            if (message.read) {
                thread_address.setTypeface(null, Typeface.NORMAL)
                thread_body_short.setTypeface(null, Typeface.NORMAL)
                thread_body_short.alpha = 0.7f
            } else {
                thread_address.setTypeface(null, Typeface.BOLD)
                thread_body_short.setTypeface(null, Typeface.BOLD)
                thread_body_short.alpha = 1f
            }

            arrayListOf<TextView>(thread_address, thread_body_short, thread_date).forEach {
                it.setTextColor(textColor)
            }

            val participant = message.participants.first()
            context.loadImage(participant.photoUri, thread_image, participant.name)
        }
    }
}
