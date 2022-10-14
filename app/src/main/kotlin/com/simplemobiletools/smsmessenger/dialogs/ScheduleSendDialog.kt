package com.simplemobiletools.smsmessenger.dialogs

import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.extensions.roundToClosestMultipleOf
import kotlinx.android.synthetic.main.schedule_message_dialog.view.*
import org.joda.time.DateTime
import java.util.*

class ScheduleSendDialog(private val activity: BaseSimpleActivity, private var dateTime: DateTime? = null, private val callback: (dt: DateTime?) -> Unit) {
    private val view = activity.layoutInflater.inflate(R.layout.schedule_message_dialog, null)
    private val textColor = activity.getProperTextColor()

    private var previewDialog: AlertDialog? = null
    private var previewShown = false
    private var isNewMessage = dateTime == null

    private val calendar = Calendar.getInstance()

    init {
        arrayOf(view.subtitle, view.edit_time, view.edit_date).forEach { it.setTextColor(textColor) }
        arrayOf(view.dateIcon, view.timeIcon).forEach { it.applyColorFilter(textColor) }
        view.edit_date.setOnClickListener { showDatePicker() }
        view.edit_time.setOnClickListener { showTimePicker() }
        updateTexts(dateTime ?: DateTime.now().plusHours(1))

        if (isNewMessage) {
            showDatePicker()
        } else {
            showPreview()
        }
    }

    private fun updateTexts(dt: DateTime) {
        val dateFormat = activity.config.dateFormat
        val timeFormat = activity.getTimeFormat()
        view.edit_date.text = dt.toString(dateFormat)
        view.edit_time.text = dt.toString(timeFormat)
    }

    private fun showPreview() {
        if (previewShown) return
        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                previewShown = true
                activity.setupDialogStuff(view, this, R.string.schedule_send) { dialog ->
                    previewDialog = dialog
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (validateDateTime()) {
                            callback(dateTime)
                            dialog.dismiss()
                        }
                    }
                    dialog.setOnDismissListener {
                        previewShown = false
                        previewDialog = null
                    }
                }
            }
    }

    private fun showDatePicker() {
        val year = dateTime?.year ?: calendar.get(Calendar.YEAR)
        val monthOfYear = dateTime?.monthOfYear?.minus(1) ?: calendar.get(Calendar.MONTH)
        val dayOfMonth = dateTime?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

        val dateSetListener = OnDateSetListener { _, y, m, d -> dateSet(y, m, d) }
        DatePickerDialog(
            activity, activity.getDatePickerDialogTheme(), dateSetListener, year, monthOfYear, dayOfMonth
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
            getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                text = activity.getString(R.string.back)
                setOnClickListener {
                    showPreview()
                    dismiss()
                }
            }
        }
    }

    private fun showTimePicker() {
        val hourOfDay = dateTime?.hourOfDay ?: getNextHour()
        val minute = dateTime?.minuteOfHour ?: getNextMinute()

        val timeSetListener = OnTimeSetListener { _, h, m -> timeSet(h, m) }
        TimePickerDialog(
            activity, activity.getDatePickerDialogTheme(), timeSetListener, hourOfDay, minute, DateFormat.is24HourFormat(activity)
        ).apply {
            show()
            getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                text = activity.getString(R.string.back)
                setOnClickListener {
                    showPreview()
                    dismiss()
                }
            }
        }
    }

    private fun dateSet(year: Int, monthOfYear: Int, dayOfMonth: Int) {
        if (isNewMessage) {
            showTimePicker()
        }

        dateTime = DateTime.now()
            .withDate(year, monthOfYear + 1, dayOfMonth)
            .run {
                if (dateTime != null) {
                    withTime(dateTime!!.hourOfDay, dateTime!!.minuteOfHour, 0, 0)
                } else {
                    withTime(getNextHour(), getNextMinute(), 0, 0)
                }
            }
        if (!isNewMessage) {
            validateDateTime()
        }
        isNewMessage = false
        updateTexts(dateTime!!)
    }

    private fun timeSet(hourOfDay: Int, minute: Int) {
        dateTime = dateTime?.withHourOfDay(hourOfDay)?.withMinuteOfHour(minute)
        if (validateDateTime()) {
            updateTexts(dateTime!!)
            showPreview()
        } else {
            showTimePicker()
        }
    }

    private fun validateDateTime(): Boolean {
        return if (dateTime?.isAfterNow == false) {
            activity.toast(R.string.must_pick_time_in_the_future)
            false
        } else {
            true
        }
    }

    private fun getNextHour() = (calendar.get(Calendar.HOUR_OF_DAY) + 1).coerceIn(0, 23)

    private fun getNextMinute() = (calendar.get(Calendar.MINUTE) + 5).roundToClosestMultipleOf(5).coerceIn(0, 59)
}
