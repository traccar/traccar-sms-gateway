package com.simplemobiletools.smsmessenger.extensions

import com.simplemobiletools.commons.extensions.normalizeString
import java.util.*

// get the contact names first letter at showing the placeholder without image
fun String.getNameLetter() = normalizeString().toCharArray().getOrNull(0)?.toString()?.toUpperCase(Locale.getDefault()) ?: "S"
