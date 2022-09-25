package com.simplemobiletools.smsmessenger.extensions

import kotlin.math.roundToInt

/**
 * Returns the closest next number divisible by [multipleOf].
 */
fun Int.round(multipleOf: Int = 1) = (toDouble() / multipleOf).roundToInt() * multipleOf
