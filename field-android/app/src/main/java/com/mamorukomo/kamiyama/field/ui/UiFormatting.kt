package com.mamorukomo.kamiyama.field.ui

import java.text.DateFormat
import java.util.Date

internal fun formatDate(millis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
}

internal fun Double.format5(): String {
    return "%.5f".format(this)
}
