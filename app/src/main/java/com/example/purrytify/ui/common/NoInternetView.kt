package com.example.purrytify.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.purrytify.R  // Pastikan import ini ada dan sesuai

class NoInternetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    init {
        View.inflate(context, R.layout.view_no_internet, this)
    }

    fun setRetryClickListener(listener: OnClickListener) {
        findViewById<Button>(R.id.btn_retry).setOnClickListener(listener)
    }
}