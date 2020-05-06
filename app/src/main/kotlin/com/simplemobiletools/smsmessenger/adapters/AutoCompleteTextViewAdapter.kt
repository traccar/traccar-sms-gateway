package com.simplemobiletools.smsmessenger.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import com.simplemobiletools.commons.extensions.normalizeString
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.activities.SimpleActivity
import com.simplemobiletools.smsmessenger.extensions.loadImage
import kotlinx.android.synthetic.main.item_contact.view.*

class AutoCompleteTextViewAdapter(val activity: SimpleActivity, val contacts: ArrayList<SimpleContact>) :
    ArrayAdapter<SimpleContact>(activity, 0, contacts) {
    var resultList = ArrayList<SimpleContact>()

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

            context.loadImage(contact.photoUri, item_autocomplete_image, contact.name)
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

        override fun convertResultToString(resultValue: Any?) = (resultValue as? SimpleContact)?.name
    }

    override fun getItem(index: Int) = resultList[index]

    override fun getCount() = resultList.size
}
