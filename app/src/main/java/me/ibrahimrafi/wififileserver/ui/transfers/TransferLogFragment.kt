package me.ibrahimrafi.wififileserver.ui.transfers

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.TextView
import me.ibrahimrafi.wififileserver.ui.snack
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.chip.Chip
import me.ibrahimrafi.wififileserver.R
import me.ibrahimrafi.wififileserver.model.Direction
import me.ibrahimrafi.wififileserver.model.TransferItem
import me.ibrahimrafi.wififileserver.model.TransferStatus
import me.ibrahimrafi.wififileserver.server.FileServerService

class TransferLogFragment : Fragment(R.layout.fragment_transfers) {
    private val viewModel: TransferLogViewModel by viewModels()
    private val adapter = TransferAdapter { item -> cancelUpload(item) }
    private var allTransfers: List<TransferItem> = emptyList()
    private var serverBinder: FileServerService.LocalBinder? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serverBinder = binder as? FileServerService.LocalBinder
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serverBinder = null
        }
    }

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

        val applyFilter: (Runnable?) -> Unit = { onCommit ->
            val filtered = when {
                chipDownloads.isChecked -> allTransfers.filter { it.direction == Direction.DOWNLOAD }
                chipUploads.isChecked -> allTransfers.filter { it.direction == Direction.UPLOAD }
                chipCompleted.isChecked -> allTransfers.filter { it.status == TransferStatus.COMPLETED }
                chipFailed.isChecked -> allTransfers.filter { it.status == TransferStatus.FAILED }
                chipCancelled.isChecked -> allTransfers.filter { it.status == TransferStatus.CANCELLED }
                else -> allTransfers
            }
            adapter.submitList(filtered, onCommit)
            emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }

        listOf(chipAll, chipDownloads, chipUploads, chipCompleted, chipFailed, chipCancelled).forEach { chip ->
            chip.setOnCheckedChangeListener { _, _ -> applyFilter(null) }
        }

        viewModel.transfers.observe(viewLifecycleOwner) { newList ->
            val previousIds = allTransfers.mapTo(HashSet()) { it.id }
            val hasNewItem = newList.any { it.id !in previousIds }
            val wasAtTop = recycler.computeVerticalScrollOffset() == 0
            allTransfers = newList
            applyFilter(
                if (hasNewItem && wasAtTop) Runnable { recycler.scrollToPosition(0) } else null,
            )
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

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), FileServerService::class.java)
        requireContext().bindService(intent, serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        runCatching { requireContext().unbindService(serviceConnection) }
        serverBinder = null
    }

    private fun cancelUpload(item: TransferItem) {
        val binder = serverBinder
        if (binder == null) {
            snack(R.string.cancel_upload_failed)
            return
        }
        val ok = binder.cancelUpload(item.relativePath)
        if (!ok) snack(R.string.cancel_upload_failed)
    }
}
