package com.familyhealth.medicinetracker.presentation.ui.addmedicine

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.familyhealth.medicinetracker.R
import com.familyhealth.medicinetracker.databinding.FragmentAddMedicineBinding
import com.familyhealth.medicinetracker.domain.model.*
import com.familyhealth.medicinetracker.presentation.viewmodel.AddMedicineViewModel
import com.familyhealth.medicinetracker.presentation.viewmodel.AddMedicineViewModelFactory
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import java.util.*

class AddMedicineFragment : Fragment() {

    private var _binding: FragmentAddMedicineBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AddMedicineViewModel
    private lateinit var searchAdapter: MedicineSearchAdapter

    // Collected schedule times: (hour, minute, label)
    private val scheduleTimes = mutableListOf<Triple<Int, Int, String>>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentAddMedicineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this, AddMedicineViewModelFactory(requireActivity().application)
        )[AddMedicineViewModel::class.java]

        setupSearch()
        setupFoodRelationSpinner()
        setupFrequencySpinner()
        setupScheduleBuilder()
        setupSaveButton()
        setupObservers()
    }

    // ── FTS Medicine Search ───────────────────────────────────────────────────

    private fun setupSearch() {
        searchAdapter = MedicineSearchAdapter { selected ->
            binding.etMedicineName.setText(selected.name)
            binding.etDosage.setText(selected.defaultDosage)
            binding.rvSearchResults.visibility = View.GONE
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }

        binding.etMedicineName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.search(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ── Spinners ──────────────────────────────────────────────────────────────

    private fun setupFoodRelationSpinner() {
        val options = listOf("Anytime", "Before Food", "After Food", "With Food")
        binding.spinnerFoodRelation.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setupFrequencySpinner() {
        val options = listOf("Once Daily", "Twice Daily", "Three Times Daily",
            "Every X Hours", "As Needed")
        binding.spinnerFrequency.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    // ── Schedule Time Builder ─────────────────────────────────────────────────

    private fun setupScheduleBuilder() {
        binding.btnAddTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val label = binding.etScheduleLabel.text.toString().ifBlank {
                    when {
                        hour < 12 -> "Morning Dose"
                        hour < 17 -> "Afternoon Dose"
                        else -> "Night Dose"
                    }
                }
                scheduleTimes.add(Triple(hour, minute, label))
                addTimeChip(hour, minute, label)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
        }
    }

    private fun addTimeChip(hour: Int, minute: Int, label: String) {
        val chip = Chip(requireContext()).apply {
            text = "%s (%02d:%02d)".format(label, hour, minute)
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                scheduleTimes.removeAll { it.first == hour && it.second == minute }
                binding.chipGroupSchedules.removeView(this)
            }
        }
        binding.chipGroupSchedules.addView(chip)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val name = binding.etMedicineName.text.toString().trim()
            val dosage = binding.etDosage.text.toString().trim()
            val stockStr = binding.etStock.text.toString().trim()

            if (name.isEmpty()) {
                binding.etMedicineName.error = "Medicine name is required"
                return@setOnClickListener
            }
            if (scheduleTimes.isEmpty()) {
                Snackbar.make(binding.root, "Add at least one schedule time", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val stock = stockStr.toIntOrNull() ?: 0

            val foodRelation = when (binding.spinnerFoodRelation.selectedItemPosition) {
                0 -> FoodRelation.ANYTIME
                1 -> FoodRelation.BEFORE_FOOD
                2 -> FoodRelation.AFTER_FOOD
                3 -> FoodRelation.WITH_FOOD
                else -> FoodRelation.ANYTIME
            }

            val frequencyType = when (binding.spinnerFrequency.selectedItemPosition) {
                0 -> FrequencyType.ONCE_DAILY
                1 -> FrequencyType.TWICE_DAILY
                2 -> FrequencyType.THRICE_DAILY
                3 -> FrequencyType.EVERY_X_HOURS
                4 -> FrequencyType.AS_NEEDED
                else -> FrequencyType.ONCE_DAILY
            }

            viewModel.saveMedication(
                name = name,
                dosageAmount = dosage.ifBlank { "1 tablet" },
                totalStock = stock,
                dailyDosageCount = scheduleTimes.size,
                foodRelation = foodRelation,
                frequencyType = frequencyType,
                intervalHours = binding.etIntervalHours.text.toString().toIntOrNull() ?: 0,
                expiryDate = 0L,
                notes = binding.etNotes.text.toString(),
                doctorId = null,
                scheduleTimes = scheduleTimes.map { Pair(it.first, it.second) },
                scheduleLabels = scheduleTimes.map { it.third }
            )
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            searchAdapter.submitList(results)
            binding.rvSearchResults.visibility =
                if (results.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Snackbar.make(binding.root, "✅ Medicine saved & alarm set!", Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
