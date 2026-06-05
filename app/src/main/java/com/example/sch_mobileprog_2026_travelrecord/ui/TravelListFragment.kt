package com.example.sch_mobileprog_2026_travelrecord.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sch_mobileprog_2026_travelrecord.data.DBHelper
import com.example.sch_mobileprog_2026_travelrecord.data.TravelRecord
import com.example.sch_mobileprog_2026_travelrecord.databinding.FragmentTravelListBinding
import kotlinx.coroutines.launch

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
            // TODO: 상세 화면(EditActivity) 수정 모드 인텐트 전환 예정 (DAY 4 연동)
            Toast.makeText(requireContext(), "선택된 기록 번호: $no", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
