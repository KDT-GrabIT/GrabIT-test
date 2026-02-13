package com.example.grabitTest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.grabitTest.databinding.FragmentProfileBinding

/**
 * 내 정보 화면.
 * [자주 찾는 상품] [최근 찾은 상품] 버튼 → 클릭 시 상세 목록 화면으로 이동.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnFrequent.setOnClickListener {
            findNavController().navigate(
                R.id.nav_search_history_detail,
                bundleOf(SEARCH_HISTORY_DETAIL_TYPE to "frequent")
            )
        }
        binding.btnRecent.setOnClickListener {
            findNavController().navigate(
                R.id.nav_search_history_detail,
                bundleOf(SEARCH_HISTORY_DETAIL_TYPE to "recent")
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
