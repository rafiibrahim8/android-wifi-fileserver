package me.ibrahimrafi.wififileshare.ui.home

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.button.MaterialButton
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.qr.QrBitmapGenerator
import me.ibrahimrafi.wififileshare.ui.snack
import me.ibrahimrafi.wififileshare.server.FileServerService
import me.ibrahimrafi.wififileshare.storage.FolderAccessManager
import me.ibrahimrafi.wififileshare.storage.ServerPreferences

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val viewModel: HomeViewModel by viewModels()
    private var pulseAnimator: ObjectAnimator? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var isStartPending = false
    private var titleLine2: TextView? = null
    private var statusPill: View? = null
    private var stateText: TextView? = null
    private var urlText: TextView? = null
    private var statusDot: View? = null
    private var startStopButton: MaterialButton? = null
    private var qrImage: ImageView? = null
    private var qrPlaceholder: TextView? = null
    private var copyButton: TextView? = null
    private var setupCard: View? = null
    private var heroCard: View? = null
    private var statsRow: View? = null
    private var outgoingValue: TextView? = null
    private var outgoingSub: TextView? = null
    private var incomingValue: TextView? = null
    private var incomingSub: TextView? = null
    private val startPendingTimeout = Runnable {
        if (viewModel.isRunning.value != true && isAdded) {
            isStartPending = false
            renderRunningState(false)
        }
    }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            snack(R.string.folder_not_selected)
            return@registerForActivityResult
        }
        val uri = result.data?.data
        if (uri == null) {
            snack(R.string.folder_not_selected)
            return@registerForActivityResult
        }
        runCatching { FolderAccessManager.persistUri(requireContext(), uri) }
            .onSuccess {
                renderRootState()
            }
            .onFailure {
                snack(R.string.folder_not_selected)
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleLine2 = view.findViewById(R.id.home_title_l2)
        statusPill = view.findViewById(R.id.status_pill)
        stateText = view.findViewById(R.id.server_state)
        urlText = view.findViewById(R.id.server_url)
        statusDot = view.findViewById(R.id.status_dot)
        startStopButton = view.findViewById(R.id.start_stop_button)
        qrImage = view.findViewById(R.id.qr_image)
        qrPlaceholder = view.findViewById(R.id.qr_placeholder)
        copyButton = view.findViewById(R.id.copy_button)
        outgoingValue = view.findViewById(R.id.stat_outgoing_value)
        outgoingSub = view.findViewById(R.id.stat_outgoing_sub)
        incomingValue = view.findViewById(R.id.stat_incoming_value)
        incomingSub = view.findViewById(R.id.stat_incoming_sub)
        setupCard = view.findViewById(R.id.setup_card)
        heroCard = view.findViewById(R.id.hero_card)
        statsRow = view.findViewById(R.id.stats_row)

        view.findViewById<View>(R.id.choose_folder_button).setOnClickListener {
            folderPicker.launch(FolderAccessManager.createFolderIntent())
        }
        renderRootState()

        startStopButton?.setOnClickListener {
            val running = viewModel.isRunning.value == true
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

        val copyAction = View.OnClickListener {
            if (viewModel.isRunning.value != true) return@OnClickListener
            val text = urlText?.text?.toString().orEmpty()
            if (text.isBlank()) return@OnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label_url), text))
            snack(R.string.url_copied)
        }
        copyButton?.setOnClickListener(copyAction)
        urlText?.setOnClickListener(copyAction)

        var lastRunningState: Boolean? = null
        viewModel.isRunning.observe(viewLifecycleOwner) { running ->
            if (running) setStartPending(false)
            renderRunningState(running)
            if (lastRunningState != null && lastRunningState != running) {
                val msg = getString(if (running) R.string.cd_status_running else R.string.cd_status_stopped)
                statusPill?.announceForAccessibility(msg)
            }
            lastRunningState = running
        }

        viewModel.url.observe(viewLifecycleOwner) { url ->
            urlText?.text = url
            viewLifecycleOwner.lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.Default) {
                    QrBitmapGenerator.generateQrBitmap(url, 512)
                }
                qrImage?.setImageBitmap(bitmap)
            }
        }

        viewModel.outgoing.observe(viewLifecycleOwner) { stat ->
            outgoingValue?.text = formatRate(stat.rateBps)
            outgoingSub?.text = activeSubtitle(stat.count)
        }
        viewModel.incoming.observe(viewLifecycleOwner) { stat ->
            incomingValue?.text = formatRate(stat.rateBps)
            incomingSub?.text = activeSubtitle(stat.count)
        }
    }

    private fun renderRootState() {
        val haveRoot = ServerPreferences(requireContext()).getConfig().rootUri != null
        setupCard?.visibility = if (haveRoot) View.GONE else View.VISIBLE
        heroCard?.visibility = if (haveRoot) View.VISIBLE else View.GONE
        statsRow?.visibility = if (haveRoot) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        renderRootState()
    }

    private fun activeSubtitle(count: Int): String =
        if (count == 0) getString(R.string.home_no_active)
        else resources.getQuantityString(R.plurals.home_active_transfers, count, count)

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
        if (kb < 1024) return String.format(java.util.Locale.US, "%.1f KB/s", kb)
        return String.format(java.util.Locale.US, "%.1f MB/s", kb / 1024.0)
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
        renderRunningState(viewModel.isRunning.value == true)
    }

    private fun renderRunningState(running: Boolean) {
        val ctx = context ?: return
        val pill = statusPill ?: return
        val stateView = stateText ?: return
        val urlView = urlText ?: return
        val dotView = statusDot ?: return
        val button = startStopButton ?: return
        val qrView = qrImage ?: return
        val qrHint = qrPlaceholder ?: return
        val l2 = titleLine2 ?: return
        val copy = copyButton

        if (running) {
            l2.setText(R.string.home_title_line2_on)
            l2.setTextColor(ContextCompat.getColor(ctx, R.color.accent))

            pill.setBackgroundResource(R.drawable.bg_status_pill_running)
            stateView.setText(R.string.status_running)
            stateView.setTextColor(ContextCompat.getColor(ctx, R.color.success))
            setDotColor(dotView, R.color.success)
            pulse(dotView)

            urlView.visibility = View.VISIBLE
            qrView.visibility = View.VISIBLE
            qrHint.visibility = View.GONE
            copy?.isEnabled = true
            copy?.alpha = 1f

            button.setText(R.string.stop_server)
            button.setIconResource(R.drawable.ic_action_stop)
            button.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.danger)
            button.setTextColor(ContextCompat.getColor(ctx, R.color.on_filled_button))
            button.iconTint = ContextCompat.getColorStateList(ctx, R.color.on_filled_button)
            button.isEnabled = true
            return
        }

        pulseAnimator?.cancel()
        dotView.alpha = 1f

        l2.setText(R.string.home_title_line2_off)
        l2.setTextColor(ContextCompat.getColor(ctx, R.color.ink_3))

        pill.setBackgroundResource(R.drawable.bg_status_pill_stopped)
        setDotColor(dotView, R.color.danger)
        stateView.setTextColor(ContextCompat.getColor(ctx, R.color.danger))

        urlView.visibility = View.INVISIBLE
        qrView.visibility = View.INVISIBLE
        qrHint.visibility = View.VISIBLE
        copy?.isEnabled = false
        copy?.alpha = 0.4f

        button.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.accent)
        button.setTextColor(ContextCompat.getColor(ctx, R.color.on_filled_button))
        button.iconTint = ContextCompat.getColorStateList(ctx, R.color.on_filled_button)
        button.setIconResource(R.drawable.ic_action_start)

        if (isStartPending) {
            stateView.setText(R.string.status_starting)
            button.setText(R.string.starting_server)
            button.isEnabled = false
        } else {
            stateView.setText(R.string.status_stopped)
            button.setText(R.string.start_server)
            button.isEnabled = true
        }
    }

    override fun onDestroyView() {
        uiHandler.removeCallbacks(startPendingTimeout)
        titleLine2 = null
        statusPill = null
        stateText = null
        urlText = null
        statusDot = null
        startStopButton = null
        qrImage = null
        qrPlaceholder = null
        copyButton = null
        setupCard = null
        heroCard = null
        statsRow = null
        outgoingValue = null
        outgoingSub = null
        incomingValue = null
        incomingSub = null
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDestroyView()
    }
}
