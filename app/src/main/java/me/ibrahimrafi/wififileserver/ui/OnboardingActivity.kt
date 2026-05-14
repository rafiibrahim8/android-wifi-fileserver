package me.ibrahimrafi.wififileshare.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import me.ibrahimrafi.wififileshare.MainActivity
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.storage.FolderAccessManager

class OnboardingActivity : AppCompatActivity() {

    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching { FolderAccessManager.persistUri(this, uri) }
        }
        // Whether or not a folder was picked, leave onboarding; folder can be picked later from Home.
        finishOnboarding()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_onboarding)

        val root = findViewById<View>(R.id.onboarding_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            WindowInsetsCompat.CONSUMED
        }

        findViewById<MaterialButton>(R.id.get_started_button).setOnClickListener {
            folderPicker.launch(FolderAccessManager.createFolderIntent())
        }
        findViewById<View>(R.id.skip_button).setOnClickListener { finishOnboarding() }
    }

    private fun finishOnboarding() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putBoolean(KEY_ONBOARDING_COMPLETE, true)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
