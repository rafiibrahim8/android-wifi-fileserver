package me.ibrahimrafi.wififileserver.ui.transfers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import me.ibrahimrafi.wififileserver.R
import me.ibrahimrafi.wififileserver.model.Direction
import me.ibrahimrafi.wififileserver.model.TransferItem
import me.ibrahimrafi.wififileserver.model.TransferStatus
import java.util.Locale

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
        private val iconBubble = itemView.findViewById<View>(R.id.icon_bubble)
        private val iconImage = itemView.findViewById<ImageView>(R.id.icon_image)
        private val fileName = itemView.findViewById<TextView>(R.id.file_name)
        private val detail = itemView.findViewById<TextView>(R.id.detail)
        private val progress = itemView.findViewById<LinearProgressIndicator>(R.id.progress)
        private val cancelUpload = itemView.findViewById<TextView>(R.id.cancel_upload)

        fun bind(item: TransferItem, onCancelUpload: (TransferItem) -> Unit) {
            val ctx = itemView.context
            fileName.text = item.fileName

            val style = bubbleStyle(item)
            iconBubble.setBackgroundResource(style.bubbleBg)
            iconImage.setImageResource(style.iconRes)
            iconImage.imageTintList = ContextCompat.getColorStateList(ctx, style.tintRes)
            iconBubble.contentDescription = ctx.getString(style.contentDescRes)

            val isActive = item.status == TransferStatus.ACTIVE
            val pct = if (item.totalBytes > 0L) (item.transferredBytes * 100 / item.totalBytes).toInt() else 0
            detail.text = when (item.status) {
                TransferStatus.ACTIVE -> ctx.getString(
                    R.string.transfer_detail_active,
                    formatRate(item.speedBps),
                    pct.coerceIn(0, 100),
                    formatBytes(item.transferredBytes),
                    formatBytes(item.totalBytes),
                )
                TransferStatus.COMPLETED -> ctx.getString(
                    R.string.transfer_detail_done,
                    ctx.getString(R.string.transfer_status_completed),
                    formatBytes(item.totalBytes),
                )
                TransferStatus.FAILED -> ctx.getString(
                    R.string.transfer_detail_done,
                    ctx.getString(R.string.transfer_status_failed),
                    formatBytes(item.transferredBytes),
                )
                TransferStatus.CANCELLED -> ctx.getString(
                    R.string.transfer_detail_done,
                    ctx.getString(R.string.transfer_status_cancelled),
                    formatBytes(item.transferredBytes),
                )
            }

            if (isActive) {
                progress.visibility = View.VISIBLE
                progress.setProgressCompat(pct.coerceIn(0, 100), false)
                progress.setIndicatorColor(ContextCompat.getColor(ctx, style.tintRes))
            } else {
                progress.visibility = View.GONE
            }

            val canCancel = item.direction == Direction.UPLOAD &&
                item.status == TransferStatus.ACTIVE &&
                item.relativePath.isNotBlank()
            cancelUpload.visibility = if (canCancel) View.VISIBLE else View.GONE
            cancelUpload.setOnClickListener {
                if (canCancel) onCancelUpload(item)
            }
        }

        private data class BubbleStyle(
            val bubbleBg: Int,
            val iconRes: Int,
            val tintRes: Int,
            val contentDescRes: Int,
        )

        private fun bubbleStyle(item: TransferItem): BubbleStyle = when (item.status) {
            TransferStatus.ACTIVE -> if (item.direction == Direction.DOWNLOAD) {
                BubbleStyle(
                    R.drawable.bg_icon_bubble_accent, R.drawable.ic_transfer_up,
                    R.color.accent, R.string.cd_transfer_download,
                )
            } else {
                BubbleStyle(
                    R.drawable.bg_icon_bubble_success, R.drawable.ic_transfer_down,
                    R.color.success, R.string.cd_transfer_upload,
                )
            }
            TransferStatus.COMPLETED -> BubbleStyle(
                R.drawable.bg_icon_bubble_success, R.drawable.ic_check,
                R.color.success, R.string.cd_transfer_completed,
            )
            TransferStatus.FAILED -> BubbleStyle(
                R.drawable.bg_icon_bubble_danger, R.drawable.ic_close,
                R.color.danger, R.string.cd_transfer_failed,
            )
            TransferStatus.CANCELLED -> BubbleStyle(
                R.drawable.bg_icon_bubble_neutral, R.drawable.ic_close,
                R.color.ink_3, R.string.cd_transfer_cancelled,
            )
        }

        private fun formatRate(speedBps: Long): String {
            if (speedBps <= 0L) return "0 B/s"
            val kb = speedBps / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB/s", kb)
            return String.format(Locale.US, "%.1f MB/s", kb / 1024)
        }

        private fun formatBytes(size: Long): String {
            if (size <= 0L) return "0 B"
            val kb = size / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            return String.format(Locale.US, "%.2f GB", mb / 1024.0)
        }
    }

    private object Diff : DiffUtil.ItemCallback<TransferItem>() {
        override fun areItemsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TransferItem, newItem: TransferItem): Boolean = oldItem == newItem
    }
}
