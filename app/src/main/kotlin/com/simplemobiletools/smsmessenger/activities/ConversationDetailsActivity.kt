package com.simplemobiletools.smsmessenger.activities

import android.os.Bundle
import androidx.core.content.res.ResourcesCompat
import com.simplemobiletools.commons.extensions.adjustAlpha
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.helpers.HIGHER_ALPHA
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.dialogs.RenameConversationDialog
import com.simplemobiletools.smsmessenger.extensions.*
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.models.Conversation
import kotlinx.android.synthetic.main.activity_conversation_details.*

class ConversationDetailsActivity : SimpleActivity() {

    private var threadId: Long = 0L
    private var conversation: Conversation? = null
    private lateinit var participants: ArrayList<SimpleContact>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_details)

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
            participants = getThreadParticipants(threadId, null)
            runOnUiThread {
                setupTextViews()
                setupParticipants()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(conversation_details_toolbar, NavigationIcon.Arrow)
    }

    private fun setupTextViews() {
        val textColor = getProperTextColor()
        val headingColor = textColor.adjustAlpha(HIGHER_ALPHA)

        members_heading.setTextColor(headingColor)
        conversation_name_heading.setTextColor(headingColor)
        conversation_name.apply {
            setTextColor(textColor)
            ResourcesCompat.getDrawable(resources, R.drawable.ic_edit_vector, theme)?.apply {
                applyColorFilter(textColor)
                setCompoundDrawablesWithIntrinsicBounds(null, null, this, null)
            }

            text = conversation?.title
            setOnClickListener {
                RenameConversationDialog(this@ConversationDetailsActivity, conversation!!) { title ->
                    text = title
                    ensureBackgroundThread {
                        conversation = renameConversation(conversation!!, newTitle = title)
                    }
                }
            }
        }
    }

    private fun setupParticipants() {
        val adapter = ContactsAdapter(this, participants, participants_recyclerview) {
            val contact = it as SimpleContact
            val address = contact.phoneNumbers.first().normalizedNumber
            getContactFromAddress(address) { simpleContact ->
                if (simpleContact != null) {
                    startContactDetailsIntent(simpleContact)
                }
            }
        }
        participants_recyclerview.adapter = adapter
    }
}
