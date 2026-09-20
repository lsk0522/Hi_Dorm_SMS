package com.hidorm.smsrelay.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hidorm.smsrelay.HiDormRelayApp
import com.hidorm.smsrelay.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val adapter = HistoryAdapter()
    private val app by lazy { application as HiDormRelayApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnClearHistory.setOnClickListener {
            showClearConfirmDialog()
        }

        observeHistory()
    }

    private fun observeHistory() {
        app.database.messageDao().getRecentMessagesLiveData(100).observe(this) { list ->
            if (list.isNullOrEmpty()) {
                binding.rvHistory.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
            } else {
                binding.rvHistory.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                adapter.submitList(list)
            }
        }
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("전송 내역 삭제")
            .setMessage("저장된 전송 및 릴레이 내역을 모두 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        app.database.messageDao().deleteAllMessages()
                    }
                    Toast.makeText(this@HistoryActivity, "전송 내역이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
