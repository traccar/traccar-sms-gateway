package com.simplemobiletools.smsmessenger.models

import com.simplemobiletools.commons.extensions.normalizeString

data class Contact(val id: Int, var name: String, var photoUri: String, var phoneNumber: String) : Comparable<Contact> {
    override fun compareTo(other: Contact): Int {
        val firstString = name.normalizeString()
        val secondString = other.name.normalizeString()

        return if (firstString.firstOrNull()?.isLetter() == true && secondString.firstOrNull()?.isLetter() == false) {
            -1
        } else if (firstString.firstOrNull()?.isLetter() == false && secondString.firstOrNull()?.isLetter() == true) {
            1
        } else {
            if (firstString.isEmpty() && secondString.isNotEmpty()) {
                1
            } else if (firstString.isNotEmpty() && secondString.isEmpty()) {
                -1
            } else {
                firstString.compareTo(secondString, true)
            }
        }
    }
}
