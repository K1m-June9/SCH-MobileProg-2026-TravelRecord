package com.example.sch_mobileprog_2026_travelrecord.data

/**
 * 여행 여정 기록을 나타내는 불변(Immutable) 데이터 클래스.
 * SQLite 테이블 travel_records 스키마와 1:1 매핑됨.
 */
data class TravelRecord(
    val no: Int? = null,          // 데이터베이스에 저장될 때 자동 생성되는 고유 키값 (Primary Key, Autoincrement)
    val place: String,            // 여행지명 (NOT NULL)
    val visitDate: String,        // 방문 날짜 (NOT NULL, YYYY-MM-DD 규격)
    val memo: String? = null,     // 여행 메모 (NULL 허용)
    val photoUri: String? = null, // 앱 내부 스토리지 영역으로 복사 완료된 로컬 파일 URI (NULL 허용)
    val latitude: Double? = null, // 위도 (선택 사항, NULL 허용)
    val longitude: Double? = null // 경도 (선택 사항, NULL 허용)
)
