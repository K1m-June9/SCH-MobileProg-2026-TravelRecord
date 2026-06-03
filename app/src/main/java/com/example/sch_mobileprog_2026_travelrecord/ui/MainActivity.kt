package com.example.sch_mobileprog_2026_travelrecord.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import com.example.sch_mobileprog_2026_travelrecord.R
import com.example.sch_mobileprog_2026_travelrecord.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 탭 구성을 위한 프래그먼트 인스턴스 생성
    private val listFragment = TravelListFragment()
    private val mapFragment = MapOverviewFragment()

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

        // 초기화 시점 (onCreate) - 프래그먼트 show/hide 구조 설계에 따른 add 및 초기 hide 상태 제어
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, listFragment, "LIST")
            add(R.id.fragment_container, mapFragment, "MAP")
            hide(mapFragment) // 기본 첫 화면은 리스트이므로 지도는 숨김
            commit()
        }

        // 하단 탭 선택 시 화면 스위칭 리스너 설정
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_list -> {
                    switchFragment(listFragment, mapFragment)
                    true
                }
                R.id.menu_map -> {
                    switchFragment(mapFragment, listFragment)
                    true
                }
                else -> false
            }
        }

        // 시스템 뒤로가기 이벤트 수렴 제어 (OnBackPressedCallback)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavigation.selectedItemId == R.id.menu_map) {
                    // 현재 지도 탭에 있다면 목록 탭으로 강제 이동 (앱 종료 방지)
                    binding.bottomNavigation.selectedItemId = R.id.menu_list
                } else {
                    // 목록 탭에 있었다면 정상적으로 앱 종료 처리
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.fab.setOnClickListener { view ->
            // TODO: 추가 모드 EditActivity 진입 인텐트 연동 예정
        }
    }

    // 탭 선택 시 트랜잭션 최적화를 위한 show / hide 유틸리티
    private fun switchFragment(targetFragment: Fragment, hideFragment: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            show(targetFragment)
            hide(hideFragment)
            commit()
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
