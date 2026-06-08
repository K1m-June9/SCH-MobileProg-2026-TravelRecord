package com.example.sch_mobileprog_2026_travelrecord.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.sch_mobileprog_2026_travelrecord.R
import com.example.sch_mobileprog_2026_travelrecord.data.DBHelper
import com.example.sch_mobileprog_2026_travelrecord.databinding.ActivityEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Calendar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * 여행 기록을 새롭게 추가하거나 기존 데이터를 수정하는 단독 액티비티.
 * Intent 매개변수 존재 유무에 따라 추가/수정 모드로 동적 분기 처리됨.
 */
class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private lateinit var dbHelper: DBHelper
    
    private var isEditMode = false
    private var isReadOnly = false
    private var recordId = -1

    // 최종 선택된 사진의 임시 URI 보관 (저장 전까지 메모리에만 임시 적재)
    private var selectedImageUri: Uri? = null

    // 기존 데이터 복원 및 수정을 위한 원본 백업 변수
    private var originalPhotoUri: String? = null
    private var originalLatitude: Double? = null
    private var originalLongitude: Double? = null

    // 카메라 캡처용 임시 파일 및 URI 변수
    private var tempCameraFile: File? = null
    private var tempCameraUri: Uri? = null

    // 갤러리 이미지 선택 결과 수신 런처
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            displaySelectedImage(uri)
        }
    }

    // 카메라 직접 촬영 결과 수신 런처
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempCameraUri?.let { uri ->
                selectedImageUri = uri
                displaySelectedImage(uri)
            }
        } else {
            // 촬영 실패 또는 취소 시 생성해 둔 임시 파일 즉시 제거
            tempCameraFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DBHelper.getInstance(this)

        // 툴바 설정 및 뒤로가기 탐색 연동
        setSupportActionBar(binding.toolbarEdit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarEdit.setNavigationOnClickListener {
            finish()
        }

        // 1단계: Intent 데이터 분석을 통한 추가/수정 모드 동적 식별 및 읽기 전용 여부 판단
        recordId = intent.getIntExtra("no", -1)
        isEditMode = recordId != -1
        isReadOnly = intent.getBooleanExtra("read_only", false)

        if (isEditMode) {
            if (isReadOnly) {
                binding.toolbarEdit.title = "여행 기록"
            } else {
                binding.toolbarEdit.title = "여행 기록 수정"
            }
            loadRecordData(recordId)
        } else {
            binding.toolbarEdit.title = "여행 기록 추가"
        }

        // 2단계: 날짜 캘린더 다이얼로그(DatePickerDialog) 입력 연동
        binding.etVisitDate.setOnClickListener {
            if (!isReadOnly) {
                showDatePicker()
            }
        }

        // 3단계: 취소 및 저장 버튼 이벤트 리스너 바인딩
        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveTravelRecord()
        }

        // 4단계: 갤러리 및 카메라 사진 호출 버튼 리스너 바인딩
        binding.btnSelectGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnTakeCamera.setOnClickListener {
            startCameraCapture()
        }

        // 5단계: 초기 읽기 전용 상태에 따른 뷰 활성화 설정
        setFieldsEnabled(!isReadOnly)
    }

    /**
     * 수정 모드 시 데이터베이스에서 기존 레코드를 비동기 조회하여 화면 입력 폼에 채워넣음.
     */
    private fun loadRecordData(no: Int) {
        lifecycleScope.launch {
            val record = dbHelper.getRecordById(no)
            if (record != null) {
                binding.etPlace.setText(record.place)
                binding.etVisitDate.setText(record.visitDate)
                binding.etMemo.setText(record.memo ?: "")
                originalPhotoUri = record.photoUri
                originalLatitude = record.latitude
                originalLongitude = record.longitude

                // 이미지가 등록된 기록일 경우 저수준 최적화 디코더를 경유하여 비동기 렌더링
                if (!record.photoUri.isNullOrEmpty()) {
                    selectedImageUri = Uri.parse(record.photoUri)
                    val bitmap = loadDetailImage(record.photoUri)
                    if (bitmap != null) {
                        binding.ivDetailPhoto.setImageBitmap(bitmap)
                    }
                }
            } else {
                Toast.makeText(this@EditActivity, "존재하지 않는 여행 기록입니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    /**
     * DatePickerDialog를 호출하여 YYYY-MM-DD 규격의 날짜를 입력 폼에 설정함.
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = String.format("%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay)
            binding.etVisitDate.setText(formattedDate)
        }, year, month, day).show()
    }

    /**
     * OOM 방지를 위한 고해상도 상세 이미지 디코딩 최적화 파이프라인.
     * 썸네일보다는 큰 크기(가로세로 800px)를 타겟으로 RGB_565 다운샘플링 처리함.
     */
    private suspend fun loadDetailImage(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val targetSize = 800
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    /**
     * 입력 데이터를 검증하고 임시 선택 이미지를 영구 복사한 후 SQLite 데이터베이스에 최종 저장(추가/수정)함.
     */
    private fun saveTravelRecord() {
        val place = binding.etPlace.text.toString().trim()
        val visitDate = binding.etVisitDate.text.toString().trim()
        val memo = binding.etMemo.text.toString().trim()

        // 1단계: 필수 필드 무결성 검증 및 시각적 에러 피드백
        var hasError = false
        if (place.isEmpty()) {
            binding.etPlace.error = "여행지명을 입력해 주세요."
            binding.etPlace.requestFocus()
            hasError = true
        } else {
            binding.etPlace.error = null
        }

        if (visitDate.isEmpty()) {
            binding.etVisitDate.error = "방문 날짜를 선택해 주세요."
            if (!hasError) {
                binding.etVisitDate.requestFocus()
            }
            hasError = true
        } else {
            binding.etVisitDate.error = null
        }

        if (hasError) {
            Toast.makeText(this, "필수 입력 항목을 확인해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                var finalPhotoUri: String? = originalPhotoUri

                // 2단계: 신규 이미지가 선택되었거나 변경되었을 경우 내부 저장소로 물리 복사 실행
                val currentUriString = selectedImageUri?.toString()
                if (currentUriString != originalPhotoUri && selectedImageUri != null) {
                    val copiedUri = com.example.sch_mobileprog_2026_travelrecord.util.FileUtil.copyUriToInternal(
                        this@EditActivity,
                        selectedImageUri!!
                    )
                    if (copiedUri != null) {
                        finalPhotoUri = copiedUri
                    } else {
                        Toast.makeText(this@EditActivity, "이미지 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                // 3단계: SQLite 저장용 TravelRecord 데이터 모델 조립
                val record = com.example.sch_mobileprog_2026_travelrecord.data.TravelRecord(
                    no = if (isEditMode) recordId else null,
                    place = place,
                    visitDate = visitDate,
                    memo = if (memo.isNotEmpty()) memo else null,
                    photoUri = finalPhotoUri,
                    latitude = originalLatitude,
                    longitude = originalLongitude
                )

                // 4단계: 모드 분기에 따라 SQLite DML 실행
                if (isEditMode) {
                    dbHelper.updateRecord(record)

                    // 5단계: 수정 시 새로운 이미지로 교체 완료 후, 이전의 낡은 가비지 이미지 물리 파일 삭제
                    if (currentUriString != originalPhotoUri && !originalPhotoUri.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val uri = Uri.parse(originalPhotoUri)
                                val oldFile = if (uri.scheme == "file") {
                                    File(uri.path ?: "")
                                } else {
                                    File(originalPhotoUri!!)
                                }
                                // 안전장치: 앱 내부 저장소(filesDir) 하위의 복사된 물리 파일만 조준 사격하여 지움 (외부 원본 보존)
                                if (oldFile.exists() && oldFile.parentFile?.absolutePath == filesDir.absolutePath) {
                                    oldFile.delete()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    Toast.makeText(this@EditActivity, "여행 기록이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                } else {
                    dbHelper.insertRecord(record)
                    Toast.makeText(this@EditActivity, "여행 기록이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                }

                finish()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EditActivity, "저장 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 카메라 촬영을 위해 임시 저장용 빈 파일과 FileProvider Content URI를 생성하고 카메라를 구동함.
     */
    private fun startCameraCapture() {
        try {
            // 캐시 디렉토리 하위에 유니크한 임시 파일 생성
            val tempFile = File.createTempFile("img_temp_", ".jpg", cacheDir).also {
                tempCameraFile = it
            }
            
            // Nougat(API 24) 이상 보안 규정에 따른 FileProvider 가상 content:// URI 생성
            val providerAuthority = "${packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(this, providerAuthority, tempFile).also {
                tempCameraUri = it
            }
            
            cameraLauncher.launch(uri)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "임시 파일 생성 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 선택된 임시 URI 이미지를 비동기 디코딩하여 화면의 대형 이미지뷰에 안전하게 표시하고 Exif GPS를 추출함.
     */
    private fun displaySelectedImage(uri: Uri) {
        lifecycleScope.launch {
            // 1단계: 고해상도 이미지 비동기 디코딩 렌더링
            val bitmap = loadDetailImage(uri.toString())
            if (bitmap != null) {
                binding.ivDetailPhoto.setImageBitmap(bitmap)
            } else {
                binding.ivDetailPhoto.setImageResource(R.drawable.default_image)
                Toast.makeText(this@EditActivity, "이미지 로드 실패", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 2단계: 이미지 바이너리로부터 Exif GPS 좌표 비동기 추출 시도
            val gps = com.example.sch_mobileprog_2026_travelrecord.util.LocationUtil.extractGpsCoordinates(this@EditActivity, uri)
            if (gps != null) {
                originalLatitude = gps.first
                originalLongitude = gps.second
                Toast.makeText(this@EditActivity, "사진의 GPS 위치 정보를 가져왔습니다.", Toast.LENGTH_SHORT).show()
            } else {
                // GPS 정보가 부재할 경우 수동 위치 지정을 위한 대화창 소환
                originalLatitude = null
                originalLongitude = null
                showManualLocationWarningDialog()
            }
        }
    }

    /**
     * 사진에 GPS 메타데이터가 존재하지 않을 때, 구글 지도가 내장된 커스텀 다이얼로그를 띄워
     * 사용자가 지도 위를 직접 터치하여 위도/경도 위치를 지정할 수 있는 수동 위치 연동 수립
     */
    private fun showManualLocationWarningDialog() {
        // 커스텀 다이얼로그 레이아웃 인플레이션
        val dialogView = layoutInflater.inflate(R.layout.dialog_map_selection, null)
        val mapView = dialogView.findViewById<MapView>(R.id.dialog_map_view)
        
        // 다이얼로그 빌더 작성 및 소환
        val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // MapView 라이프사이클 수동 연동 제어 (메모리 누수 방지)
        mapView.onCreate(null)
        mapView.onResume()

        var tempSelectedLatLng: LatLng? = null
        var currentMap: GoogleMap? = null

        // 구글 지도 비동기 준비
        mapView.getMapAsync { googleMap ->
            currentMap = googleMap
            googleMap.uiSettings.isZoomControlsEnabled = true

            // 초기 카메라 위치는 서울 시청으로 기본 지정 (초점 최적화)
            val defaultCenter = LatLng(37.5665, 126.9780)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultCenter, 14f))

            // 지도 터치 시 기존 핀 마커를 제거하고 새롭게 터치한 영역에 핀 마커 등록
            googleMap.setOnMapClickListener { latLng ->
                googleMap.clear()
                googleMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("선택된 위치")
                )
                tempSelectedLatLng = latLng
            }
        }

        // 다이얼로그 내부 동작 제어 단추 바인딩
        dialogView.findViewById<View>(R.id.btn_dialog_cancel).setOnClickListener {
            // 취소 시 좌표는 지정하지 않고 다이얼로그 해제
            originalLatitude = null
            originalLongitude = null
            mapView.onDestroy()
            alertDialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_dialog_confirm).setOnClickListener {
            // 확인 시 사용자가 지도 상에 핀 마커를 꽂았는지 무결성 검증
            val latLng = tempSelectedLatLng
            if (latLng != null) {
                originalLatitude = latLng.latitude
                originalLongitude = latLng.longitude
                Toast.makeText(this, "선택된 위치 좌표를 연동했습니다.", Toast.LENGTH_SHORT).show()
                mapView.onDestroy()
                alertDialog.dismiss()
            } else {
                Toast.makeText(this, "지도 위를 탭하여 위치 핀 마커를 표시해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        alertDialog.show()
    }

    /**
     * 상세 조회 시 모든 텍스트 필드를 잠그고 사진/저장 버튼들을 숨기거나 편집 시 활성화함
     */
    private fun setFieldsEnabled(enabled: Boolean) {
        binding.etPlace.isEnabled = enabled
        binding.etPlace.isFocusable = enabled
        binding.etPlace.isFocusableInTouchMode = enabled

        binding.etVisitDate.isEnabled = enabled
        binding.etVisitDate.isClickable = enabled

        binding.etMemo.isEnabled = enabled
        binding.etMemo.isFocusable = enabled
        binding.etMemo.isFocusableInTouchMode = enabled

        binding.btnSelectGallery.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.btnTakeCamera.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.btnCancel.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.btnSave.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        if (isReadOnly) {
            menuInflater.inflate(R.menu.menu_edit, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit_mode -> {
                // 읽기 전용 모드에서 편집 모드로 토글 전환
                isReadOnly = false
                setFieldsEnabled(true)
                binding.toolbarEdit.title = "여행 기록 수정"
                invalidateOptionsMenu() // '수정' 메뉴 아이콘을 숨기기 위해 다시 그림
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 입력 폼 이외의 빈 공간을 터치했을 때 키보드를 자동으로 내리고 포커스를 해제함
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
