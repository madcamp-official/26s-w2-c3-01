# 음악 프로필 구현 계획

기준 화면: Figma `Sync Wireframe`의 음악 프로필 화면(`40:2`)

## 1. 제품 정의

이 화면은 단순 계정 소개가 아니라 다음 네 가지를 한 번에 보여주는 **음악 관계 프로필**로 정의한다.

1. 사용자가 직접 꾸민 음악 정체성: 소개, 장르·무드, 대표 3곡, 최애 아티스트 3명
2. 지금의 상태: 현재 재생 중인 곡과 버블 모드 참여 상태
3. 관계의 상태: 팔로우·맞팔 여부와 두 사람이 실제로 교환한 횟수
4. 둘 사이의 결과: 대표곡·취향 태그·검증된 교환을 근거로 계산한 공통 취향

프로필 화면에 표시되는 값을 모두 사용자에게 입력받으면 안 된다. 팔로워 수, 교환 횟수, 현재 재생, 공통 취향 점수는 서버 또는 기기가 계산하는 값이어야 한다.

## 2. 현재 구현과 신규 구현의 경계

| 화면 요소 | 원본 데이터 | 현재 상태 | 필요한 작업 |
|---|---|---|---|
| 프로필 사진, 이름, 핸들, 소개, 색상 | 사용자 프로필 | 구현됨 | 새 UI에 매핑하고 이미지 URL 정책 정리 |
| `INDIE`, `CALM`, `NIGHT` 태그 | 사용자가 고른 장르·무드 | 구현됨 | 선택 개수·표준 태그 목록 검증 추가 |
| 팔로잉·팔로워 수 | `user_follows` 집계 | 구현됨 | 숫자 탭 시 목록으로 이동 |
| 검증된 교환 횟수 | 양쪽 기록이 일치한 `VERIFIED` 교환 | 구현됨 | 기존 `verifiedExchangeCount` 사용 |
| 맞팔/팔로잉 상태 | 요청자와 대상의 follow 관계 | 구현됨 | 상태 배지와 액션 버튼의 역할 분리 |
| 함께 교환한 횟수 | 요청자-대상 사이의 `VERIFIED` 교환 | 구현됨 | 기존 `sharedVerifiedExchangeCount` 사용 |
| 지금 듣는 음악 | Android MediaSession/알림 → TTL 상태 | 부분 구현 | 공개 프로필 응답, 앨범아트·진행률·만료 처리 추가 |
| 버블 모드 참여 상태 | 로컬 Nearby Connections 상태 | 부분 구현 | 공개 가능한 상태·참여자 수의 의미와 집계 방식 추가 |
| 요즘 나를 설명하는 3곡 | 사용자 직접 선택 | 미구현 | 검색/선택 UI, 순서 저장, 곡 참조 테이블/API 추가 |
| 최애 아티스트 3명 | 사용자 직접 선택 | 미구현 | 검색/선택 UI, 순서 저장, 아티스트 참조 테이블/API 추가 |
| 공통 취향 4개와 87% | 두 사용자의 음악 근거를 비교한 계산값 | 미구현 | 계산 규칙, 최소 표본, 캐시, 공개 범위 추가 |
| 항목별 공개 범위 | 사용자 설정 + 서버 권한 판정 | 부분 구현 | 현재 음악·청취 분석·교환 분석을 각각 분리 |

현재 서버의 `tasteFingerprint`는 **검증된 교환에서 받은 카드의 장르·무드 빈도**다. 이것을 그대로 두 사람의 `공통 취향 87%`로 사용하면 안 된다. 공통 취향은 요청자와 프로필 대상의 데이터를 비교한 별도 결과여야 한다.

## 3. 프로필 설정에서 사용자에게 받을 정보

### 3.1 기본 프로필

| 항목 | 필수 여부 | 입력 방식 | 저장 규칙 |
|---|---:|---|---|
| 프로필 사진 | 선택 | 갤러리/카메라, 정사각 크롭 | JPG/PNG/WebP, 업로드 전 축소, 원본 EXIF 제거 |
| 프로필 이름 | 필수 | 텍스트 | 2~40자, 앞뒤 공백 제거 |
| 프로필 핸들 | 필수 | 가입 시 자동 생성, 별도 변경 화면 | 영문 소문자·숫자·`_`, 3~32자, 중복 불가 |
| 소개 | 선택 | 여러 줄 텍스트 | 0~160자, 프로필에는 최대 2줄 표시 |
| 프로필 테마 색상 | 선택 | 정해진 팔레트 | 허용된 색상 토큰 또는 HEX |
| 선호 장르 | 선택 | 표준 태그 다중 선택 | 최대 5개 |
| 선호 무드 | 선택 | 표준 태그 다중 선택 | 최대 5개 |

핸들은 현재 서버에서 자동 생성되고 `PATCH /api/v1/me`로는 변경할 수 없다. 프로필 편집에서 노출하려면 중복 확인과 변경 제한을 가진 별도 API를 만들고, 그렇지 않으면 읽기 전용으로 보여준다.

### 3.2 음악 정체성

| 항목 | 입력 방식 | 실제 저장할 값 |
|---|---|---|
| 요즘 나를 설명하는 3곡 | 음악 검색 결과에서 최대 3개 선택 후 순서 변경 | provider, providerTrackId, title, artist, album, artworkUrl, rank, 선택 시각 |
| 최애 아티스트 3명 | 아티스트 검색 결과에서 최대 3명 선택 후 순서 변경 | provider, providerArtistId, name, imageUrl, rank, 선택 시각 |
| 음악 취향 태그 | 장르·무드·질감 태그 선택 | 표준 taxonomy ID와 표시명 |

`대표 3곡`과 `최애 아티스트`는 자동 청취 순위가 아니라 사용자가 직접 고르는 항목으로 시작한다. 그래야 현재 화면을 구현하기 위해 전체 청취 기록을 수집할 필요가 없다.

음원 사업자가 아직 정해지지 않았다면 저장 모델은 사업자 중립적으로 만든다. 검색 연동 전 MVP에서는 `provider=MANUAL`로 제목·아티스트·이미지 스냅샷을 저장할 수 있지만, 같은 곡 판별과 앨범아트 품질을 위해 운영 버전에는 정규화된 track/artist ID가 필요하다.

기존의 `30초 프로필 음악`과 `멜로디 별칭`은 현재 Figma 핵심 화면에 없다. 데이터와 생성 기능은 유지하되, 이번 프로필 1차 화면에서는 더보기 또는 프로필 편집의 별도 항목으로 두고 화면 길이를 다시 늘리지 않는다.

### 3.3 공개 및 수집 설정

수집 동의와 공개 범위는 같은 설정이 아니다. 예를 들어 청취 분석을 꺼 두면 공개 범위가 무엇이든 청취 이력을 만들지 않아야 한다.

| 설정 | 권장 값 | 기본값 |
|---|---|---|
| 주변에서 발견 가능 | `NEARBY`, `MUTUALS`, `HIDDEN` | 기존 값 유지 |
| 지금 듣는 음악 공개 | `EVERYONE`, `MUTUALS`, `PRIVATE` | 기존 `musicVisibility`를 마이그레이션 |
| 청취 기반 취향 분석 사용 | 켜기/끄기 | 끄기 |
| 청취 분석 결과 공개 | `EVERYONE`, `MUTUALS`, `PRIVATE` | `PRIVATE` |
| 교환 기반 취향 공개 | `EVERYONE`, `MUTUALS`, `EXCHANGED`, `PRIVATE` | `EXCHANGED` |
| 버블 참여 상태 공개 | `PARTICIPANTS_ONLY`, `MUTUALS`, `PRIVATE` | `PARTICIPANTS_ONLY` |
| 음악 리액션 받기 | 켜기/끄기 | 기존 값 유지 |
| 오프라인 음악 카드 교환 | 켜기/끄기 | 기존 값 유지 |

프로필 API는 앱이 공개 여부를 다시 추측하게 하지 않는다. 서버가 요청자와 대상의 관계·차단·공개 범위를 판정한 뒤 허용된 섹션만 반환한다.

## 4. 화면을 위해 자동으로 수집·계산할 정보

### 4.1 소셜 관계

- `followingCount`, `followerCount`: 서버 `user_follows` 집계
- `relationship`: `SELF`, `MUTUAL`, `FOLLOWING`, `FOLLOWS_ME`, `NONE`
- `verifiedExchangeCount`: 사용자의 전체 검증 교환 수
- `sharedVerifiedExchangeCount`: 요청자와 대상이 함께 검증한 교환 수
- 차단 관계가 있으면 프로필 전체를 `404`처럼 처리하고 일부 정보도 반환하지 않음

Figma의 두 버튼은 다음처럼 역할을 분리한다.

- `맞팔 중`: 현재 관계를 설명하는 비활성 상태 배지
- `팔로잉`: 내가 팔로우 중임을 나타내는 액션 버튼. 탭하면 팔로우 취소 확인
- 상대만 나를 팔로우: `맞팔하기`
- 관계 없음: `팔로우`
- 내 프로필: `프로필 편집`

### 4.2 현재 듣는 음악

기존 `NowPlayingNotificationListenerService`와 `PresenceSyncCoordinator`를 확장한다.

수집할 최소 값:

- title, artist, album
- 정규화된 trackId가 있으면 trackId
- artworkUrl 또는 서버가 정규화한 artwork 참조
- playbackState
- durationMs, positionMs, positionObservedAt
- 감지 경로를 나타내는 제한된 source enum
- observedAt, expiresAt

원본 알림 본문, 음악 앱 계정, 재생목록, 위치, 마이크 오디오는 수집하지 않는다. 현재 음악은 `music_statuses`에 TTL로 한 건만 두며, 기본 90초 내 heartbeat가 없으면 프로필에서 즉시 숨긴다.

진행률은 `positionMs + (현재 시각 - positionObservedAt)`로 계산하되 재생 중일 때만 증가시킨다. 지금 API는 title/artist/isPlaying만 보내므로 `MusicUpdateRequest`와 서버 `music_statuses`를 확장해야 한다.

### 4.3 버블 모드 상태

현재 `PresenceMode.BUBBLE`은 앱 로컬 상태이고 Nearby Connections 교환은 1:1이다. 따라서 Figma의 `참여자 12명`은 현재 데이터로 만들 수 없다.

MVP 권장안:

- 다른 사람의 프로필에는 공개가 허용된 경우 `버블 모드 참여 중`만 표시
- 참여자 수는 같은 사용자가 버블 모드를 열어 실제로 발견한 Nearby endpoint 수로 한정하고 `주변에서 발견 N명`으로 표현
- 온라인 전체 사용자 수나 지역 인원처럼 보이는 숫자는 서버 집계 기능이 생기기 전까지 표시하지 않음

후속 서버 집계를 도입한다면 `presence_sessions`에 `presence_mode`를 추가하고, 같은 탐색 범위의 활성 세션만 익명 집계한다. 적은 인원의 위치 추론을 막기 위해 최소 집계 기준도 둔다.

### 4.4 공통 취향

`87%`는 입력받는 값이 아니라 요청자-대상 쌍마다 계산하는 값이다. 1차 버전은 전체 청취 이력 없이 다음 근거만 사용한다.

1. 두 사용자가 직접 고른 장르·무드 태그
2. 대표 3곡의 정규화된 곡·아티스트·음악 태그
3. 최애 아티스트 3명
4. 검증된 교환 카드에서 발견된 장르·무드

권장 v1 가중치:

- 장르 유사도 30%
- 무드·질감 유사도 30%
- 아티스트 겹침 20%
- 대표곡 겹침 10%
- 검증된 교환 기반 친화도 10%

각 라벨의 퍼센트는 그 라벨에 대한 두 사람의 정규화 가중치 유사도다. 화면에는 점수가 높은 공통 라벨 최대 4개만 표시한다. 두 사용자 모두 최소 3개 이상의 유효 근거가 없으면 숫자를 만들지 말고 `취향 데이터가 더 필요해요` 상태를 보여준다.

계산 결과에는 다음 메타데이터가 필요하다.

- `score`: 0~100 전체 점수
- `metrics`: label, type, score, evidenceCount
- `algorithmVersion`: 예: `COMMON_TASTE_V1`
- `sampleSize`: 계산에 사용한 유효 근거 수
- `calculatedAt`

`피아노 중심`, `몽환적인 사운드` 같은 항목을 보여주려면 해당 음악 특성을 제공하는 정규화 taxonomy가 필요하다. 제목·아티스트 문자열만으로는 이 값을 신뢰성 있게 만들 수 없다. 음악 검색 공급자의 메타데이터를 사용하거나 사용자가 대표곡 선택 시 태그를 함께 확인하도록 해야 한다.

청취 이력 기반 분석은 2차 기능으로 분리한다. 도입 시에는 명시적 opt-in을 받고 원시 이벤트를 무기한 저장하지 말고 일 단위 집계로 축약한다.

## 5. 권장 API 계약

### 5.1 프로필 조회

기존 `GET /api/v1/profiles/{profileHandle}`에 선택 필드를 추가하거나 `/v2` 응답을 만든다.

```json
{
  "identity": {
    "profileHandle": "mintwave",
    "displayName": "민트",
    "bio": "잔잔한 멜로디로 기억해요.",
    "avatarUrl": "https://...",
    "themeColor": "#8B5CF6",
    "tags": ["INDIE", "CALM", "NIGHT"]
  },
  "social": {
    "relationship": "MUTUAL",
    "following": true,
    "mutual": true,
    "followingCount": 128,
    "followerCount": 1892
  },
  "exchange": {
    "verifiedTotalCount": 243,
    "sharedVerifiedCount": 37
  },
  "nowPlaying": null,
  "bubblePresence": null,
  "signatureTracks": [],
  "favoriteArtists": [],
  "commonTaste": null,
  "sectionStates": {
    "NOW_PLAYING": "NO_DATA",
    "COMMON_TASTE": "INSUFFICIENT_DATA"
  }
}
```

숨겨진 데이터는 빈 객체로 추측 가능하게 보내지 않고 `PRIVATE`, 데이터 없음은 `NO_DATA`, 만료된 현재 음악은 `STALE`처럼 상태를 구분한다. 차단된 사용자는 이 응답 자체를 주지 않는다.

### 5.2 편집 및 상태 API

- `GET /api/v1/me/profile-editor`: 편집 화면 전체 초기값
- `PATCH /api/v1/me`: 이름, 소개, 아바타, 테마, 장르, 무드
- `PUT /api/v1/me/profile-curation`: 대표 3곡, 최애 아티스트 3명과 순서
- `PUT /api/v1/me/profile-privacy`: 섹션별 수집·공개 설정
- `GET /api/v1/music/search?q=`: 곡/아티스트 검색, 공급자 중립 결과
- `POST /api/v1/nearby/music`: 현재 재생 상태 확장

모든 편집 요청에는 `profileRevision` 또는 `If-Match`를 사용해 두 기기에서 수정할 때 조용히 덮어쓰지 않도록 한다.

## 6. 데이터베이스 변경

### 6.1 새 테이블

`profile_signature_tracks`

- user_id, rank(1~3)
- provider, provider_track_id
- title, artist_name, album_name, artwork_url
- normalized_track_id nullable
- selected_at, updated_at
- `UNIQUE(user_id, rank)`

`profile_favorite_artists`

- user_id, rank(1~3)
- provider, provider_artist_id
- artist_name, image_url
- normalized_artist_id nullable
- selected_at, updated_at
- `UNIQUE(user_id, rank)`

`pair_taste_snapshots`는 선택적 캐시다.

- first_user_id, second_user_id를 UUID 정렬 순서로 저장
- score, metrics JSONB, algorithm_version, sample_size, calculated_at
- 프로필 태그·대표곡·아티스트·검증 교환이 바뀌면 무효화

### 6.2 기존 테이블 변경

`user_privacy_settings`

- `current_music_visibility`
- `listening_insights_enabled`
- `listening_insights_visibility`
- `exchange_insights_visibility`
- `bubble_presence_visibility`

기존 `music_visibility`는 다음처럼 마이그레이션한다.

- `TITLE_ARTIST` → `EVERYONE`
- `MUTUALS` → `MUTUALS`
- `HIDDEN` → `PRIVATE`

`music_statuses`

- album_name, normalized_track_id
- duration_ms, position_ms, position_observed_at
- artwork_url
- observed_at

현재 테이블처럼 사용자당 최신 한 건과 TTL만 유지한다.

`presence_sessions`

- 후속 온라인 버블 집계를 할 경우에만 `presence_mode` 추가

## 7. Android 화면과 상태 모델

### 7.1 프로필 설정 화면

설정은 한 화면에 모두 펼치지 않고 다음 네 하위 화면으로 나눈다.

1. 기본 프로필: 사진, 이름, 소개, 테마, 장르·무드
2. 음악 정체성: 대표 3곡, 최애 아티스트, 기존 30초 프로필 음악
3. 공개 범위: 지금 듣는 음악, 청취 분석, 교환 분석, 버블 상태
4. 연결 기능: 주변 발견, 리액션, 오프라인 교환

각 대표곡·아티스트 행은 검색, 삭제, 드래그 순서 변경을 지원한다. 저장 후 프로필 화면에서 같은 순서를 사용한다.

### 7.2 공개 프로필 화면

Compose 모델을 다음 섹션 단위로 분리한다.

- `ProfileHeroUiState`
- `RelationshipUiState`
- `NowPlayingUiState`
- `ProfileCurationUiState`
- `CommonTasteUiState`
- `SectionVisibilityState`

한 API 호출이 끝날 때까지 전체 화면을 막지 않고 hero를 먼저 표시하고, 실시간 상태는 별도 갱신할 수 있게 한다. 단, 초기 구현은 통합 응답 한 번으로 시작해도 된다.

표시 규칙:

- 현재 음악이 없거나 TTL 만료: LIVE 카드 숨김 또는 명확한 빈 상태
- 대표곡/아티스트가 0개: 다른 사람 프로필에서는 섹션 숨김, 내 프로필에서는 추가 CTA
- 공통 취향 근거 부족: 숫자를 임의 생성하지 않고 근거 부족 상태
- 비공개: 빈 데이터와 구분되는 잠금 상태
- 로딩 실패: 이미 받은 기본 프로필은 유지하고 실패한 섹션만 재시도

## 8. 오프라인 계정 모드 동작

계정은 존재하지만 사용자가 오프라인 모드로 들어간다는 현재 제품 방향을 유지한다.

- 기본 프로필과 대표곡/아티스트 편집은 로컬 캐시에 `dirty` 필드와 revision을 저장하고 연결 후 동기화
- 음악 검색 결과가 캐시되지 않았다면 오프라인에서는 새 검색 불가를 명확히 표시
- 검증 전 오프라인 교환은 로컬 기록만 표시하고 서버 통계에는 포함하지 않음
- 양쪽 업로드가 일치해 `VERIFIED`가 되면 서버가 교환 수와 취향 snapshot을 다시 계산
- 현재 듣는 음악은 임시 상태이므로 오프라인 중의 오래된 곡을 나중에 `현재 음악`으로 업로드하지 않음
- 버블 모드의 로컬 발견 수는 서버의 온라인 참여자 수와 합산하지 않음

프로필 편집 outbox는 기존 오프라인 교환 outbox와 종류를 분리한다. 충돌은 전체 프로필 last-write-wins보다 필드별 revision 또는 서버 `If-Match`로 처리한다.

## 9. 구현 순서

### 0단계 — 계약 확정

- 대표곡/아티스트 검색 공급자 또는 `MANUAL` MVP 결정
- 버블 참여자 수의 의미 확정
- 공통 취향 v1 taxonomy·가중치·최소 표본 확정
- 섹션별 공개 범위 enum 확정

완료 기준: Android DTO, 서버 DTO, DB migration 설계가 같은 용어를 사용한다.

### 1단계 — 프로필 설정과 저장

- DB migration 추가
- 서버 curation/privacy API 추가
- Android 모델, Retrofit API, Repository, ViewModel 추가
- 기본 프로필/대표곡/아티스트/공개 범위 편집 UI 구현

완료 기준: 앱 재설치·재로그인 뒤에도 설정한 프로필이 같은 순서로 복원된다.

### 2단계 — 수정된 Figma 프로필 UI

- hero, 통계, 관계, 교환, 대표곡, 아티스트, 공개 범위 카드 구현
- 팔로우 상태 전이와 목록 이동 연결
- empty/loading/private/error 상태 구현

완료 기준: `SELF`, `MUTUAL`, `FOLLOWING`, `FOLLOWS_ME`, `NONE` 상태가 모두 올바른 버튼과 문구를 보인다.

### 3단계 — 현재 음악과 버블 상태

- MediaSession 메타데이터/재생 위치 추출 확장
- `MusicUpdateRequest`, 서버 `music_statuses`, 공개 프로필 응답 확장
- TTL·중지·권한 변경·백그라운드 복귀 처리
- 버블 상태의 MVP 표시 규칙 적용

완료 기준: 음악을 정지하거나 heartbeat가 끊기면 90초 이내에 다른 기기의 프로필에서 LIVE가 사라진다.

### 4단계 — 공통 취향

- 정규화 taxonomy와 사용자 취향 벡터 생성
- 요청자-대상 비교 서비스와 캐시 무효화 구현
- 근거 부족/비공개/차단 처리
- UI의 4개 지표와 전체 점수 연결

완료 기준: 같은 입력에는 항상 같은 점수와 algorithmVersion이 반환되고, 검증되지 않은 교환은 점수에 포함되지 않는다.

### 5단계 — 오프라인 동기화·안전·QA

- 프로필 편집 outbox와 충돌 처리
- 계정 전환 시 로컬 캐시 격리
- 공개 범위 서버 권한 테스트
- 접근성, 작은 화면, 긴 이름, 이미지 실패, 느린 네트워크 테스트

완료 기준: 비공개 섹션이 다른 API·캐시·실시간 이벤트를 통해 우회 노출되지 않는다.

## 10. 필수 테스트

- 서버 단위 테스트: relationship 5종, 차단, 섹션별 공개 범위, 검증 교환만 집계
- 계약 테스트: Android DTO와 서버 JSON의 optional/null/enum 호환
- Compose 테스트: 데이터 있음/없음/비공개/근거 부족/실패 상태
- 두 기기 테스트: follow→mutual 전환, 오프라인 교환→온라인 검증→통계 반영
- 현재 음악 테스트: 재생/일시정지/앱 전환/권한 철회/TTL 만료
- 개인정보 테스트: 정확한 위치, 원본 알림, 전체 청취 이력이 프로필 응답·로그·Room에 남지 않음

## 11. MVP 범위 권장 결론

이번 1차 구현에는 다음을 포함한다.

- 사용자 직접 입력: 기본 프로필, 장르·무드, 대표 3곡, 최애 아티스트 3명
- 자동 반영: 팔로우 통계, 관계, 검증 교환 횟수, TTL 현재 음악
- 계산: 직접 입력한 취향과 검증 교환만 사용한 공통 취향 v1
- 개인정보: 지금 음악·교환 기반 취향·버블 상태의 분리된 공개 범위

전체 청취 이력 수집과 청취 기반 자동 최애 아티스트는 2차로 미룬다. 현재 Figma를 구현하는 데 필수 데이터가 아니며, 별도의 동의·보존·삭제 정책 없이는 수집 범위를 불필요하게 키운다.
