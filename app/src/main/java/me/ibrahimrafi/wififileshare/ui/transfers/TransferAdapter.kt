package me.ibrahimrafi.wififileshare.ui.transfers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import me.ibrahimrafi.wififileshare.R
import me.ibrahimrafi.wififileshare.model.Direction
import me.ibrahimrafi.wififileshare.model.TransferItem
import me.ibrahimrafi.wififileshare.model.TransferStatus

class TransferAdapter(
    private val onCancelUpload: (TransferItem) -> Unit,
) : ListAdapter<TransferItem, TransferAdapter.TransferViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransferViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transfer, parent, false)
        return TransferViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransferViewHolder, position: Int) {
        holder.bind(getItem(position), onCancelUpload)
    }

    class TransferViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileName = itemView.findViewById<TextView>(R.id.file_name)
        private val detail = itemView.findViewById<TextView>(R.id.detail)
        private val progress = itemView.findViewById<LinearProgressIndicator>(R.id.progress)
        private val cancelUpload = itemView.findViewById<TextView>(R.id.cancel_upload)

        fun bind(item: TransferItem, onCancelUpload: (TransferItem) -> Unit) {
            fileName.text = item.fileName
            val pct = if (item.totalBytes > 0L) (item.transferredBytes * 100 / item.totalBytes).toInt() else 0
            progress.progress = pct.coerceIn(0, 100)
            progress.setIndicatorColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (item.direction == Direction.DOWNLOAD) R.color.primary_dark else R.color.amber_upload,
                ),
            )
            detail.text = buildString {
                append(if (item.direction == Direction.UPLOAD) "↓ " else "↑ ")
                append("${formatBytes(item.transferredBytes)} / ${formatBytes(item.totalBytes)}")
                append(" · ${formatRate(item.speedBps)}")
                append(" · ${item.clientIp}")
                append(" · ${statusLabel(item.status)}")
            }
            progress.visibility = if (
                item.status == TransferStatus.FAILED ||
                item.status == TransferStatus.CANCELLED
            ) View.GONE else View.VISIBLE

            val canCancel = item.direction == Direction.UPLOAD &&
                item.status == TransferStatus.ACTIVE &&
                item.relativePath.isNotBlank()
            cancelUpload.visibility = if (canCancel) View.VISIBLE else View.GONE
            cancelUpload.setOnClickListener {
                if (canCancel) {
                    onCancelUpload(item)
                }
            }
        }

        private fun formatRate(speedBps: Long): String {
            if (speedBps <= 0L) return "0 B/s"
            val kb = speedBps / 1024.0
            if (kb < 1024) return "%.1f KB/s".format(kb)
            return "%.1f MB/s".format(kb / 1024)
        }

        private fun formatBytes(size: Long): String {
            if (size <= 0L) return "0 B"
            val kb = size / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            return "%.2f GB".format(mb / 1024.0)
        }

        private fun statusLabel(status: TransferStatus): String {
            return when (status) {
                TransferStatus.ACTIVE -> "Active"
                TransferStatus.COMPLETED -> "Completed"
                TransferStatus.FAILED -> "Failed"
                TransferStatus.CANCELLED -> "Cancelled"
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TransferItem>() {
        override fun areItemsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean = oldItem == newItem
    }
}
