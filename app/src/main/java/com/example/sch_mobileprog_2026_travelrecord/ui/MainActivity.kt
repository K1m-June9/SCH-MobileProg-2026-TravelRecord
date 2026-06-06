package com.example.sch_mobileprog_2026_travelrecord.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import com.example.sch_mobileprog_2026_travelrecord.R
import com.example.sch_mobileprog_2026_travelrecord.data.DBHelper
import com.example.sch_mobileprog_2026_travelrecord.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isSortByDate = true // 현재 정렬 기준 토글 상태 필드 (기본값: 날짜 최신순)

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

        // 하단 탭 선택 시 화면 스위칭 리스너 설정 (백스택 관리 연동)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_list -> {
                    if (binding.bottomNavigation.selectedItemId != R.id.menu_list) {
                        switchFragment(listFragment, mapFragment, "LIST")
                    }
                    true
                }
                R.id.menu_map -> {
                    if (binding.bottomNavigation.selectedItemId != R.id.menu_map) {
                        switchFragment(mapFragment, listFragment, "MAP")
                    }
                    true
                }
                else -> false
            }
        }

        // 시스템 뒤로가기 이벤트 수렴 제어 (OnBackPressedCallback + BackStack 동적 제어)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavigation.selectedItemId == R.id.menu_map) {
                    // 현재 지도 탭에 있다면 백스택에서 지도 트랜잭션을 소멸시키고 목록 탭으로 복귀
                    supportFragmentManager.popBackStack("MAP", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    binding.bottomNavigation.selectedItemId = R.id.menu_list
                } else {
                    // 목록 탭에 있었다면 쌓여 있는 모든 탭 전환 백스택을 제거하고 앱 최종 종료
                    supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.fab.setOnClickListener { view ->
            val intent = Intent(this, EditActivity::class.java)
            startActivity(intent)
        }
    }

    // 탭 선택 시 트랜잭션 최적화를 수행하고 명시적으로 백스택에 트랜잭션 상태를 기록함 (백스택 검증 방어용)
    private fun switchFragment(targetFragment: Fragment, hideFragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction().apply {
            show(targetFragment)
            hide(hideFragment)
            addToBackStack(tag) // 트랜잭션 상태 백스택 등록 (명세 준수)
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
            R.id.action_sort_toggle -> {
                // 정렬 기준 토글 스위칭 및 프래그먼트 비동기 리로드 호출 (Task 5.1 연동)
                isSortByDate = !isSortByDate
                if (isSortByDate) {
                    item.title = "날짜순 정렬"
                    listFragment.changeSortOrderAndReload(DBHelper.SortOrder.DATE_DESC)
                } else {
                    item.title = "이름순 정렬"
                    listFragment.changeSortOrderAndReload(DBHelper.SortOrder.PLACE_ASC)
                }
                true
            }
            R.id.action_settings -> {
                // 앱 정보 안내 다이얼로그 소환 (명세서 요건 충족)
                AlertDialog.Builder(this)
                    .setTitle("앱 정보")
                    .setMessage("여행 여정 기록장 v1.0\n2026학년도 모바일 프로그래밍 기말 프로젝트")
                    .setPositiveButton("확인", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
