package me.ibrahimrafi.wififileshare.ui.settings

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.storage.FolderAccessManager
import me.ibrahimrafi.wififileshare.storage.ServerPreferences

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var serverPreferences: ServerPreferences

    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            FolderAccessManager.persistUri(requireContext(), uri)
            updateRootSummary(uri)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        serverPreferences = ServerPreferences(requireContext())

        setupPortValidation()
        setupTheme()
        setupRootFolder()
        setupAuthPreferences()
        setupReadOnlyDependency()
    }

    private fun setupPortValidation() {
        val portPref = findPreference<EditTextPreference>(ServerPreferences.KEY_PORT) ?: return
        portPref.setOnPreferenceChangeListener { _, newValue ->
            val port = (newValue as? String)?.toIntOrNull()
            port != null && port in 1024..65535
        }
    }

    private fun setupTheme() {
        val themePref = findPreference<ListPreference>("theme_mode") ?: return
        themePref.setOnPreferenceChangeListener { _, newValue ->
            when (newValue as String) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            true
        }
    }

    private fun setupRootFolder() {
        val rootPref = findPreference<Preference>("root_folder") ?: return
        val currentUri = serverPreferences.getConfig().rootUri
        if (currentUri != null) {
            updateRootSummary(currentUri)
        }
        rootPref.setOnPreferenceClickListener {
            folderPicker.launch(FolderAccessManager.createFolderIntent())
            true
        }
    }

    private fun setupAuthPreferences() {
        val anonPref = findPreference<SwitchPreferenceCompat>(ServerPreferences.KEY_ANON) ?: return
        val userPref = findPreference<EditTextPreference>(ServerPreferences.KEY_USER)
        val passwordPref = findPreference<EditTextPreference>(ServerPreferences.KEY_PASSWORD)

        fun refreshVisibility() {
            val anonymous = anonPref.isChecked
            userPref?.isVisible = !anonymous
            passwordPref?.isVisible = !anonymous
            anonPref.summary = if (anonymous) {
                "Anonymous access enabled"
            } else {
                "With auth enabled, use: wget --user=USER --password=PASS \"URL\""
            }
        }

        passwordPref?.text = ""
        passwordPref?.summary = if (serverPreferences.getConfig().password.isNotEmpty()) "********" else ""
        passwordPref?.setOnPreferenceChangeListener { _, newValue ->
            serverPreferences.setPassword((newValue as? String).orEmpty())
            passwordPref.summary = if ((newValue as? String).isNullOrBlank()) "" else "********"
            false
        }

        anonPref.setOnPreferenceChangeListener { _, newValue ->
            anonPref.isChecked = newValue as Boolean
            refreshVisibility()
            true
        }

        refreshVisibility()
    }

    private fun updateRootSummary(uri: Uri) {
        val rootPref = findPreference<Preference>("root_folder") ?: return
        rootPref.summary = uri.lastPathSegment ?: uri.toString()
    }

    private fun setupReadOnlyDependency() {
        val readOnlyPref = findPreference<SwitchPreferenceCompat>(ServerPreferences.KEY_READ_ONLY_FILESERVER) ?: return
        val uploadPref = findPreference<SwitchPreferenceCompat>(ServerPreferences.KEY_ALLOW_UPLOADS) ?: return

        fun refresh(readOnly: Boolean) {
            uploadPref.isEnabled = !readOnly
            uploadPref.summary = if (readOnly) {
                getString(R.string.pref_upload_disabled_by_read_only)
            } else {
                null
            }
        }

        readOnlyPref.setOnPreferenceChangeListener { _, newValue ->
            refresh(newValue as? Boolean ?: false)
            true
        }

        refresh(readOnlyPref.isChecked)
    }
}
