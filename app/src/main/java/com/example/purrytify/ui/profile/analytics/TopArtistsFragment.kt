package com.example.purrytify.ui.profile.analytics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.purrytify.R
import com.example.purrytify.databinding.FragmentTopArtistsBinding
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class TopArtistsFragment : Fragment() {
    private var _binding: FragmentTopArtistsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SoundCapsuleViewModel by viewModels()
    private val artistAdapter = TopArtistsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        viewModel.loadMonthlyStats()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }

            rvTopArtists.apply {
                adapter = artistAdapter
                layoutManager = LinearLayoutManager(context)

                // Add custom divider
                val divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
                divider.setDrawable(ContextCompat.getDrawable(context, R.drawable.list_divider)!!)
                addItemDecoration(divider)

                // Add top and bottom dividers
                addItemDecoration(object : RecyclerView.ItemDecoration() {
                    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                        // Draw top divider for first item
                        val child = parent.getChildAt(0)
                        if (child != null) {
                            val params = child.layoutParams as RecyclerView.LayoutParams
                            val top = child.top + params.topMargin
                            val left = parent.paddingLeft
                            val right = parent.width - parent.paddingRight

                            c.drawRect(
                                left.toFloat(),
                                top.toFloat(),
                                right.toFloat(),
                                (top + 1).toFloat(),
                                Paint().apply {
                                    color = Color.parseColor("#1FFFFFFF")
                                }
                            )
                        }
                    }
                })
            }
        }
    }

    private fun observeViewModel() {
        viewModel.apply {
            loadMonthlyStats()

                Log.e("Observe",monthlyStats.value.toString())
            monthlyStats.observe(viewLifecycleOwner) { stats ->
                binding.tvDate.text = stats.monthYear
                val artistCount = stats.topArtists.size

                val fullText = "You listened to $artistCount artists this month."
                val spannableString = SpannableString(fullText)
                val start = fullText.indexOf(artistCount.toString())
                val end = start + artistCount.toString().length + " artists".length

                spannableString.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.blue)),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                binding.tvArtistCount.text = spannableString
                artistAdapter.submitList(stats.topArtists)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}