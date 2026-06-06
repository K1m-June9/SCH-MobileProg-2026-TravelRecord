package com.example.sch_mobileprog_2026_travelrecord.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 갤러리/카메라 앱에서 전달받은 임시 URI 소스를 앱 내부 영속 스토리지(context.filesDir)로 복사 전담 유틸리티.
 */
object FileUtil {

    /**
     * 임시 URI 소스의 바이트 스트림을 열어 내부 저장소에 고유한 파일(img_timestamp.jpg)로 안전하게 물리 복사함.
     * 복사 성공 시 저장된 로컬 파일의 URI 주소 문자열(file:// 절대경로)을 반환하고 실패 시 null을 반환함.
     */
    suspend fun copyUriToInternal(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            // 1단계: 내부 저장소 공간 하위에 유니크한 영구 파일 생성
            val uniqueFileName = "img_${System.currentTimeMillis()}.jpg"
            val destinationFile = File(context.filesDir, uniqueFileName)

            // 2단계: URI로부터 입력 스트림 획득 및 복사 대상 파일 출력 스트림 생성
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null
            
            outputStream = FileOutputStream(destinationFile)

            // 3단계: 버퍼 복사 연산 수행 (ANR 방지를 위해 IO 스레드 격리)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()

            // 4단계: 디코더에서 사용하기 용이하도록 가독성 높은 file:// 절대경로 URI 형태로 반환
            Uri.fromFile(destinationFile).toString()

        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            // 5단계: 열려 있는 입출력 스트림 안전 해제
            try {
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
