package com.example.purrytify.ui.profile.analytics

import android.graphics.Canvas
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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
import com.example.purrytify.databinding.FragmentTopSongsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TopSongsFragment : Fragment() {
    private var _binding: FragmentTopSongsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SoundCapsuleViewModel by viewModels()
    private val songAdapter = TopSongsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTopSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.apply {
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }

            rvTopSongs.apply {
                adapter = songAdapter
                layoutManager = LinearLayoutManager(context)
                
                // Add divider
                addItemDecoration(object : RecyclerView.ItemDecoration() {
                    private val divider = ContextCompat.getDrawable(
                        context,
                        R.drawable.list_divider
                    )

                    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                        val left = parent.paddingLeft
                        val right = parent.width - parent.paddingRight

                        for (i in 0 until parent.childCount - 1) {
                            val child = parent.getChildAt(i)
                            val params = child.layoutParams as RecyclerView.LayoutParams

                            val top = child.bottom + params.bottomMargin
                            val bottom = top + (divider?.intrinsicHeight ?: 1)

                            divider?.setBounds(left, top, right, bottom)
                            divider?.draw(c)
                        }
                    }
                })
            }
        }

        // Create highlighted text for song count
        viewModel.monthlyStats.observe(viewLifecycleOwner) { stats ->
            binding.tvDate.text = stats.monthYear
            
            val songCount = stats.topSongs.size
            val fullText = "You played $songCount different songs this month."
            val spannableString = SpannableString(fullText)
            
            val highlightText = "$songCount different songs"
            val start = fullText.indexOf(highlightText)
            val end = start + highlightText.length
            
            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.yellow)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            binding.tvSongCount.text = spannableString
        }
    }

    private fun observeViewModel() {
        viewModel.monthlyStats.observe(viewLifecycleOwner) { stats ->
            binding.tvDate.text = stats.monthYear
            val songCount = stats.topSongs.size

            // Create spannable text
            val fullText = "You played $songCount different songs this month."
            val spannableString = SpannableString(fullText)
            val start = fullText.indexOf(songCount.toString())
            val end = start + songCount.toString().length + " different songs".length

            spannableString.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.yellow)),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            binding.tvSongCount.text = spannableString
            songAdapter.submitList(stats.topSongs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}