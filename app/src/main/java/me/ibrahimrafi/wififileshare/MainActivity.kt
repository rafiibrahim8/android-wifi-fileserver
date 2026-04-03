package me.ibrahimrafi.wififileshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.ibrahimrafi.wififileshare.ui.home.HomeFragment
import me.ibrahimrafi.wififileshare.ui.settings.SettingsFragment
import me.ibrahimrafi.wififileshare.ui.transfers.TransferLogFragment
import me.ibrahimrafi.wififileshare.server.FileServerService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> show(HomeFragment())
                R.id.nav_transfers -> show(TransferLogFragment())
                R.id.nav_settings -> show(SettingsFragment())
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }

        if (intent?.action == ACTION_START_SHORTCUT) {
            FileServerService.start(this)
        }
    }

    private fun show(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        return true
    }

    companion object {
        private const val ACTION_START_SHORTCUT = "me.ibrahimrafi.wififileshare.action.START_SHORTCUT"
    }
}
