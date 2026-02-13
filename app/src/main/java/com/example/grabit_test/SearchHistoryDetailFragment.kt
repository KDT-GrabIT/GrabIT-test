package com.example.grabitTest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.grabit_test.ProfileViewModel
import com.example.grabit_test.data.history.FrequentSearchItem
import com.example.grabit_test.data.history.SearchHistoryItem
import com.example.grabitTest.databinding.FragmentSearchHistoryDetailBinding
import com.example.grabitTest.databinding.ItemSearchHistoryBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Nav argument key: "type" = "frequent" | "recent" */
const val SEARCH_HISTORY_DETAIL_TYPE = "type"

/**
 * 자주 찾는 상품 / 최근 찾은 상품 상세 목록.
 * argument "type": "frequent" | "recent"
 * 항목 클릭 시 홈으로 이동하여 해당 상품 탐지 시작.
 */
class SearchHistoryDetailFragment : Fragment() {

    private var _binding: FragmentSearchHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private val type: String get() = arguments?.getString(SEARCH_HISTORY_DETAIL_TYPE) ?: "recent"

    private val viewModel: ProfileViewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[ProfileViewModel::class.java]
    }

    private val sharedViewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchHistoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isFrequent = type == "frequent"
        binding.toolbar.title = if (isFrequent) "자주 찾는 상품" else "최근 찾은 상품"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.recycler.layoutManager = LinearLayoutManager(requireContext())

        if (isFrequent) {
            val adapter = FrequentAdapter(emptyList()) { classLabel ->
                navigateToHomeAndSearch(classLabel)
            }
            binding.recycler.adapter = adapter
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.frequentSearches.collect { adapter.updateList(it) }
                }
            }
        } else {
            val adapter = RecentAdapter(emptyList()) { classLabel ->
                navigateToHomeAndSearch(classLabel)
            }
            binding.recycler.adapter = adapter
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.recentSearches.collect { adapter.updateList(it) }
                }
            }
        }
    }

    private fun navigateToHomeAndSearch(classLabel: String) {
        sharedViewModel.selectTargetFromAdmin(classLabel)
        findNavController().navigate(R.id.nav_home)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class FrequentAdapter(
    initialList: List<FrequentSearchItem>,
    private val onItemClick: (classLabel: String) -> Unit
) : RecyclerView.Adapter<FrequentAdapter.VH>() {

    private var list = initialList

    fun updateList(newList: List<FrequentSearchItem>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSearchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.binding.itemSearchHistoryTitle.text = if (ProductDictionary.isLoaded()) ProductDictionary.getDisplayNameKo(item.classLabel) else item.classLabel
        holder.binding.itemSearchHistorySubtitle.text = "검색 ${item.searchCount}회"
        holder.binding.itemSearchHistorySubtitle.visibility = View.VISIBLE
        holder.binding.root.setOnClickListener { onItemClick(item.classLabel) }
    }

    override fun getItemCount(): Int = list.size

    class VH(val binding: ItemSearchHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}

private class RecentAdapter(
    initialList: List<SearchHistoryItem>,
    private val onItemClick: (classLabel: String) -> Unit
) : RecyclerView.Adapter<RecentAdapter.VH>() {

    private var list = initialList

    fun updateList(newList: List<SearchHistoryItem>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemSearchHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.binding.itemSearchHistoryTitle.text = if (ProductDictionary.isLoaded()) ProductDictionary.getDisplayNameKo(item.classLabel) else item.classLabel
        holder.binding.itemSearchHistorySubtitle.text = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.searchedAt))
        holder.binding.itemSearchHistorySubtitle.visibility = View.VISIBLE
        holder.binding.root.setOnClickListener { onItemClick(item.classLabel) }
    }

    override fun getItemCount(): Int = list.size

    class VH(val binding: ItemSearchHistoryBinding) : RecyclerView.ViewHolder(binding.root)
}
