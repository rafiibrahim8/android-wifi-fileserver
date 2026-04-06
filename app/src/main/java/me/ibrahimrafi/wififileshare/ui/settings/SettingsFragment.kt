package me.ibrahimrafi.wififileshare.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import me.ibrahimrafi.wififileshare.R

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (childFragmentManager.findFragmentByTag(TAG_PREFERENCES) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.settings_content, SettingsPreferencesFragment(), TAG_PREFERENCES)
                .commit()
        }
    }

    companion object {
        private const val TAG_PREFERENCES = "settings_preferences"
    }
}
