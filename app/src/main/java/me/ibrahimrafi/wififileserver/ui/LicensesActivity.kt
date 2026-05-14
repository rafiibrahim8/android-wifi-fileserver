package me.ibrahimrafi.wififileserver.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import me.ibrahimrafi.wififileserver.R

class LicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_licenses)

        val root = findViewById<View>(R.id.licenses_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            WindowInsetsCompat.CONSUMED
        }

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val container = findViewById<LinearLayout>(R.id.licenses_container)
        val inflater = LayoutInflater.from(this)
        for (entry in LIBRARIES) {
            val card = inflater.inflate(R.layout.item_license, container, false)
            card.findViewById<TextView>(R.id.license_name).text = entry.name
            card.findViewById<TextView>(R.id.license_version).text = entry.version
            card.findViewById<TextView>(R.id.license_license).text = entry.license
            card.findViewById<TextView>(R.id.license_purpose).text = entry.purpose
            card.setOnClickListener {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)))
                }
            }
            container.addView(card)
        }
    }

    private data class LibraryEntry(
        val name: String,
        val version: String,
        val license: String,
        val purpose: String,
        val url: String,
    )

    companion object {
        // Keep in sync with gradle/libs.versions.toml. When you bump a version, update here too.
        private val LIBRARIES = listOf(
            LibraryEntry(
                name = "NanoHTTPD",
                version = "2.3.1",
                license = "BSD 3-Clause License",
                purpose = "The HTTP server itself — handles incoming requests and streams responses.",
                url = "https://github.com/NanoHttpd/nanohttpd",
            ),
            LibraryEntry(
                name = "ZXing Core",
                version = "3.5.4",
                license = "Apache License 2.0",
                purpose = "QR code generation for the on-screen scan target.",
                url = "https://github.com/zxing/zxing",
            ),
            LibraryEntry(
                name = "Material Components for Android",
                version = "1.13.0",
                license = "Apache License 2.0",
                purpose = "Buttons, chips, cards, bottom navigation, Snackbar, and other UI widgets.",
                url = "https://github.com/material-components/material-components-android",
            ),
            LibraryEntry(
                name = "Timber",
                version = "5.0.1",
                license = "Apache License 2.0",
                purpose = "Lightweight logging façade. Debug builds only.",
                url = "https://github.com/JakeWharton/timber",
            ),
            LibraryEntry(
                name = "AndroidX AppCompat",
                version = "1.7.1",
                license = "Apache License 2.0",
                purpose = "Backwards-compatible Activity, theme, and resource APIs.",
                url = "https://developer.android.com/jetpack/androidx/releases/appcompat",
            ),
            LibraryEntry(
                name = "AndroidX Core KTX",
                version = "1.18.0",
                license = "Apache License 2.0",
                purpose = "Kotlin extensions for the core framework.",
                url = "https://developer.android.com/jetpack/androidx/releases/core",
            ),
            LibraryEntry(
                name = "AndroidX Activity KTX",
                version = "1.13.0",
                license = "Apache License 2.0",
                purpose = "Activity Result APIs and lifecycle helpers.",
                url = "https://developer.android.com/jetpack/androidx/releases/activity",
            ),
            LibraryEntry(
                name = "AndroidX Fragment KTX",
                version = "1.8.9",
                license = "Apache License 2.0",
                purpose = "Fragment APIs including viewModels() delegate.",
                url = "https://developer.android.com/jetpack/androidx/releases/fragment",
            ),
            LibraryEntry(
                name = "AndroidX Preference KTX",
                version = "1.2.1",
                license = "Apache License 2.0",
                purpose = "Powers the Settings screen.",
                url = "https://developer.android.com/jetpack/androidx/releases/preference",
            ),
            LibraryEntry(
                name = "AndroidX RecyclerView",
                version = "1.4.0",
                license = "Apache License 2.0",
                purpose = "Renders the transfers list.",
                url = "https://developer.android.com/jetpack/androidx/releases/recyclerview",
            ),
            LibraryEntry(
                name = "AndroidX ConstraintLayout",
                version = "2.2.1",
                license = "Apache License 2.0",
                purpose = "Flexible layout primitive.",
                url = "https://developer.android.com/jetpack/androidx/releases/constraintlayout",
            ),
            LibraryEntry(
                name = "AndroidX DocumentFile",
                version = "1.1.0",
                license = "Apache License 2.0",
                purpose = "Storage Access Framework wrapper for the user-picked shared folder.",
                url = "https://developer.android.com/jetpack/androidx/releases/documentfile",
            ),
            LibraryEntry(
                name = "AndroidX Lifecycle (Service, ViewModel, LiveData)",
                version = "2.10.0",
                license = "Apache License 2.0",
                purpose = "ViewModel + LifecycleService + LiveData adapters for state observation.",
                url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            ),
            LibraryEntry(
                name = "Kotlin standard library + coroutines",
                version = "(bundled)",
                license = "Apache License 2.0",
                purpose = "Language runtime; coroutines power Dispatchers.Default and lifecycle scopes.",
                url = "https://github.com/JetBrains/kotlin",
            ),
        )
    }
}
