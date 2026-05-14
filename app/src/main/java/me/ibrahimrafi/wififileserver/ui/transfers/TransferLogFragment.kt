package me.ibrahimrafi.wififileserver.ui.transfers

import android.os.Bundle
import android.view.View
import android.widget.TextView
import me.ibrahimrafi.wififileserver.ui.snack
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.chip.Chip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ibrahimrafi.wififileserver.R
import me.ibrahimrafi.wififileserver.model.Direction
import me.ibrahimrafi.wififileserver.model.TransferItem
import me.ibrahimrafi.wififileserver.model.TransferStatus
import me.ibrahimrafi.wififileserver.storage.ServerPreferences
import me.ibrahimrafi.wififileserver.server.percentEncodePath
import java.net.HttpURLConnection
import java.net.URL

class TransferLogFragment : Fragment(R.layout.fragment_transfers) {
    private val viewModel: TransferLogViewModel by viewModels()
    private val adapter = TransferAdapter { item -> cancelUpload(item) }
    private var allTransfers: List<TransferItem> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.transfers_recycler)
        val emptyState = view.findViewById<TextView>(R.id.transfers_empty)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        val clearButton = view.findViewById<View>(R.id.clear_button)
        clearButton.setOnClickListener {
            viewModel.clearTransfers()
            viewModel.clearLogs()
        }

        val chipAll = view.findViewById<Chip>(R.id.chip_all)
        val chipDownloads = view.findViewById<Chip>(R.id.chip_downloads)
        val chipUploads = view.findViewById<Chip>(R.id.chip_uploads)
        val chipCompleted = view.findViewById<Chip>(R.id.chip_completed)
        val chipFailed = view.findViewById<Chip>(R.id.chip_failed)
        val chipCancelled = view.findViewById<Chip>(R.id.chip_cancelled)

        val applyFilter = {
            val filtered = when {
                chipDownloads.isChecked -> allTransfers.filter { it.direction == Direction.DOWNLOAD }
                chipUploads.isChecked -> allTransfers.filter { it.direction == Direction.UPLOAD }
                chipCompleted.isChecked -> allTransfers.filter { it.status == TransferStatus.COMPLETED }
                chipFailed.isChecked -> allTransfers.filter { it.status == TransferStatus.FAILED }
                chipCancelled.isChecked -> allTransfers.filter { it.status == TransferStatus.CANCELLED }
                else -> allTransfers
            }
            adapter.submitList(filtered)
            emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }

        listOf(chipAll, chipDownloads, chipUploads, chipCompleted, chipFailed, chipCancelled).forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ -> applyFilter() }
        }

        viewModel.transfers.observe(viewLifecycleOwner) {
            allTransfers = it
            applyFilter()
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val item = adapter.currentList.getOrNull(viewHolder.bindingAdapterPosition) ?: return
                viewModel.removeTransfer(item.id)
            }
        }).attachToRecyclerView(recycler)
    }

    private fun cancelUpload(item: TransferItem) {
        val ctx = context ?: return
        val port = ServerPreferences(ctx).getConfig().port
        val encodedPath = percentEncodePath(item.relativePath)
        val targetUrl = "http://127.0.0.1:$port/upload-cancel/$encodedPath"

        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                sendCancelWithRetry(targetUrl)
            }
            if (!ok) {
                snack(R.string.cancel_upload_failed)
            }
        }
    }

    private suspend fun sendCancelWithRetry(targetUrl: String): Boolean {
        var attempt = 0
        var backoff = 400L
        while (attempt < CANCEL_MAX_ATTEMPTS) {
            val code = postCancel(targetUrl)
            if (code in 200..299 || code == 404) return true
            attempt++
            if (attempt >= CANCEL_MAX_ATTEMPTS) return false
            delay(backoff)
            backoff *= 2
        }
        return false
    }

    private fun postCancel(targetUrl: String): Int {
        return runCatching {
            val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3_000
                readTimeout = 5_000
                doOutput = true
            }
            connection.outputStream.use { }
            val code = connection.responseCode
            connection.disconnect()
            code
        }.getOrDefault(-1)
    }

    companion object {
        private const val CANCEL_MAX_ATTEMPTS = 3
    }
}
