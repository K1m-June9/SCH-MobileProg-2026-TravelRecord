package com.example.sch_mobileprog_2026_travelrecord.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQLite 데이터베이스 생성 및 스키마 관리를 담당하는 DBHelper 클래스.
 * 싱글톤(Singleton) 패턴을 적용하여 앱 전역에서 단 하나의 DB 커넥션 헬퍼를 유지하고 메모리 누수를 방지함.
 */
class DBHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "travel_records.db"
        private const val DATABASE_VERSION = 1

        // 테이블 및 컬럼 이름 정의
        const val TABLE_NAME = "travel_records"
        const val COLUMN_NO = "no"
        const val COLUMN_PLACE = "place"
        const val COLUMN_VISIT_DATE = "visit_date"
        const val COLUMN_MEMO = "memo"
        const val COLUMN_PHOTO_URI = "photo_uri"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"

        @Volatile
        private var instance: DBHelper? = null

        /**
         * DBHelper 싱글톤 인스턴스를 반환함.
         * context.applicationContext를 활용하여 Activity 컨텍스트의 메모리 누수(Memory Leak)를 원천 방지함.
         */
        fun getInstance(context: Context): DBHelper {
            return instance ?: synchronized(this) {
                instance ?: DBHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 최초로 데이터베이스가 생성될 때 테이블 구조(DDL)를 정의함.
     * 설계서 기준에 맞춰 위도/경도는 REAL 타입으로, 방문일 및 장소명은 NOT NULL로 설정함.
     */
    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                `$COLUMN_NO` INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PLACE TEXT NOT NULL,
                $COLUMN_VISIT_DATE TEXT NOT NULL,
                $COLUMN_MEMO TEXT,
                $COLUMN_PHOTO_URI TEXT,
                $COLUMN_LATITUDE REAL,
                $COLUMN_LONGITUDE REAL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    /**
     * 데이터베이스 버전이 업데이트(마이그레이션)될 때 호출됨.
     * 여기서는 개발 환경 특성상 기존 테이블을 삭제(Drop) 후 재생성하도록 정의함.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * 전체 조회 시 사용될 정렬 기준 정의
     */
    enum class SortOrder {
        DATE_ASC,   // 날짜 오름차순 (과거순)
        DATE_DESC,  // 날짜 내림차순 (최신순)
        PLACE_ASC   // 여행지 가나다순
    }

    /**
     * 새로운 여행 기록을 삽입함.
     * SQL 인젝션 방지를 위해 ContentValues를 활용하며, 코루틴 비동기(Dispatchers.IO) 안전하게 처리함.
     * AGENT 지침에 따라 사용이 끝난 SQLiteDatabase 인스턴스를 .use로 확실히 닫음.
     */
    suspend fun insertRecord(record: TravelRecord): Long = withContext(Dispatchers.IO) {
        writableDatabase.use { db ->
            val values = ContentValues().apply {
                put(COLUMN_PLACE, record.place)
                put(COLUMN_VISIT_DATE, record.visitDate)
                put(COLUMN_MEMO, record.memo)
                put(COLUMN_PHOTO_URI, record.photoUri)
                put(COLUMN_LATITUDE, record.latitude)
                put(COLUMN_LONGITUDE, record.longitude)
            }
            db.insert(TABLE_NAME, null, values)
        }
    }

    /**
     * 전체 여행 기록을 조회함.
     * 자원 누수 방지를 위해 SQLiteDatabase와 Cursor에 각각 .use 확장 함수를 결합해 확실히 close를 보장함.
     * 정렬 매개변수(SortOrder)에 맞춰 동적으로 정렬 쿼리를 비동기로 처리함.
     */
    suspend fun getAllRecords(sortOrder: SortOrder): List<TravelRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<TravelRecord>()
        val orderBy = when (sortOrder) {
            SortOrder.DATE_ASC -> "$COLUMN_VISIT_DATE ASC"
            SortOrder.DATE_DESC -> "$COLUMN_VISIT_DATE DESC"
            SortOrder.PLACE_ASC -> "$COLUMN_PLACE ASC"
        }
        val query = "SELECT * FROM $TABLE_NAME ORDER BY $orderBy"

        readableDatabase.use { db ->
            db.rawQuery(query, null).use { cursor ->
                val colNo = cursor.getColumnIndex(COLUMN_NO)
                val colPlace = cursor.getColumnIndex(COLUMN_PLACE)
                val colVisitDate = cursor.getColumnIndex(COLUMN_VISIT_DATE)
                val colMemo = cursor.getColumnIndex(COLUMN_MEMO)
                val colPhotoUri = cursor.getColumnIndex(COLUMN_PHOTO_URI)
                val colLatitude = cursor.getColumnIndex(COLUMN_LATITUDE)
                val colLongitude = cursor.getColumnIndex(COLUMN_LONGITUDE)

                while (cursor.moveToNext()) {
                    val no = if (colNo != -1) cursor.getInt(colNo) else null
                    val place = if (colPlace != -1) cursor.getString(colPlace) else ""
                    val visitDate = if (colVisitDate != -1) cursor.getString(colVisitDate) else ""
                    val memo = if (colMemo != -1) cursor.getString(colMemo) else null
                    val photoUri = if (colPhotoUri != -1) cursor.getString(colPhotoUri) else null
                    val latitude = if (colLatitude != -1 && !cursor.isNull(colLatitude)) cursor.getDouble(colLatitude) else null
                    val longitude = if (colLongitude != -1 && !cursor.isNull(colLongitude)) cursor.getDouble(colLongitude) else null

                    records.add(
                        TravelRecord(
                            no = no,
                            place = place,
                            visitDate = visitDate,
                            memo = memo,
                            photoUri = photoUri,
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                }
            }
        }
        records
    }
}
