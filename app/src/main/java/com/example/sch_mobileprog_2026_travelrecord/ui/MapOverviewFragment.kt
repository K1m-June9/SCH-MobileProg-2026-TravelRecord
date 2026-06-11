package com.example.sch_mobileprog_2026_travelrecord.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.example.sch_mobileprog_2026_travelrecord.databinding.FragmentMapOverviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // 마커 정보창(InfoWindow) 클릭 시 상세보기(EditActivity) 연동 설정
        mMap?.setOnInfoWindowClickListener { marker ->
            val recordId = marker.tag as? Int
            if (recordId != null) {
                val intent = Intent(requireContext(), EditActivity::class.java).apply {
                    putExtra("no", recordId)
                    putExtra("read_only", true)
                }
                startActivity(intent)
            }
        }

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

                        // 비동기로 마커 이미지 합성 (Task 7.3)
                        val markerIcon = createCustomMarkerBitmap(record.photoUri)

                        val markerOptions = MarkerOptions()
                            .position(latLng)
                            .title(record.place)
                            .snippet(record.visitDate)

                        if (markerIcon != null) {
                            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(markerIcon))
                        }

                        val marker = googleMap.addMarker(markerOptions)
                        marker?.tag = record.no
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

    /**
     * 썸네일과 화이트 말풍선 프레임을 합성하여 커스텀 마커 비트맵을 생성함
     */
    private suspend fun createCustomMarkerBitmap(photoUriStr: String?): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. 타겟 사이즈 설정 (마커 전체 크기: 가로 90px, 세로 105px)
            val width = 90
            val height = 105
            val imageSize = 74 // 내부 썸네일 이미지 영역
            val padding = 8    // 테두리 여백
            val cornerRadius = 16f // 둥글기 반경

            // 2. 기본 합성용 빈 비트맵 생성
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 3. 하단 삼각형 꼬리가 달린 화이트 프레임 경로(Path) 그리기
            val path = Path()
            // 둥근 사각형 바운더리
            val rectF = RectF(0f, 0f, width.toFloat(), width.toFloat())
            path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
            // 아래 꼬리 그리기 (삼각형 좌표 설정: 좌(width/2 - 12), 우(width/2 + 12), 하(height))
            path.moveTo((width / 2 - 12).toFloat(), width.toFloat())
            path.lineTo((width / 2).toFloat(), height.toFloat())
            path.lineTo((width / 2 + 12).toFloat(), width.toFloat())
            path.close()

            // 4. 화이트 말풍선 배경 그리기
            paint.color = android.graphics.Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)

            // 5. 프레임 테두리에 미세한 그레이 보더 추가 (경계 획선 선명도 확보)
            paint.color = android.graphics.Color.parseColor("#E5E8EB")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawPath(path, paint)

            // 6. 썸네일 이미지 로드 및 크롭 (있을 경우만)
            var srcBitmap: Bitmap? = null
            if (!photoUriStr.isNullOrEmpty()) {
                srcBitmap = loadThumbnailForMarker(photoUriStr, imageSize)
            }

            // 7. 이미지 렌더링 (동작 무결성)
            val innerRect = RectF(
                padding.toFloat(),
                padding.toFloat(),
                (padding + imageSize).toFloat(),
                (padding + imageSize).toFloat()
            )
            
            if (srcBitmap != null) {
                // 원형/둥근형으로 썸네일 깎아서 프레임에 얹기
                val croppedBitmap = getRoundedCornerBitmap(srcBitmap, imageSize, 12f)
                canvas.drawBitmap(croppedBitmap, null, innerRect, null)
            } else {
                // 사진이 없을 경우: 기본 벡터/플레이스홀더 이미지를 캔버스 중앙에 그리기
                val defaultPlaceholder = BitmapFactory.decodeResource(resources, R.drawable.placeholder_image)
                if (defaultPlaceholder != null) {
                    val placeholderScaled = Bitmap.createScaledBitmap(defaultPlaceholder, 36, 36, true)
                    val x = (width - placeholderScaled.width) / 2f
                    val y = (width - placeholderScaled.height) / 2f
                    canvas.drawBitmap(placeholderScaled, x, y, null)
                }
            }

            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 마커용 고성능 저해상도 썸네일 디코딩 파이프라인
     */
    private fun loadThumbnailForMarker(uriString: String, size: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            requireContext().contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            options.inSampleSize = calculateInSampleSize(options, size, size)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565
            
            val decoded = requireContext().contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            decoded?.let {
                Bitmap.createScaledBitmap(it, size, size, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 비트맵을 지정된 크기로 자르고 코너를 둥글게 깎아 반환하는 유틸리티
     */
    private fun getRoundedCornerBitmap(bitmap: Bitmap, size: Int, pixels: Float): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)
        val roundPx = pixels

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
