package com.example.purrytify.model
import android.os.Parcelable
import kotlinx.parcelize.Parcelize


@Parcelize
data class DailyListening(
    val playDate: String,
    val minutes: Int
) : Parcelable