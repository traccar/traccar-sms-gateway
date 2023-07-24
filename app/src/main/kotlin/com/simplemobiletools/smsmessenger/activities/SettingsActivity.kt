package com.simplemobiletools.smsmessenger.activities

import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.simplemobiletools.commons.activities.ManageBlockedNumbersActivity
import com.simplemobiletools.commons.dialogs.*
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.dialogs.ExportMessagesDialog
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.emptyMessagesRecycleBin
import com.simplemobiletools.smsmessenger.extensions.messagesDB
import com.simplemobiletools.smsmessenger.helpers.*
import com.simplemobiletools.smsmessenger.models.*
import kotlinx.android.synthetic.main.activity_settings.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private var blockedNumbersAtPause = -1
    private var recycleBinMessages = 0
    private val messagesFileType = "application/json"
    private val messageImportFileTypes = listOf("application/json", "application/xml", "text/xml")

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateMaterialActivityViews(settings_coordinator, settings_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(settings_nested_scrollview, settings_toolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupCustomizeNotifications()
        setupUseEnglish()
        setupLanguage()
        setupManageBlockedNumbers()
        setupManageBlockedKeywords()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupShowCharacterCounter()
        setupUseSimpleCharacters()
        setupSendOnEnter()
        setupEnableDeliveryReports()
        setupSendLongMessageAsMMS()
        setupGroupMessageAsMMS()
        setupLockScreenVisibility()
        setupMMSFileSizeLimit()
        setupUseRecycleBin()
        setupEmptyRecycleBin()
        setupAppPasswordProtection()
        setupMessagesExport()
        setupMessagesImport()
        updateTextColors(settings_nested_scrollview)

        if (blockedNumbersAtPause != -1 && blockedNumbersAtPause != getBlockedNumbers().hashCode()) {
            refreshMessages()
        }

        arrayOf(
            settings_color_customization_section_label,
            settings_general_settings_label,
            settings_outgoing_messages_label,
            settings_notifications_label,
            settings_recycle_bin_label,
            settings_security_label,
            settings_migrating_label
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            MessagesImporter(this).importMessages(uri)
        }
    }

    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(messagesFileType)) { uri ->
        if (uri != null) {
            toast(R.string.exporting)
            exportMessages(uri)
        }
    }

    private fun setupMessagesExport() {
        settings_export_messages_holder.setOnClickListener {
            ExportMessagesDialog(this) { fileName ->
                saveDocument.launch(fileName)
            }
        }
    }

    private fun setupMessagesImport() {
        settings_import_messages_holder.setOnClickListener {
            getContent.launch(messageImportFileTypes.toTypedArray())
        }
    }

    private fun exportMessages(uri: Uri) {
        ensureBackgroundThread {
            try {
                MessagesReader(this).getMessagesToExport(config.exportSms, config.exportMms) { messagesToExport ->
                    if (messagesToExport.isEmpty()) {
                        toast(R.string.no_entries_for_exporting)
                        return@getMessagesToExport
                    }
                    val json = Json { encodeDefaults = true }
                    val jsonString = json.encodeToString(messagesToExport)
                    val outputStream = contentResolver.openOutputStream(uri)!!

                    outputStream.use {
                        it.write(jsonString.toByteArray())
                    }
                    toast(R.string.exporting_successful)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        blockedNumbersAtPause = getBlockedNumbers().hashCode()
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_color_customization_label.text = getCustomizeColorsString()
        settings_color_customization_holder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupCustomizeNotifications() {
        settings_customize_notifications_holder.beVisibleIf(isOreoPlus())
        settings_customize_notifications_holder.setOnClickListener {
            launchCustomizeNotificationsIntent()
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())
        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    // support for device-wise blocking came on Android 7, rely only on that
    @TargetApi(Build.VERSION_CODES.N)
    private fun setupManageBlockedNumbers() {
        settings_manage_blocked_numbers.text = addLockedLabelIfNeeded(R.string.manage_blocked_numbers)
        settings_manage_blocked_numbers_holder.beVisibleIf(isNougatPlus())

        settings_manage_blocked_numbers_holder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                Intent(this, ManageBlockedNumbersActivity::class.java).apply {
                    startActivity(this)
                }
            } else {
                FeatureLockedDialog(this) { }
            }
        }
    }

    private fun setupManageBlockedKeywords() {
        settings_manage_blocked_keywords.text = addLockedLabelIfNeeded(R.string.manage_blocked_keywords)

        settings_manage_blocked_keywords_holder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                Intent(this, ManageBlockedKeywordsActivity::class.java).apply {
                    startActivity(this)
                }
            } else {
                FeatureLockedDialog(this) { }
            }
        }
    }

    private fun setupChangeDateTimeFormat() {
        settings_change_date_time_format_holder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {
                refreshMessages()
            }
        }
    }

    private fun setupFontSize() {
        settings_font_size.text = getFontSizeText()
        settings_font_size_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settings_font_size.text = getFontSizeText()
            }
        }
    }

    private fun setupShowCharacterCounter() {
        settings_show_character_counter.isChecked = config.showCharacterCounter
        settings_show_character_counter_holder.setOnClickListener {
            settings_show_character_counter.toggle()
            config.showCharacterCounter = settings_show_character_counter.isChecked
        }
    }

    private fun setupUseSimpleCharacters() {
        settings_use_simple_characters.isChecked = config.useSimpleCharacters
        settings_use_simple_characters_holder.setOnClickListener {
            settings_use_simple_characters.toggle()
            config.useSimpleCharacters = settings_use_simple_characters.isChecked
        }
    }

    private fun setupSendOnEnter() {
        settings_send_on_enter.isChecked = config.sendOnEnter
        settings_send_on_enter_holder.setOnClickListener {
            settings_send_on_enter.toggle()
            config.sendOnEnter = settings_send_on_enter.isChecked
        }
    }

    private fun setupEnableDeliveryReports() {
        settings_enable_delivery_reports.isChecked = config.enableDeliveryReports
        settings_enable_delivery_reports_holder.setOnClickListener {
            settings_enable_delivery_reports.toggle()
            config.enableDeliveryReports = settings_enable_delivery_reports.isChecked
        }
    }

    private fun setupSendLongMessageAsMMS() {
        settings_send_long_message_mms.isChecked = config.sendLongMessageMMS
        settings_send_long_message_mms_holder.setOnClickListener {
            settings_send_long_message_mms.toggle()
            config.sendLongMessageMMS = settings_send_long_message_mms.isChecked
        }
    }

    private fun setupGroupMessageAsMMS() {
        settings_send_group_message_mms.isChecked = config.sendGroupMessageMMS
        settings_send_group_message_mms_holder.setOnClickListener {
            settings_send_group_message_mms.toggle()
            config.sendGroupMessageMMS = settings_send_group_message_mms.isChecked
        }
    }

    private fun setupLockScreenVisibility() {
        settings_lock_screen_visibility.text = getLockScreenVisibilityText()
        settings_lock_screen_visibility_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(LOCK_SCREEN_SENDER_MESSAGE, getString(R.string.sender_and_message)),
                RadioItem(LOCK_SCREEN_SENDER, getString(R.string.sender_only)),
                RadioItem(LOCK_SCREEN_NOTHING, getString(R.string.nothing)),
            )

            RadioGroupDialog(this@SettingsActivity, items, config.lockScreenVisibilitySetting) {
                config.lockScreenVisibilitySetting = it as Int
                settings_lock_screen_visibility.text = getLockScreenVisibilityText()
            }
        }
    }

    private fun getLockScreenVisibilityText() = getString(
        when (config.lockScreenVisibilitySetting) {
            LOCK_SCREEN_SENDER_MESSAGE -> R.string.sender_and_message
            LOCK_SCREEN_SENDER -> R.string.sender_only
            else -> R.string.nothing
        }
    )

    private fun setupMMSFileSizeLimit() {
        settings_mms_file_size_limit.text = getMMSFileLimitText()
        settings_mms_file_size_limit_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(7, getString(R.string.mms_file_size_limit_none), FILE_SIZE_NONE),
                RadioItem(6, getString(R.string.mms_file_size_limit_2mb), FILE_SIZE_2_MB),
                RadioItem(5, getString(R.string.mms_file_size_limit_1mb), FILE_SIZE_1_MB),
                RadioItem(4, getString(R.string.mms_file_size_limit_600kb), FILE_SIZE_600_KB),
                RadioItem(3, getString(R.string.mms_file_size_limit_300kb), FILE_SIZE_300_KB),
                RadioItem(2, getString(R.string.mms_file_size_limit_200kb), FILE_SIZE_200_KB),
                RadioItem(1, getString(R.string.mms_file_size_limit_100kb), FILE_SIZE_100_KB),
            )

            val checkedItemId = items.find { it.value == config.mmsFileSizeLimit }?.id ?: 7
            RadioGroupDialog(this@SettingsActivity, items, checkedItemId) {
                config.mmsFileSizeLimit = it as Long
                settings_mms_file_size_limit.text = getMMSFileLimitText()
            }
        }
    }

    private fun setupUseRecycleBin() {
        updateRecycleBinButtons()
        settings_use_recycle_bin.isChecked = config.useRecycleBin
        settings_use_recycle_bin_holder.setOnClickListener {
            settings_use_recycle_bin.toggle()
            config.useRecycleBin = settings_use_recycle_bin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun updateRecycleBinButtons() {
        settings_empty_recycle_bin_holder.beVisibleIf(config.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() {
        ensureBackgroundThread {
            recycleBinMessages = messagesDB.getArchivedCount()
            runOnUiThread {
                settings_empty_recycle_bin_size.text =
                    resources.getQuantityString(R.plurals.delete_messages, recycleBinMessages, recycleBinMessages)
            }
        }

        settings_empty_recycle_bin_holder.setOnClickListener {
            if (recycleBinMessages == 0) {
                toast(R.string.recycle_bin_empty)
            } else {
                ConfirmationDialog(this, "", R.string.empty_recycle_bin_messages_confirmation, R.string.yes, R.string.no) {
                    ensureBackgroundThread {
                        emptyMessagesRecycleBin()
                    }
                    recycleBinMessages = 0
                    settings_empty_recycle_bin_size.text =
                        resources.getQuantityString(R.plurals.delete_messages, recycleBinMessages, recycleBinMessages)
                }
            }
        }
    }

    private fun setupAppPasswordProtection() {
        settings_app_password_protection.isChecked = config.isAppPasswordProtectionOn
        settings_app_password_protection_holder.setOnClickListener {
            val tabToShow = if (config.isAppPasswordProtectionOn) config.appProtectionType else SHOW_ALL_TABS
            SecurityDialog(this, config.appPasswordHash, tabToShow) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isAppPasswordProtectionOn
                    settings_app_password_protection.isChecked = !hasPasswordProtection
                    config.isAppPasswordProtectionOn = !hasPasswordProtection
                    config.appPasswordHash = if (hasPasswordProtection) "" else hash
                    config.appProtectionType = type

                    if (config.isAppPasswordProtectionOn) {
                        val confirmationTextId = if (config.appProtectionType == PROTECTION_FINGERPRINT)
                            R.string.fingerprint_setup_successfully else R.string.protection_setup_successfully
                        ConfirmationDialog(this, "", confirmationTextId, R.string.ok, 0) { }
                    }
                }
            }
        }
    }

    private fun getMMSFileLimitText() = getString(
        when (config.mmsFileSizeLimit) {
            FILE_SIZE_100_KB -> R.string.mms_file_size_limit_100kb
            FILE_SIZE_200_KB -> R.string.mms_file_size_limit_200kb
            FILE_SIZE_300_KB -> R.string.mms_file_size_limit_300kb
            FILE_SIZE_600_KB -> R.string.mms_file_size_limit_600kb
            FILE_SIZE_1_MB -> R.string.mms_file_size_limit_1mb
            FILE_SIZE_2_MB -> R.string.mms_file_size_limit_2mb
            else -> R.string.mms_file_size_limit_none
        }
    )
}
