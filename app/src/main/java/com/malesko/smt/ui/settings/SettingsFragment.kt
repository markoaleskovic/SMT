package com.malesko.smt.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.malesko.smt.data.local.prefs.PrefsKeys
import com.malesko.smt.databinding.FragmentSettingsBinding
import com.malesko.smt.reminder.PracticeReminderScheduler
import java.util.Calendar
import androidx.core.content.edit

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.btnPracticeReminder.setOnClickListener {
            ensureNotificationPermissionThenPickTime()
        }

        binding.btnPracticeReminder.setOnLongClickListener {
            disableReminder()
            true
        }

        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ensureNotificationPermissionThenPickTime() {

            val granted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }


        pickTimeAndSchedule()
    }

    private fun pickTimeAndSchedule() {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)

        TimePickerDialog(requireContext(), { _, h, m ->
            val prefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_NAME, 0)
            prefs.edit {
                putBoolean(PrefsKeys.KEY_PRACTICE_REMINDER_ENABLED, true)
                    .putInt(PrefsKeys.KEY_PRACTICE_REMINDER_HOUR, h)
                    .putInt(PrefsKeys.KEY_PRACTICE_REMINDER_MINUTE, m)
            }

            PracticeReminderScheduler.scheduleDaily(requireContext(), h, m)
        }, hour, minute, true).show()


    }

    private fun disableReminder() {
        val prefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_NAME, 0)
        prefs.edit {
            putBoolean(PrefsKeys.KEY_PRACTICE_REMINDER_ENABLED, false)
        }

        PracticeReminderScheduler.cancel(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
