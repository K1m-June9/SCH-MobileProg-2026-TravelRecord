package com.example.sch_mobileprog_2026_travelrecord.util

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * 원본 이미지의 바이트 스트림에서 Exif 메타데이터를 파싱하여 GPS 위도/경도를 추출하는 위치 유틸리티.
 */
object LocationUtil {

    /**
     * 이미지 URI를 분석하여 위도(Latitude)와 경도(Longitude) 쌍을 반환함.
     * GPS 메타데이터가 존재하지 않거나 예외 발생 시 null을 반환함.
     */
    suspend fun extractGpsCoordinates(context: Context, uri: Uri): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null

            // 안드로이드 기본 SDK 내장 ExifInterface 기동
            val exif = ExifInterface(inputStream)
            val latLong = FloatArray(2)

            // getLatLong 메서드를 통해 위도/경도 배열 값을 획득함 (성공 시 true 반환)
            if (exif.getLatLong(latLong)) {
                val latitude = latLong[0].toDouble()
                val longitude = latLong[1].toDouble()
                
                // 유효 좌표계 검증 (위도: -90~90, 경도: -180~180)
                if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                    return@withContext Pair(latitude, longitude)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
