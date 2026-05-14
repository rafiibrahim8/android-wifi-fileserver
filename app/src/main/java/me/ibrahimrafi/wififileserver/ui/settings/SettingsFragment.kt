package me.ibrahimrafi.wififileserver.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import android.widget.TextView
import me.ibrahimrafi.wififileserver.R

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val viewModel: SettingsViewModel by viewModels()
    private var restartHintView: TextView? = null
    private var sharedPreferences: SharedPreferences? = null
    private var isServerRunning = false
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (isServerRunning && key != THEME_MODE_KEY) {
            viewModel.markSettingsChangedWhileRunning()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restartHintView = view.findViewById(R.id.settings_restart_hint)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        viewModel.isRunning.observe(viewLifecycleOwner) { running ->
            isServerRunning = running
            updateRestartHintVisibility()
        }
        viewModel.hasPendingRestartNotice.observe(viewLifecycleOwner) {
            updateRestartHintVisibility()
        }

        if (childFragmentManager.findFragmentByTag(TAG_PREFERENCES) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_content, SettingsPreferencesFragment(), TAG_PREFERENCES)
                .commit()
        }
    }

    override fun onStart() {
        super.onStart()
        sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onStop() {
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        super.onStop()
    }

    override fun onDestroyView() {
        restartHintView = null
        super.onDestroyView()
    }

    private fun updateRestartHintVisibility() {
        val show = isServerRunning && (viewModel.hasPendingRestartNotice.value == true)
        restartHintView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    companion object {
        private const val TAG_PREFERENCES = "settings_preferences"
        private const val THEME_MODE_KEY = "theme_mode"
    }
}
