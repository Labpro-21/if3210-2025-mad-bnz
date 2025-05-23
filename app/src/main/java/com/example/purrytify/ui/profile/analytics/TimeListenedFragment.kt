package com.example.purrytify.ui.profile.analytics

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentTimeListenedBinding
import com.example.purrytify.model.DailyListening
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.log

@AndroidEntryPoint
class TimeListenedFragment : Fragment() {
    private var _binding: FragmentTimeListenedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SoundCapsuleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimeListenedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupChart()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }


            tvDate.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(Calendar.getInstance().time)
        }
    }

    private fun setupChart() {
        binding.dailyChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#333333")
                setDrawAxisLine(false)
                textColor = Color.parseColor("#808080")
                textSize = 12f
                axisMinimum = 0f
                labelCount = 5
                granularity = 3f
            }


            xAxis.apply {
                setDrawGridLines(false)
                setDrawAxisLine(false)
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#808080")
                textSize = 10f
                labelCount = 7
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }

                spaceMin = 0.5f
                spaceMax = 0.5f
            }


            axisRight.isEnabled = false


            setVisibleXRangeMaximum(31f)


            setScaleEnabled(false)
            setPinchZoom(false)
        }
    }

    private fun observeViewModel() {
        Log.d("TimeListenedFragment", "Starting observation...")

        viewModel.timeListened.observe(viewLifecycleOwner) { stats ->
            if (stats == null) {
                Log.e("TimeListenedFragment", "Received null stats")
                return@observe
            }

            Log.d("TimeListenedFragment", "Received stats: $stats")
            updateListeningStats(stats.totalMinutes, stats.dailyAverage)

            if (stats.dailyData.isEmpty()) {
                Log.w("TimeListenedFragment", "Daily data is empty")
            } else {
                Log.d("TimeListenedFragment", "Daily data size: ${stats.dailyData.size}")
                updateChart(stats.dailyData)
            }
        }
    }

    private fun updateListeningStats(totalMinutes: Int, dailyAverage: Int) {
        binding.apply {
            val fullText = "You listened to music for ${totalMinutes} minutes this month."
            val spannableString = SpannableString(fullText)
            val minutesStart = fullText.indexOf(totalMinutes.toString())
            val minutesEnd = minutesStart + totalMinutes.toString().length + 8

            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.spotify_green)),
                minutesStart,
                minutesEnd,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            tvListeningStats.text = spannableString
            tvDailyAverage.text = "Daily average: $dailyAverage min"
        }
    }

    private fun updateChart(dailyListening: List<DailyListening>) {

        val entries = (0..30).map { day ->

            val minutes = dailyListening.find {
                it.playDate.split("-").last().toInt() == day + 1
            }?.minutes?.toFloat() ?: 0f

            Entry(day.toFloat(), minutes)
        }

        val dataSet = LineDataSet(entries, "Daily Listening").apply {
            color = ContextCompat.getColor(requireContext(), R.color.spotify_green)
            setDrawValues(false)
            setDrawCircles(false)
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillAlpha = 50
            fillColor = ContextCompat.getColor(requireContext(), R.color.spotify_green)
            setDrawFilled(true)


            val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.chart_gradient)
            fillDrawable = drawable
        }

        binding.dailyChart.apply {
            data = LineData(dataSet)

            moveViewToX(0f)

            animateY(1000)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}