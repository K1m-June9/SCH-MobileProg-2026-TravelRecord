package com.example.sch_mobileprog_2026_travelrecord.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.sch_mobileprog_2026_travelrecord.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.sch_mobileprog_2026_travelrecord.databinding.FragmentMapOverviewBinding

/**
 * 저장된 모든 여행지의 위치 좌표를 조회하여 구글 지도 상에 마커로 플로팅하는 프래그먼트.
 * OnMapReadyCallback을 구현하여 구글 지도 컴포넌트의 라이프사이클을 통제함.
 */
class MapOverviewFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapOverviewBinding? = null
    private val binding get() = _binding!!

    private var mMap: GoogleMap? = null

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

        // 1단계: SupportMapFragment 객체를 획득하여 구글 지도 비동기 준비 시작
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    /**
     * 구글 지도 라이브러리 로드가 완료되고 지도가 화면에 렌더링될 준비가 되었을 때 호출됨.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // 2단계: 기본 지도 제어 UI 활성화 (줌인/아웃 컨트롤러 배치)
        mMap?.uiSettings?.isZoomControlsEnabled = true

        // 3단계: 초기 로딩 확인용 카메라 디폴트 좌표 스위칭 (서울시청 기준)
        val seoulCityHall = LatLng(37.5665, 126.9780)
        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(seoulCityHall, 14f))

        // TODO: SQLite DB를 쿼리하여 위도/경도가 존재하는 마커 전체 자동 플로팅 예정 (Task 5.4 연동)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
