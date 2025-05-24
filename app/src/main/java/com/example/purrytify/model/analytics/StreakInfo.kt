package com.example.purrytify.model.analytics

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class StreakInfo(
    val daysCount: Int,
    val startDate: String,
    val endDate: String
) :Parcelable