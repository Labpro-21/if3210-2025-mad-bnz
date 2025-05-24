package com.example.purrytify.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.example.purrytify.R
import com.example.purrytify.ui.profile.analytics.MonthlyStats
import java.util.concurrent.CompletableFuture
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import com.bumptech.glide.load.resource.bitmap.RoundedCorners as GlideRoundedCorners


object ViewUtils {
    suspend fun createShareableImage(context: Context, stats: MonthlyStats): Bitmap =
        withContext(Dispatchers.Main) {
            val view = LayoutInflater.from(context).inflate(R.layout.layout_share_capsule, null)

            // Load image first
            val imageView = view.findViewById<ImageView>(R.id.ivTopSong)
            val imageLoaded = suspendCancellableCoroutine<Boolean> { continuation ->
                Glide.with(context)
                    .load(stats.topSongs.firstOrNull()?.imageUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .transform(GlideRoundedCorners(16))
                    .into(object : CustomTarget<Drawable>() {
                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            imageView.setImageDrawable(resource)
                            continuation.resume(true)
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            imageView.setImageResource(R.drawable.placeholder_album)
                            continuation.resume(true)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Not needed
                        }
                    })
            }

            // Set other data
            view.findViewById<TextView>(R.id.tvDate).text = stats.monthYear
            view.findViewById<TextView>(R.id.tvTimeListened).text = "${stats.totalMinutes} minutes"

            val topArtistsText = stats.topArtists.take(5).mapIndexed { index, artist ->
                "${index + 1} ${artist.name}"
            }.joinToString("\n")
            view.findViewById<TextView>(R.id.tvTopArtists).text = topArtistsText

            val topSongsText = stats.topSongs.take(5).mapIndexed { index, song ->
                "${index + 1} ${song.title}"
            }.joinToString("\n")
            view.findViewById<TextView>(R.id.tvTopSongs).text = topSongsText

            // Measure and layout
            val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
            val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(widthMeasureSpec, heightMeasureSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)

            // Create bitmap after everything is loaded
            Bitmap.createBitmap(
                view.measuredWidth,
                view.measuredHeight,
                Bitmap.Config.ARGB_8888
            ).also { bitmap ->
                Canvas(bitmap).also { canvas ->
                    view.draw(canvas)
                }
            }
        }
}