package com.familyhealth.medicinetracker.presentation.ui.dashboard

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.familyhealth.medicinetracker.R
import com.familyhealth.medicinetracker.databinding.FragmentDashboardBinding
import com.familyhealth.medicinetracker.domain.model.*
import com.familyhealth.medicinetracker.presentation.viewmodel.DashboardViewModel
import com.familyhealth.medicinetracker.presentation.viewmodel.DashboardViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: DashboardViewModel
    private lateinit var doseAdapter: TodayDoseAdapter

    // Countdown timer handler
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            viewModel.nextAppointment.value?.let { doc ->
                viewModel.refreshAppointmentCountdown(doc.nextAppointment)
            }
            countdownHandler.postDelayed(this, 60_000) // Update every minute
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this, DashboardViewModelFactory(requireActivity().application)
        )[DashboardViewModel::class.java]

        setupRecyclerView()
        setupMealButtons()
        setupObservers()
        setupFab()

        // Show today's date
        binding.tvDate.text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
            .format(Date())
    }

    private fun setupRecyclerView() {
        doseAdapter = TodayDoseAdapter(
            onTaken = { todayDose ->
                viewModel.markTaken(todayDose.doseLog.id, todayDose.medication.id)
            },
            onSkipped = { todayDose ->
                viewModel.markSkipped(todayDose.doseLog.id)
            }
        )
        binding.rvTodayDoses.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = doseAdapter
        }
    }

    private fun setupMealButtons() {
        binding.btnBreakfast.setOnClickListener {
            viewModel.logMeal(MealType.BREAKFAST)
            binding.btnBreakfast.isEnabled = false
            binding.btnBreakfast.text = "✅ Breakfast Logged"
        }
        binding.btnLunch.setOnClickListener {
            viewModel.logMeal(MealType.LUNCH)
            binding.btnLunch.isEnabled = false
            binding.btnLunch.text = "✅ Lunch Logged"
        }
        binding.btnDinner.setOnClickListener {
            viewModel.logMeal(MealType.DINNER)
            binding.btnDinner.isEnabled = false
            binding.btnDinner.text = "✅ Dinner Logged"
        }
    }

    private fun setupObservers() {
        viewModel.todayDoses.observe(viewLifecycleOwner) { doses ->
            doseAdapter.submitList(doses)
            binding.tvEmptyState.visibility =
                if (doses.isEmpty()) View.VISIBLE else View.GONE
            updateDoseProgress(doses)
        }

        viewModel.todayEvents.observe(viewLifecycleOwner) { events ->
            // Update meal button states based on logged events
            events.forEach { event ->
                when (event.mealType) {
                    MealType.BREAKFAST -> {
                        binding.btnBreakfast.isEnabled = false
                        binding.btnBreakfast.text = "✅ Breakfast Logged"
                    }
                    MealType.LUNCH -> {
                        binding.btnLunch.isEnabled = false
                        binding.btnLunch.text = "✅ Lunch Logged"
                    }
                    MealType.DINNER -> {
                        binding.btnDinner.isEnabled = false
                        binding.btnDinner.text = "✅ Dinner Logged"
                    }
                    else -> {}
                }
            }
        }

        viewModel.lowStockMeds.observe(viewLifecycleOwner) { meds ->
            if (meds.isNotEmpty()) {
                binding.cardLowStock.visibility = View.VISIBLE
                val names = meds.joinToString(", ") { "${it.name} (${it.totalStock} left)" }
                binding.tvLowStock.text = "⚠️ Low stock: $names"
            } else {
                binding.cardLowStock.visibility = View.GONE
            }
        }

        viewModel.nextAppointment.observe(viewLifecycleOwner) { doctor ->
            if (doctor?.nextAppointment != null) {
                binding.cardAppointment.visibility = View.VISIBLE
                viewModel.refreshAppointmentCountdown(doctor.nextAppointment)
            } else {
                binding.cardAppointment.visibility = View.GONE
            }
        }

        viewModel.appointmentCountdown.observe(viewLifecycleOwner) { ms ->
            ms?.let {
                val days = TimeUnit.MILLISECONDS.toDays(it)
                val hours = TimeUnit.MILLISECONDS.toHours(it) % 24
                binding.tvAppointmentCountdown.text = when {
                    days > 0 -> "📅 Appointment in $days days, $hours hours"
                    hours > 0 -> "📅 Appointment in $hours hours"
                    else -> "📅 Appointment today!"
                }
                val docName = viewModel.nextAppointment.value?.name ?: ""
                binding.tvDoctorName.text = "Dr. $docName"
            }
        }
    }

    private fun setupFab() {
        binding.fabAddMedicine.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_addMedicine)
        }
    }

    private fun updateDoseProgress(doses: List<TodayDose>) {
        val total = doses.size
        val taken = doses.count { it.doseLog.status == DoseStatus.TAKEN }
        binding.progressDoses.max = total
        binding.progressDoses.progress = taken
        binding.tvDoseProgress.text = "$taken / $total doses taken today"
    }

    override fun onResume() {
        super.onResume()
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
