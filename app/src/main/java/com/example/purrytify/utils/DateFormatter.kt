package com.example.purrytify.utils

import java.text.SimpleDateFormat
import java.util.Locale

object DateFormatter {

    fun formatApiDate(apiDate: String): String {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)

            val date = inputFormat.parse(apiDate)
            return if (date != null) {
                outputFormat.format(date)
            } else {
                apiDate
            }
        } catch (e: Exception) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)

                val date = inputFormat.parse(apiDate)
                return if (date != null) {
                    outputFormat.format(date)
                } else {
                    apiDate
                }
            } catch (e: Exception) {
                return apiDate
            }
        }
    }
}