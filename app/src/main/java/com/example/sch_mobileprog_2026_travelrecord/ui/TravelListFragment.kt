package com.example.sch_mobileprog_2026_travelrecord.ui

import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sch_mobileprog_2026_travelrecord.R
import com.example.sch_mobileprog_2026_travelrecord.data.DBHelper
import com.example.sch_mobileprog_2026_travelrecord.data.TravelRecord
import com.example.sch_mobileprog_2026_travelrecord.databinding.FragmentTravelListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TravelListFragment : Fragment() {

    private var _binding: FragmentTravelListBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DBHelper
    private lateinit var travelAdapter: TravelAdapter

    // 기본 정렬 순서 정의 (방문일 최신순)
    private var currentSortOrder = DBHelper.SortOrder.DATE_DESC

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTravelListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        dbHelper = DBHelper.getInstance(requireContext())
        
        // 어댑터 인스턴스 초기화 및 클릭 콜백 리스너 바인딩
        travelAdapter = TravelAdapter(viewLifecycleOwner.lifecycleScope) { no ->
            val intent = Intent(requireContext(), EditActivity::class.java).apply {
                putExtra("no", no)
                putExtra("read_only", true)
            }
            startActivity(intent)
        }
        
        binding.recyclerView.adapter = travelAdapter
    }

    override fun onResume() {
        super.onResume()
        // 화면으로 포커스가 복귀할 때마다 실시간으로 DB 데이터를 읽어와 목록을 최신화
        loadTravelRecords()
    }

    /**
     * 비동기 코루틴 백그라운드 스레드에서 DB 조회 쿼리를 실행함.
     * DB 조회 작업 중에는 화면에 ProgressBar를 띄워 피드백을 주며, 데이터가 비어 있을 경우 Dummy 레코드를 주입함.
     */
    private fun loadTravelRecords() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 로딩바 노출
            binding.progressBar.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.textEmpty.visibility = View.GONE

            try {
                // 1단계: DB 전체 리스트 조회
                var records = dbHelper.getAllRecords(currentSortOrder)

                // 2단계: 가짜 데이터 자동 주입 예외 처리 (최초 1회 검증용)
                if (records.isEmpty()) {
                    val dummyRecord = TravelRecord(
                        place = "서울 시청",
                        visitDate = "2026-06-05",
                        memo = "기말 프로젝트 리스트 뷰 및 데이터베이스 CRUD 기능 연동 테스트를 위해 자동으로 생성된 가짜 데이터입니다.",
                        photoUri = null, // 이미지 부재 시 default_image.jpg 바인딩 검증
                        latitude = 37.5665,
                        longitude = 126.9780
                    )
                    dbHelper.insertRecord(dummyRecord)
                    // 주입 완료 후 즉각 재조회
                    records = dbHelper.getAllRecords(currentSortOrder)
                }

                // 3단계: 화면 렌더링 상태 스위칭
                if (records.isEmpty()) {
                    binding.recyclerView.visibility = View.GONE
                    binding.textEmpty.visibility = View.VISIBLE
                } else {
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.textEmpty.visibility = View.GONE
                    travelAdapter.submitList(records)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "데이터 로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                // 로딩바 제거
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * 외부(MainActivity)에서 호출하여 정렬 기준을 갱신하고 DB 비동기 조회를 재수행함.
     */
    fun changeSortOrderAndReload(newOrder: DBHelper.SortOrder) {
        currentSortOrder = newOrder
        loadTravelRecords()
    }

    /**
     * 컨텍스트 메뉴 아이템('수정' 또는 '삭제') 클릭 시 콜백 이벤트 수신
     */
    override fun onContextItemSelected(item: MenuItem): Boolean {
        // 어댑터로부터 롱클릭된 아이템의 고유 번호(no) 획득
        val no = travelAdapter.longClickedNo ?: return super.onContextItemSelected(item)
        
        return when (item.itemId) {
            R.id.menu_context_edit -> {
                val intent = Intent(requireContext(), EditActivity::class.java).apply {
                    putExtra("no", no)
                    putExtra("read_only", false)
                }
                startActivity(intent)
                true
            }
            R.id.menu_context_delete -> {
                // 경고 알림 팝업 소환
                showDeleteDialog(no)
                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    /**
     * 기록 삭제 전 경고 알림 다이얼로그를 표출함.
     * 사용자가 '확인'을 누를 때에만 SQLite 데이터베이스 레코드와 단말기 내부 저장소의 물리적 이미지 파일을 동시 제거함.
     */
    private fun showDeleteDialog(no: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("이 여행 기록을 삭제하시겠습니까?\n삭제된 데이터와 사진 파일은 영구히 복구할 수 없습니다.")
            .setPositiveButton("확인") { _, _ ->
                performDeleteRecord(no)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 실제 물리 이미지 파일 삭제 및 DB 행(Row) 삭제 연산을 비동기로 수행하고,
     * notifyItemRemoved 애니메이션을 엮어 리사이클러뷰 화면을 실시간 업데이트함.
     */
    private fun performDeleteRecord(no: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1단계: 어댑터 내 해당 데이터 검색
                val items = travelAdapter.getItems()
                val position = items.indexOfFirst { it.no == no }
                if (position == -1) return@launch

                val targetRecord = items[position]

                // 2단계: 백그라운드 스레드에서 물리적 이미지 파일 안전하게 동반 소멸 처리 (AGENT 지침 준수)
                if (!targetRecord.photoUri.isNullOrEmpty()) {
                    withContext(Dispatchers.IO) {
                        val uri = Uri.parse(targetRecord.photoUri)
                        val file = if (uri.scheme == "file") {
                            File(uri.path ?: "")
                        } else {
                            File(targetRecord.photoUri)
                        }
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                }

                // 3단계: SQLite 데이터베이스 내 DELETE 쿼리 비동기 수행
                dbHelper.deleteRecord(no)

                // 4단계: 리사이클러뷰 실시간 삭제 애니메이션(notifyItemRemoved) 트리거 및 상태 분기
                travelAdapter.removeItem(position)

                // 5단계: 만약 삭제 후 목록이 완전히 비었다면 가이드 텍스트 노출
                if (travelAdapter.getItems().isEmpty()) {
                    binding.recyclerView.visibility = View.GONE
                    binding.textEmpty.visibility = View.VISIBLE
                }

                Toast.makeText(requireContext(), "기록이 정상적으로 삭제되었습니다.", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "삭제 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
