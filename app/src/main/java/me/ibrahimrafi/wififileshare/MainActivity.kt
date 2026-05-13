package me.ibrahimrafi.wififileshare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import android.view.ViewGroup.MarginLayoutParams
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.ibrahimrafi.wififileshare.ui.OnboardingActivity
import me.ibrahimrafi.wififileshare.ui.home.HomeFragment
import me.ibrahimrafi.wififileshare.ui.settings.SettingsFragment
import me.ibrahimrafi.wififileshare.ui.transfers.TransferLogFragment
import me.ibrahimrafi.wififileshare.server.FileServerService

class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored — notification is best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldShowOnboarding()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        requestNotificationPermissionIfNeeded()

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

        if (savedInstanceState == null) {
            val fm = supportFragmentManager
            val tx = fm.beginTransaction()
            val home = HomeFragment()
            val transfers = TransferLogFragment()
            val settings = SettingsFragment()
            tx.add(R.id.fragment_container, home, TAG_HOME)
            tx.add(R.id.fragment_container, transfers, TAG_TRANSFERS)
            tx.hide(transfers)
            tx.add(R.id.fragment_container, settings, TAG_SETTINGS)
            tx.hide(settings)
            tx.commitNow()
            activeFragment = home
        } else {
            val fm = supportFragmentManager
            activeFragment = listOf(TAG_HOME, TAG_TRANSFERS, TAG_SETTINGS)
                .mapNotNull { fm.findFragmentByTag(it) }
                .firstOrNull { !it.isHidden }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchTo(TAG_HOME)
                R.id.nav_transfers -> switchTo(TAG_TRANSFERS)
                R.id.nav_settings -> switchTo(TAG_SETTINGS)
                else -> false
            }
        }

        if (intent?.action == ACTION_START_SHORTCUT) {
            FileServerService.start(this)
        }
    }

    private var activeFragment: Fragment? = null

    private fun shouldShowOnboarding(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return !prefs.getBoolean(OnboardingActivity.KEY_ONBOARDING_COMPLETE, false)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun switchTo(tag: String): Boolean {
        val fm = supportFragmentManager
        val target = fm.findFragmentByTag(tag) ?: return false
        val current = activeFragment
        if (target === current) return true
        val movingRight = current != null && tabIndex(tag) > tabIndex(current.tag)
        val enter = if (movingRight) R.anim.slide_in_right else R.anim.slide_in_left
        val exit = if (movingRight) R.anim.slide_out_left else R.anim.slide_out_right
        val tx = fm.beginTransaction()
            .setCustomAnimations(enter, exit)
        current?.let { tx.hide(it) }
        tx.show(target)
        tx.commit()
        activeFragment = target
        return true
    }

    private fun tabIndex(tag: String?): Int = when (tag) {
        TAG_HOME -> 0
        TAG_TRANSFERS -> 1
        TAG_SETTINGS -> 2
        else -> -1
    }

    companion object {
        private const val ACTION_START_SHORTCUT = "me.ibrahimrafi.wififileshare.action.START_SHORTCUT"
        private const val TAG_HOME = "nav_home"
        private const val TAG_TRANSFERS = "nav_transfers"
        private const val TAG_SETTINGS = "nav_settings"
    }
}
