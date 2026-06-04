package com.example.sch_mobileprog_2026_travelrecord.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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
}
