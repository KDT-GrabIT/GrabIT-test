package com.example.grabitTest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.grabitTest.databinding.FragmentAdminBinding
import java.io.BufferedReader
import java.io.InputStreamReader

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var classLabels: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadClassLabels()
        setupSpinner()
    }

    private fun loadClassLabels() {
        try {
            requireContext().assets.open("classes.txt").use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    classLabels = reader.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                }
            }
        } catch (e: Exception) {
            classLabels = emptyList()
        }
    }

    private fun setupSpinner() {
        if (classLabels.isEmpty()) {
            Toast.makeText(requireContext(), "클래스 목록을 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val spinnerItems = listOf("선택하세요") + classLabels
        binding.objectSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            spinnerItems
        )
        binding.objectSpinner.setSelection(0)
        binding.objectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) return
                val selected = classLabels.getOrNull(position - 1) ?: return
                if (selected.isNotBlank()) {
                    sharedViewModel.selectTargetFromAdmin(selected)
                    findNavController().navigate(R.id.nav_home)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
