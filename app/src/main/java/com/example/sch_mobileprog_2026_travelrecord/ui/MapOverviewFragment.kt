package com.example.sch_mobileprog_2026_travelrecord.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sch_mobileprog_2026_travelrecord.R
import com.example.sch_mobileprog_2026_travelrecord.data.DBHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.example.sch_mobileprog_2026_travelrecord.databinding.FragmentMapOverviewBinding
import kotlinx.coroutines.launch

/**
 * 저장된 모든 여행지의 위치 좌표를 조회하여 구글 지도 상에 마커로 플로팅하는 프래그먼트.
 * OnMapReadyCallback을 구현하여 구글 지도 컴포넌트의 라이프사이클을 통제함.
 */
class MapOverviewFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapOverviewBinding? = null
    private val binding get() = _binding!!

    private var mMap: GoogleMap? = null
    private lateinit var dbHelper: DBHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        dbHelper = DBHelper.getInstance(requireContext())

        // SupportMapFragment 객체를 획득하여 구글 지도 비동기 준비 시작
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면(추가/수정/삭제) 후 탭 복귀 시 실시간 마커 동기화
        if (mMap != null) {
            loadMarkersFromDb()
        }
    }

    /**
     * show/hide 메커니즘을 사용하는 단일 호스트 액티비티 구조 특성상,
     * 숨겨졌던 지도 프래그먼트가 다시 활성화될 때 호출되어 실시간 마커 동기화를 재수행함.
     */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && mMap != null) {
            loadMarkersFromDb()
        }
    }

    /**
     * 구글 지도 라이브러리 로드가 완료되고 지도가 화면에 렌더링될 준비가 되었을 때 호출됨.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // 기본 지도 제어 UI 활성화 (줌인/아웃 컨트롤러 배치)
        mMap?.uiSettings?.isZoomControlsEnabled = true

        // 지도가 준비된 최초 시점에 마커들을 데이터베이스에서 쿼리하여 플로팅함
        loadMarkersFromDb()
    }

    /**
     * SQLite 데이터베이스에서 전체 여행 기록을 쿼리하여 위도/경도가 유효한 모든 항목을 지도에 핀 마커로 띄움.
     * 모든 마커가 찍힌 후 카메라 줌 및 위치 경계를 지능적으로 자동 조정(LatLngBounds)함.
     */
    private fun loadMarkersFromDb() {
        val googleMap = mMap ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1단계: 기존 지도의 모든 마커와 오버레이 깨끗이 초기화 (중복 마킹 방지)
                googleMap.clear()

                // 2단계: 코루틴 비동기로 전체 여행 기록 조회
                val records = dbHelper.getAllRecords(DBHelper.SortOrder.DATE_DESC)

                val boundsBuilder = LatLngBounds.Builder()
                var hasValidMarkers = false

                // 3단계: 기록 루프를 돌며 위도/경도가 존재하는 항목만 마커 추가
                for (record in records) {
                    val lat = record.latitude
                    val lng = record.longitude
                    if (lat != null && lng != null) {
                        val latLng = LatLng(lat, lng)
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title(record.place)
                                .snippet(record.visitDate)
                        )
                        boundsBuilder.include(latLng)
                        hasValidMarkers = true
                    }
                }

                // 4단계: 카메라 뷰포트 확대/축소 범위 지능형 바운딩 연동 (마커 일괄 노출 줌 조정)
                if (hasValidMarkers) {
                    val bounds = boundsBuilder.build()
                    // 마커들의 범위 경계에 맞춰 카메라 줌 이동 (패딩 여백: 150px)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                } else {
                    // 지도에 찍을 마커가 아예 없을 경우 디폴트 카메라는 서울 시청으로 고정
                    val seoulCityHall = LatLng(37.5665, 126.9780)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(seoulCityHall, 14f))
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
