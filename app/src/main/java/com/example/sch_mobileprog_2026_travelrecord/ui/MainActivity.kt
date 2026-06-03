package com.example.sch_mobileprog_2026_travelrecord.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.Menu
import android.view.MenuItem
import com.example.sch_mobileprog_2026_travelrecord.R
import com.example.sch_mobileprog_2026_travelrecord.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener { view ->
            // TODO: 추가 모드 EditActivity 진입 인텐트 연동 예정
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // MainActivity 상단 옵션 메뉴 활성화 (정렬 토글 및 앱 정보)
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // TODO: 앱 정보 다이얼로그 노출 예정
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
