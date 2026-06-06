package com.example.sch_mobileprog_2026_travelrecord.ui

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
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

/**
 * 여행 기록을 새롭게 추가하거나 기존 데이터를 수정하는 단독 액티비티.
 * Intent 매개변수 존재 유무에 따라 추가/수정 모드로 동적 분기 처리됨.
 */
class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private lateinit var dbHelper: DBHelper
    
    private var isEditMode = false
    private var recordId = -1

    // [A2 대안 A] 최종 선택된 사진의 임시 URI 보관 (저장 전까지 메모리에만 임시 적재)
    private var selectedImageUri: Uri? = null

    // 카메라 캡처용 임시 파일 및 URI 변수
    private var tempCameraFile: File? = null
    private var tempCameraUri: Uri? = null

    // 갤러리 이미지 선택 결과 수신 런처 (Task 4.3 연동)
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            displaySelectedImage(uri)
        }
    }

    // 카메라 직접 촬영 결과 수신 런처 (Task 4.3 연동)
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

        // 1단계: Intent 데이터 분석을 통한 추가/수정 모드 동적 식별
        recordId = intent.getIntExtra("no", -1)
        isEditMode = recordId != -1

        if (isEditMode) {
            binding.toolbarEdit.title = "여행 기록 수정"
            loadRecordData(recordId)
        } else {
            binding.toolbarEdit.title = "여행 기록 추가"
        }

        // 2단계: 날짜 캘린더 다이얼로그(DatePickerDialog) 입력 연동
        binding.etVisitDate.setOnClickListener {
            showDatePicker()
        }

        // 3단계: 취소 및 저장 버튼 이벤트 리스너 바인딩
        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            // TODO: 임시 보관된 URI를 filesDir로 영속 복사 후 DB에 CRUD 저장 처리 예정 (Task 4.4 연동)
            Toast.makeText(this, "저장 기능 연동 예정", Toast.LENGTH_SHORT).show()
        }

        // 4단계: 갤러리 및 카메라 사진 호출 버튼 리스너 바인딩 (Task 4.3 연동)
        binding.btnSelectGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnTakeCamera.setOnClickListener {
            startCameraCapture()
        }
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
     * 선택된 임시 URI 이미지를 비동기 디코딩하여 화면의 대형 이미지뷰에 안전하게 표시함.
     */
    private fun displaySelectedImage(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = loadDetailImage(uri.toString())
            if (bitmap != null) {
                binding.ivDetailPhoto.setImageBitmap(bitmap)
            } else {
                binding.ivDetailPhoto.setImageResource(R.drawable.default_image)
                Toast.makeText(this@EditActivity, "이미지 로드 실패", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
