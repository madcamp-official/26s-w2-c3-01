# 위치 라운지 재구현

## 기존 구현 분석과 분리 기준

재사용한 기능은 `current_locations`/`presence_sessions`의 최신 위치와 만료 시각, 커밋 뒤 발행되는 STOMP 실시간 봉투, PostgreSQL/PostGIS, 기존 채팅의 선택 가입 개념이다.

교체한 기능은 건물·Wi-Fi 후보를 중심으로 움직이는 라운지, 명시적인 건물 라운지 입장/퇴장 세션, 30~2,000m 건물 반경, Wi-Fi 일치 요구사항이다. 이 코드는 호환성을 위해 남아 있지만 새 지도 경로에서는 사용하지 않는다. 새 기능은 `location_lounges`와 `/api/v1/location-lounges` 아래에 격리했다.

## 구현 구조

- `V25__location_lounges.sql`: 고정 중심점, 5/10/20m 반경, 상태, 파생 presence 캐시, 선택 가입 채팅방·멤버·메시지.
- `LocationLoungePolicy.kt`: 안정 반경, 원 교집합, 방향성 70% 규칙, 양방향 생존자와 다중 목적지 선택.
- `LocationLounge.kt`: 생성/조회, 위치 갱신 후 재계산, 병합, 1분 유예 자동 삭제, 채팅 API, 트랜잭션 락, 실시간 이벤트.
- `Nearby.kt`: 최신 위치 저장 직후 같은 트랜잭션에서 라운지 재조정.
- Android `LocationLoungeApi.kt`: 새 지도 스냅샷과 생성 API.
- Android 실시간 라우터: `/topic/location-lounges` 변경 시 지도 스냅샷 갱신.

## 동시성 및 데이터 정책

사용자별 생성 락과 전체 라운지 재계산 advisory lock을 함께 사용한다. 라운지와 채팅방 행은 `FOR UPDATE`로 잠근다. 병합 소스는 먼저 `MERGING`으로 선점하고 방의 `lounge_id`만 한 번 변경하므로 메시지, 가입자, 방장, 생성 시각은 보존된다. 위치 캐시는 입장/이탈 이벤트 차이를 위한 파생 데이터이며 영구 라운지 멤버십이 아니다.
