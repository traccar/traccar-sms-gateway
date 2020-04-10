package com.simplemobiletools.smsmessenger.extensions

import com.simplemobiletools.commons.extensions.normalizeString

// get the contact names first letter at showing the placeholder without image
fun String.getNameLetter() = normalizeString().toCharArray().getOrNull(0)?.toString() ?: "S"
