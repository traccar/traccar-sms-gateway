package com.simplemobiletools.smsmessenger.activities

import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.Telephony
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_READ_SMS
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.smsmessenger.BuildConfig
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.SMSsAdapter
import com.simplemobiletools.smsmessenger.models.SMS
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        if (checkAppSideloading()) {
            return
        }

        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                initMessenger()
            } else {
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initMessenger() {
        val smss = getSMSs()
        SMSsAdapter(this, smss, smss_list, smsss_fastscroller) {

        }.apply {
            smss_list.adapter = this
        }
    }

    private fun getSMSs(): ArrayList<SMS> {
        val smss = ArrayList<SMS>()
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.SUBJECT,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(Telephony.Sms._ID)
                    val subject = cursor.getStringValue(Telephony.Sms.SUBJECT) ?: ""
                    val body = cursor.getStringValue(Telephony.Sms.BODY)
                    val type = cursor.getIntValue(Telephony.Sms.TYPE)
                    val address = cursor.getStringValue(Telephony.Sms.ADDRESS)
                    val date = (cursor.getLongValue(Telephony.Sms.DATE) / 1000).toInt()
                    val read = cursor.getIntValue(Telephony.Sms.READ) == 1
                    val sms = SMS(id, subject, body, type, address, date, read)
                    smss.add(sms)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            showErrorToast(e)
        } finally {
            cursor?.close()
        }
        return smss
    }

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = 0

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons)
        )

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }
}
