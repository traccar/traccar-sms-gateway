package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.simplemobiletools.commons.extensions.normalizePhoneNumber
import com.simplemobiletools.commons.extensions.sendEmailIntent
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.VCardViewerAdapter
import com.simplemobiletools.smsmessenger.databinding.ActivityVcardViewerBinding
import com.simplemobiletools.smsmessenger.extensions.dialNumber
import com.simplemobiletools.smsmessenger.helpers.EXTRA_VCARD_URI
import com.simplemobiletools.smsmessenger.helpers.parseVCardFromUri
import com.simplemobiletools.smsmessenger.models.VCardPropertyWrapper
import com.simplemobiletools.smsmessenger.models.VCardWrapper
import ezvcard.VCard
import ezvcard.property.Email
import ezvcard.property.Telephone

class VCardViewerActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityVcardViewerBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.vcardViewerCoordinator, binding.contactsList, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.contactsList, binding.vcardToolbar)

        val vCardUri = intent.getParcelableExtra(EXTRA_VCARD_URI) as? Uri
        if (vCardUri != null) {
            setupOptionsMenu(vCardUri)
            parseVCardFromUri(this, vCardUri) {
                runOnUiThread {
                    setupContactsList(it)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.vcardToolbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu(vCardUri: Uri) {
        binding.vcardToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_contact -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val mimetype = contentResolver.getType(vCardUri)
                        setDataAndType(vCardUri, mimetype?.lowercase())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }

                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupContactsList(vCards: List<VCard>) {
        val items = prepareData(vCards)
        val adapter = VCardViewerAdapter(this, items.toMutableList()) { item ->
            val property = item as? VCardPropertyWrapper
            if (property != null) {
                handleClick(item)
            }
        }
        binding.contactsList.adapter = adapter
    }

    private fun handleClick(property: VCardPropertyWrapper) {
        when (property.property) {
            is Telephone -> dialNumber(property.value.normalizePhoneNumber())
            is Email -> sendEmailIntent(property.value)
        }
    }

    private fun prepareData(vCards: List<VCard>): List<VCardWrapper> {
        return vCards.map { vCard -> VCardWrapper.from(this, vCard) }
    }
}
