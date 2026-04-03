package me.ibrahimrafi.wififileshare.ui.transfers

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.model.Direction
import me.ibrahimrafi.wififileshare.model.ServerStateStore
import me.ibrahimrafi.wififileshare.model.TransferItem
import me.ibrahimrafi.wififileshare.model.TransferStatus
import me.ibrahimrafi.wififileshare.storage.ServerPreferences
import me.ibrahimrafi.wififileshare.server.percentEncodePath
import java.net.HttpURLConnection
import java.net.URL

class TransferLogFragment : Fragment(R.layout.fragment_transfers) {
    private val adapter = TransferAdapter { item -> cancelUpload(item) }
    private var allTransfers: List<TransferItem> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.transfers_recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val clearButton = view.findViewById<View>(R.id.clear_button)
        clearButton.setOnClickListener {
            ServerStateStore.clearTransfers()
            ServerStateStore.clearLogs()
        }

        val chipAll = view.findViewById<Chip>(R.id.chip_all)
        val chipDownloads = view.findViewById<Chip>(R.id.chip_downloads)
        val chipUploads = view.findViewById<Chip>(R.id.chip_uploads)
        val chipCompleted = view.findViewById<Chip>(R.id.chip_completed)
        val chipFailed = view.findViewById<Chip>(R.id.chip_failed)

        val applyFilter = {
            val filtered = when {
                chipDownloads.isChecked -> allTransfers.filter { it.direction == Direction.DOWNLOAD }
                chipUploads.isChecked -> allTransfers.filter { it.direction == Direction.UPLOAD }
                chipCompleted.isChecked -> allTransfers.filter { it.status == TransferStatus.COMPLETED }
                chipFailed.isChecked -> allTransfers.filter { it.status == TransferStatus.FAILED }
                else -> allTransfers
            }
            adapter.submitList(filtered)
        }

        listOf(chipAll, chipDownloads, chipUploads, chipCompleted, chipFailed).forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ -> applyFilter() }
        }

        ServerStateStore.transfers.observe(viewLifecycleOwner) {
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
                ServerStateStore.removeTransfer(item.id)
            }
        }).attachToRecyclerView(recycler)
    }

    private fun cancelUpload(item: TransferItem) {
        val ctx = context ?: return
        val port = ServerPreferences(ctx).getConfig().port
        val encodedPath = percentEncodePath(item.relativePath)
        Thread {
            runCatching {
                val url = URL("http://127.0.0.1:$port/upload-cancel/$encodedPath")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3_000
                    readTimeout = 5_000
                    doOutput = true
                }
                connection.outputStream.use { }
                connection.responseCode
                connection.disconnect()
            }
        }.start()
    }
}
