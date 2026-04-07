package me.ibrahimrafi.wififileshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import android.view.ViewGroup.MarginLayoutParams
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.ibrahimrafi.wififileshare.ui.home.HomeFragment
import me.ibrahimrafi.wififileshare.ui.settings.SettingsFragment
import me.ibrahimrafi.wififileshare.ui.transfers.TransferLogFragment
import me.ibrahimrafi.wififileshare.server.FileServerService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.main_root)
        val fragmentContainer = findViewById<android.view.View>(R.id.fragment_container)
        val navCard = findViewById<MaterialCardView>(R.id.bottom_nav_card)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        val baseRootLeft = root.paddingLeft
        val baseRootTop = root.paddingTop
        val baseRootRight = root.paddingRight
        val baseFragmentTop = fragmentContainer.paddingTop
        val baseNavBottomMargin = (navCard.layoutParams as MarginLayoutParams).bottomMargin
        val baseBottomNavLeft = bottomNav.paddingLeft
        val baseBottomNavTop = bottomNav.paddingTop
        val baseBottomNavRight = bottomNav.paddingRight
        val baseBottomNavBottom = bottomNav.paddingBottom

        // Keep BottomNavigationView's own shape stable; we handle bottom system inset on outer card.
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            view.updatePadding(
                left = baseBottomNavLeft,
                top = baseBottomNavTop,
                right = baseBottomNavRight,
                bottom = baseBottomNavBottom,
            )
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            root.updatePadding(
                left = baseRootLeft + bars.left,
                top = baseRootTop,
                right = baseRootRight + bars.right,
                bottom = 0,
            )
            fragmentContainer.updatePadding(top = baseFragmentTop + bars.top)
            navCard.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = baseNavBottomMargin + bars.bottom
            }

            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        ViewCompat.requestApplyInsets(bottomNav)

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
