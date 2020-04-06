package com.simplemobiletools.smsmessenger.adapters

import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.normalizeString
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.config
import com.simplemobiletools.smsmessenger.models.Contact
import kotlinx.android.synthetic.main.item_contact.view.*

class AutoCompleteTextViewAdapter(val activity: SimpleActivity, val contacts: ArrayList<Contact>) :
    ArrayAdapter<Contact>(activity, 0, contacts) {
    var resultList = ArrayList<Contact>()
    private var placeholder = activity.resources.getDrawable(R.drawable.contact_circular_background)

    init {
        (placeholder as LayerDrawable).findDrawableByLayerId(R.id.attendee_circular_background)
            .applyColorFilter(activity.config.primaryColor)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val contact = resultList[position]
        var listItem = convertView
        if (listItem == null || listItem.tag != contact.name.isNotEmpty()) {
            listItem = LayoutInflater.from(activity).inflate(R.layout.item_contact, parent, false)
        }

        listItem!!.apply {
            tag = contact.name.isNotEmpty()
            item_autocomplete_name.text = contact.name
            item_autocomplete_number.text = contact.phoneNumber

            contact.updateImage(context, item_autocomplete_image, placeholder)
        }

        return listItem
    }

    override fun getFilter() = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterResults = FilterResults()
            if (constraint != null) {
                resultList.clear()
                val searchString = constraint.toString().normalizeString()
                contacts.forEach {
                    if (it.phoneNumber.contains(searchString, true) || it.name.contains(searchString, true)) {
                        resultList.add(it)
                    }
                }

                resultList.sortWith(compareBy { !it.name.startsWith(searchString, true) })

                filterResults.values = resultList
                filterResults.count = resultList.size
            }
            return filterResults
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results?.count ?: -1 > 0) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?) = (resultValue as? Contact)?.name
    }

    override fun getItem(index: Int) = resultList[index]

    override fun getCount() = resultList.size
}
