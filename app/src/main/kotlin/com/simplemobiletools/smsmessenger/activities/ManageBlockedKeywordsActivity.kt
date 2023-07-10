package com.simplemobiletools.smsmessenger.activities

import android.os.Bundle
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.underlineText
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.helpers.APP_ICON_IDS
import com.simplemobiletools.commons.helpers.APP_LAUNCHER_NAME
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.dialogs.AddBlockedKeywordDialog
import com.simplemobiletools.smsmessenger.dialogs.ManageBlockedKeywordsAdapter
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.toArrayList
import kotlinx.android.synthetic.main.activity_manage_blocked_keywords.*

class ManageBlockedKeywordsActivity : BaseSimpleActivity(), RefreshRecyclerViewListener {
    override fun getAppIconIDs() = intent.getIntegerArrayListExtra(APP_ICON_IDS) ?: ArrayList()

    override fun getAppLauncherName() = intent.getStringExtra(APP_LAUNCHER_NAME) ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_blocked_keywords)
        updateBlockedKeywords()
        setupOptionsMenu()

        updateMaterialActivityViews(block_keywords_coordinator, manage_blocked_keywords_list, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(manage_blocked_keywords_list, block_keywords_toolbar)
        updateTextColors(manage_blocked_keywords_wrapper)

        manage_blocked_keywords_placeholder_2.apply {
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener {
                addOrEditBlockedKeyword()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(block_keywords_toolbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        block_keywords_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_blocked_keyword -> {
                    addOrEditBlockedKeyword()
                    true
                }

                else -> false
            }
        }
    }

    override fun refreshItems() {
        updateBlockedKeywords()
    }

    private fun updateBlockedKeywords() {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords
            runOnUiThread {
                ManageBlockedKeywordsAdapter(this, blockedKeywords.toArrayList(), this, manage_blocked_keywords_list) {
                    addOrEditBlockedKeyword(it as String)
                }.apply {
                    manage_blocked_keywords_list.adapter = this
                }

                manage_blocked_keywords_placeholder.beVisibleIf(blockedKeywords.isEmpty())
                manage_blocked_keywords_placeholder_2.beVisibleIf(blockedKeywords.isEmpty())
            }
        }
    }

    private fun addOrEditBlockedKeyword(keyword: String? = null) {
        AddBlockedKeywordDialog(this, keyword) {
            updateBlockedKeywords()
        }
    }
}
