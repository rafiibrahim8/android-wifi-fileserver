package me.ibrahimrafi.wififileshare.ui

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import me.ibrahimrafi.wififileshare.R

/** Show a short snackbar anchored above the floating bottom-nav card. */
fun Fragment.snack(@StringRes msg: Int) {
    val anchorView = view ?: return
    Snackbar.make(anchorView, msg, Snackbar.LENGTH_SHORT)
        .setAnchorView(activity?.findViewById(R.id.bottom_nav_card))
        .show()
}

fun Fragment.snack(msg: CharSequence) {
    val anchorView = view ?: return
    Snackbar.make(anchorView, msg, Snackbar.LENGTH_SHORT)
        .setAnchorView(activity?.findViewById(R.id.bottom_nav_card))
        .show()
}
