package me.ibrahimrafi.wififileshare.ui.home

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.model.Direction
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import me.ibrahimrafi.wififileshare.model.TransferStatus
import me.ibrahimrafi.wififileshare.qr.QrBitmapGenerator
import me.ibrahimrafi.wififileshare.server.FileServerService
import me.ibrahimrafi.wififileshare.storage.FolderAccessManager
import me.ibrahimrafi.wififileshare.storage.ServerPreferences

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var pulseAnimator: ObjectAnimator? = null
    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(requireContext(), getString(R.string.folder_not_selected), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val uri = result.data?.data
        if (uri == null) {
            Toast.makeText(requireContext(), getString(R.string.folder_not_selected), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        runCatching { FolderAccessManager.persistUri(requireContext(), uri) }
            .onSuccess { FileServerService.start(requireContext()) }
            .onFailure {
                Toast.makeText(requireContext(), getString(R.string.folder_not_selected), Toast.LENGTH_SHORT).show()
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val stateText = view.findViewById<TextView>(R.id.server_state)
        val urlText = view.findViewById<TextView>(R.id.server_url)
        val networkText = view.findViewById<TextView>(R.id.network_name)
        val quickStats = view.findViewById<TextView>(R.id.quick_stats)
        val statusDot = view.findViewById<View>(R.id.status_dot)
        val startStopButton = view.findViewById<MaterialButton>(R.id.start_stop_button)
        val qrImage = view.findViewById<ImageView>(R.id.qr_image)

        startStopButton.setOnClickListener {
            val running = ServerStateStore.isRunning.value == true
            if (running) {
                FileServerService.stop(requireContext())
            } else {
                val rootUri = ServerPreferences(requireContext()).getConfig().rootUri
                if (rootUri == null) {
                    folderPicker.launch(FolderAccessManager.createFolderIntent())
                } else {
                    FileServerService.start(requireContext())
                }
            }
        }

        urlText.setOnClickListener {
            val text = urlText.text?.toString().orEmpty()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("WiFi URL", text))
            Toast.makeText(requireContext(), getString(R.string.url_copied), Toast.LENGTH_SHORT).show()
        }

        ServerStateStore.isRunning.observe(viewLifecycleOwner) { running ->
            stateText.setText(if (running) R.string.server_running else R.string.server_not_running)
            startStopButton.setText(if (running) R.string.stop_server else R.string.start_server)
            startStopButton.setIconResource(if (running) android.R.drawable.ic_delete else android.R.drawable.ic_menu_upload)
            startStopButton.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (running) R.color.rose_stop else R.color.primary_light,
            )
            setDotColor(statusDot, if (running) R.color.success_dark else R.color.error_dark)
            if (running) {
                pulse(statusDot)
                urlText.visibility = View.VISIBLE
                qrImage.visibility = View.VISIBLE
            } else {
                pulseAnimator?.cancel()
                statusDot.alpha = 1f
                urlText.visibility = View.INVISIBLE
                qrImage.visibility = View.INVISIBLE
            }
        }

        ServerStateStore.url.observe(viewLifecycleOwner) { url ->
            urlText.text = url
            qrImage.setImageBitmap(QrBitmapGenerator.generateQrBitmap(url, 512))
        }

        ServerStateStore.networkName.observe(viewLifecycleOwner) { network ->
            networkText.text = "Network: $network"
        }

        ServerStateStore.transfers.observe(viewLifecycleOwner) { transfers ->
            val active = transfers.filter { it.status == TransferStatus.ACTIVE }
            val outgoingFromPhone = active.filter { it.direction == Direction.DOWNLOAD }.sumOf { it.speedBps }
            val incomingToPhone = active.filter { it.direction == Direction.UPLOAD }.sumOf { it.speedBps }
            quickStats.text = "↑ ${formatRate(outgoingFromPhone)}    ↓ ${formatRate(incomingToPhone)}"
        }
    }

    private fun pulse(view: View) {
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.3f).apply {
            duration = 900L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun formatRate(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 B/s"
        val kb = bytesPerSec / 1024.0
        if (kb < 1024) return "%.1f KB/s".format(kb)
        return "%.1f MB/s".format(kb / 1024.0)
    }

    private fun setDotColor(view: View, colorRes: Int) {
        val drawable = view.background as? GradientDrawable ?: return
        drawable.setColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    override fun onDestroyView() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDestroyView()
    }
}
