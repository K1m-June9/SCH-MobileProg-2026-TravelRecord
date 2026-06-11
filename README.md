# TravelRecord (2026-1 모바일프로그래밍)

## 1. 개발 스펙
- **개발 환경:** macOS(Macbook Air M1(ARM)) / Android Studio Panda (2025.3.4 Patch 1)
- **최소 SDK:** Android API 27 (Oreo 8.1)
- **타겟 SDK:** Android API 36
- **데이터베이스:** SQLite (`SQLiteOpenHelper` 직접 구현)
- **주요 라이브러리:** Google Maps SDK, AndroidX Core, Lifecycle
- **폰트:** Pretendard-Medium (.otf) 전역 폰트

## 2. 테스트 환경
- **가상 디바이스 (Emulator):** Medium Phone (Android 8.1 Oreo, API 27, arm64 아키텍처)
- **물리 기기 테스트:** 미수행 (iOS 사용, 안드로이드 기기 없음)

## 3. 개발 방법
- **탑다운 설계 및 점진적 릴리즈**
  - 앱의 전역 뼈대, 내비게이션, fragment 백스택(show/hide) 제어권을 선제 구축
  - 이후 저수준 컴포넌트(SQLite 데이터베이스, 리사이클러뷰 목록, 사진 업로드, 비동기 코루틴/구글맵 마커 연동)를 점진적으로 조립
  - 기능 완성 이후 실시간 UI 수정 및 상세 조회 읽기 전용 모드 개편

## 4. 상세 기능 명세
- **메인 네비게이션 및 백스택**
  - `BottomNavigationView`를 활용한 2개 탭(`TravelListFragment`, `MapOverviewFragment`) show/hide 제어
  - 지도 탭 뒤로가기 시 목록 탭으로 복귀하고, 목록 탭에서만 최종 앱이 종료되는 뒤로가기 콜백 수렴

- **여행 기록 목록**
  - `RecyclerView` 기반의 리스트 출력
  - 길게 누를 때 컨텍스트 메뉴(수정, 삭제) 플로팅
  - 삭제 시 `AlertDialog` 검증 단계를 경유하여 DB 행 및 내부 저장소 물리 파일 동시 제거

- **입력 및 수정 화면 (`EditActivity`)**
  - Intent 데이터 전달 분기에 따른 추가/수정 모드 동적 식별
  - `DatePickerDialog`를 연동한 날짜 자동 입력
  - 갤러리 선택 및 `FileProvider`를 경유한 카메라 촬영 임시 URI 획득
  - 입력 필드 이외의 영역 터치 시 키보드 자동 dismiss
  - 필수 필드 누락 시 인라인 에러 알림

- **상세 조회 읽기 전용 모드 및 편집 전환**
  - 리스트 짧은 터치 및 지도 마커 클릭 시 에디터 필드 비활성화 및 제어 단추 숨김(읽기 전용 상태)
  - 툴바 우측의 '수정' 버튼 클릭 시 모든 필드 활성화 및 저장 버튼이 노출되는 편집 상태로의 실시간 토글

- **비동기 처리**
  - `Kotlin Coroutines`(`Dispatchers.IO`) 및 `lifecycleScope` 기반의 DB 쿼리/스토리지 I/O 비동기 격리
  - 사진 Exif 정보 파싱을 통한 GPS 위도/경도 자동 추출 및 지도 마커 연동
  - GPS 정보 부재 시 구글 맵 다이얼로그 팝업 터치 연동을 통한 수동 위치 지정
  - 지도 탭 마커 클릭 시 원형 썸네일과 화이트 테두리가 결합된 커스텀 마커 아이콘 동적 캔버스 합성

- **물리적 가비지 파일 제거 안전망**
  - 수정 시 새로운 이미지로 교체 완료 후 구형 파일 즉시 삭제
  - 앱 시작 2초 후 백그라운드 스레드에서 DB에 매핑되지 않은 `filesDir` 및 `cacheDir` 내 5분 초과의 잉여 임시 파일 일괄 감지 및 제거

## 5. AI 도구 활용
- **NotebookLM:** 개발 사양 분석, 설계 지침 검토 및 PPT 강의자료 정보 요약에 활용
- **Gemini:** 안드로이드 표준 API 명세 탐색 및 개념 구체화에 활용
- **Antigravity CLI:** 안드로이드 스튜디오 환경 하에서의 실제 소스 코드 파일 편집 및 직접 개발에 활용
