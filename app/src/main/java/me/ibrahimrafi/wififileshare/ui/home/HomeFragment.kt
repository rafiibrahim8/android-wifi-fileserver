package me.ibrahimrafi.wififileshare.ui.home

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Bundle
import android.os.Looper
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
    private val uiHandler = Handler(Looper.getMainLooper())
    private var isStartPending = false
    private var stateText: TextView? = null
    private var urlText: TextView? = null
    private var statusDot: View? = null
    private var startStopButton: MaterialButton? = null
    private var qrImage: ImageView? = null
    private var qrPlaceholder: TextView? = null
    private val startPendingTimeout = Runnable {
        if (ServerStateStore.isRunning.value != true && isAdded) {
            isStartPending = false
            renderRunningState(false)
        }
    }

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
            .onSuccess {
                setStartPending(true)
                FileServerService.start(requireContext())
            }
            .onFailure {
                Toast.makeText(requireContext(), getString(R.string.folder_not_selected), Toast.LENGTH_SHORT).show()
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val stateText = view.findViewById<TextView>(R.id.server_state)
        val urlText = view.findViewById<TextView>(R.id.server_url)
        val quickStats = view.findViewById<TextView>(R.id.quick_stats)
        val statusDot = view.findViewById<View>(R.id.status_dot)
        val startStopButton = view.findViewById<MaterialButton>(R.id.start_stop_button)
        val qrImage = view.findViewById<ImageView>(R.id.qr_image)
        val qrPlaceholder = view.findViewById<TextView>(R.id.qr_placeholder)
        this.stateText = stateText
        this.urlText = urlText
        this.statusDot = statusDot
        this.startStopButton = startStopButton
        this.qrImage = qrImage
        this.qrPlaceholder = qrPlaceholder

        startStopButton.setOnClickListener {
            val running = ServerStateStore.isRunning.value == true
            if (running) {
                setStartPending(false)
                FileServerService.stop(requireContext())
            } else {
                val rootUri = ServerPreferences(requireContext()).getConfig().rootUri
                if (rootUri == null) {
                    folderPicker.launch(FolderAccessManager.createFolderIntent())
                } else {
                    setStartPending(true)
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
            if (running) {
                setStartPending(false)
            }
            renderRunningState(running)
        }

        ServerStateStore.url.observe(viewLifecycleOwner) { url ->
            urlText.text = url
            qrImage.setImageBitmap(QrBitmapGenerator.generateQrBitmap(url, 512))
        }

        ServerStateStore.transfers.observe(viewLifecycleOwner) { transfers ->
            val active = transfers.filter { it.status == TransferStatus.ACTIVE }
            val outgoingFromPhone = active.filter { it.direction == Direction.DOWNLOAD }.sumOf { it.speedBps }
            val incomingToPhone = active.filter { it.direction == Direction.UPLOAD }.sumOf { it.speedBps }
            quickStats.text = getString(
                R.string.home_quick_stats_format,
                formatRate(outgoingFromPhone),
                formatRate(incomingToPhone),
            )
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

    private fun setStartPending(pending: Boolean) {
        isStartPending = pending
        uiHandler.removeCallbacks(startPendingTimeout)
        if (pending) {
            uiHandler.postDelayed(startPendingTimeout, 8_000L)
        }
        renderRunningState(ServerStateStore.isRunning.value == true)
    }

    private fun renderRunningState(running: Boolean) {
        val stateView = stateText ?: return
        val urlView = urlText ?: return
        val dotView = statusDot ?: return
        val button = startStopButton ?: return
        val qrView = qrImage ?: return
        val qrHint = qrPlaceholder ?: return

        if (running) {
            stateView.setText(R.string.server_running)
            button.setText(R.string.stop_server)
            button.setIconResource(R.drawable.ic_action_stop)
            button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.rose_stop)
            button.isEnabled = true
            setDotColor(dotView, R.color.success_dark)
            pulse(dotView)
            urlView.visibility = View.VISIBLE
            qrView.visibility = View.VISIBLE
            qrHint.visibility = View.GONE
            return
        }

        pulseAnimator?.cancel()
        dotView.alpha = 1f
        urlView.visibility = View.INVISIBLE
        qrView.visibility = View.INVISIBLE
        qrHint.visibility = View.VISIBLE
        button.setIconResource(R.drawable.ic_action_start)
        button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary_light)
        setDotColor(dotView, R.color.error_dark)

        if (isStartPending) {
            stateView.setText(R.string.server_starting)
            button.setText(R.string.starting_server)
            button.isEnabled = false
        } else {
            stateView.setText(R.string.server_not_running)
            button.setText(R.string.start_server)
            button.isEnabled = true
        }
    }

    override fun onDestroyView() {
        uiHandler.removeCallbacks(startPendingTimeout)
        stateText = null
        urlText = null
        statusDot = null
        startStopButton = null
        qrImage = null
        qrPlaceholder = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDestroyView()
    }
}
