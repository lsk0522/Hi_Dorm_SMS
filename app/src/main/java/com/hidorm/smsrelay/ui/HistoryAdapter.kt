package com.hidorm.smsrelay.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hidorm.smsrelay.data.local.MessageEntity
import com.hidorm.smsrelay.databinding.ItemMessageHistoryBinding
import com.hidorm.smsrelay.util.PhoneNumberNormalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<MessageEntity>()
    private val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA)

    fun submitList(newItems: List<MessageEntity>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemMessageHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MessageEntity) {
            binding.tvRecipient.text = PhoneNumberNormalizer.formatDisplay(item.recipientPhone)
            binding.tvContent.text = item.content
            binding.tvTime.text = dateFormat.format(Date(item.sentAt ?: item.createdAt))

            // 상태 뱃지 디자인 (Apple Pill Badge)
            binding.tvStatusBadge.text = item.status
            val badgeColor = when (item.status) {
                "SENT", "DELIVERED" -> Color.parseColor("#30D158") // Apple Green
                "FAILED" -> Color.parseColor("#FF453A")            // Apple Red
                "DISPATCHED" -> Color.parseColor("#0A84FF")        // Apple Blue
                else -> Color.parseColor("#FF9F0A")                // Apple Orange
            }

            val badgeDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50f // Pill Shape
                setColor(badgeColor)
            }
            binding.tvStatusBadge.background = badgeDrawable

            if (!item.errorMessage.isNullOrBlank() && item.status == "FAILED") {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = "실패 사유: ${item.errorMessage} (${item.resultCode ?: ""})"
            } else {
                binding.tvError.visibility = View.GONE
            }
        }
    }
}
