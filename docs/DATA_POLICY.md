# DB, Android 저장소, 개인정보 기준

## 1. 저장 책임 분리

`DB.png`는 PostgreSQL/PostGIS를 전제로 한 서버 전체 모델입니다. Android Room은 그 스키마를 복제하는 데이터베이스가 아닙니다. 앱은 오프라인 UX와 재전송에 필요한 최소 데이터만 저장하고, 권한·관계·정합성의 원본은 서버가 소유합니다.

| 데이터 | 서버 원본 | Android Room | 이유 |
|---|---|---|---|
| 계정, 이메일, password hash, 계정 상태 | 저장 | 저장 안 함 | 인증·운영 원본이며 비밀번호 자료는 기기에 두지 않음 |
| access/refresh token | 발급·검증 | Room 저장 안 함 | Android 보안 저장소 사용 대상 |
| 프로필·멜로디 별칭·취향 | 저장 | 필요한 표시 캐시만 선택 | 서버 원본, 오프라인 화면 보조 |
| 대표곡·최애 아티스트·프로필 공개 범위 | 저장 | 계정 ID로 분리한 편집 캐시와 dirty 상태 | 오프라인 편집 후 재연결 동기화, 다른 계정으로 전송 금지 |
| 공개 범위, 차단, 팔로우·맞팔 | 저장 | UI 캐시만 선택 | 권한 판단은 반드시 서버가 수행 |
| Presence 세션 | TTL 저장 | 저장 안 함 | 실시간 임시 상태 |
| 원시 좌표, 정확도, area 판정 | 최신값 TTL 저장 | 저장 안 함 | 민감 위치 이력 방지 |
| 주변 사용자 snapshot·Delta | 계산·전송 | 영속 저장하지 않거나 짧은 비민감 캐시 | 재접속 시 서버 snapshot으로 교체 |
| 현재 음악 상태 | TTL·정규화 track 저장 | 현재 표시 캐시 선택 | 장기 청취 이력으로 확장하지 않음 |
| 알림 | 저장 | 읽기 UX용 캐시 선택 | 서버가 원본 |
| 채팅 메시지 | 저장 | 최근 대화 캐시·미전송 outbox 선택 | 오프라인 표시와 멱등 재전송 |
| 라운지 멤버·카드·투표·리액션 | 저장 또는 TTL | 화면 캐시·미전송 outbox 선택 | Topic 재구독 후 서버 상태로 정합화 |
| 오프라인 음악 카드 교환 | 동기화 후 저장 | 저장 | Room의 핵심 소유 데이터 |
| 오프라인 기기 인증서 | 공개키·발급·만료·폐기 상태 저장 | 암호화 계정 캐시에 현재 인증서만 보관 | Nearby 수신·서버 동기화 때 서명·유효기간·폐기 상태 검증 |
| 신고·운영 상태 | 저장 | 저장 안 함 | 서버 운영 원본 |
| 지역·행사·라운지 정의 | 저장 | 읽기 캐시 선택 | 서버가 활성 상태와 만료를 결정 |

현재 Android 구현에서 공유 서비스가 쓰는 비민감 운영 상태는 Room과 별개입니다. `melody_bubble_sharing_state` SharedPreferences에는 공유 활성 여부, 마지막 갱신 시각, `excellent/good/fair/poor/unknown` 정확도 품질만 저장합니다. 위·경도, provider, 이동 경로는 저장하거나 로그하지 않으며 현재 데모에서는 서버로도 전송하지 않습니다. `melody_bubble_now_playing_fallback`에는 transport 알림에서 길이를 제한·정리한 제목, 표시 문자열, 갱신 시각, 활성 여부만 저장하고, 앱이 다시 활성화될 때 이를 현재 음악의 수동 폴백 모델로 읽습니다. 원본 앱 패키지·알림 key·장기 청취 이력은 남기지 않습니다. `melody-bubble-session`에는 온보딩 완료 여부만 둡니다.

## 2. 현재 Room 모델

현재 Room DB 이름은 `melody-bubble-local.db`이고, 오프라인 교환용 두 테이블과 멜로디 후보 테이블을 가집니다. 서버 스키마의 축소 복사본이 아니라 계정별 오프라인 표시와 멱등 재전송에 필요한 로컬 모델입니다.

### `offline_exchange_local`

| 필드 | 설명 |
|---|---|
| `ownerUserId`, `id` | 계정과 교환 ID로 구성한 복합 기본 키; 계정 간 기록 격리 |
| `localSessionId` | 기기에서 만든 교환 세션 키 |
| `credentialId`, `peerCredentialId` | 서버 발급 오프라인 인증서 식별자 |
| `peerDisplayAlias` | 상대의 비식별 표시 별칭 |
| `trackTitle`, `trackArtist`, `melodyAlias` | 오프라인 화면용 수신 카드 snapshot |
| `sentCardJson`, `receivedCardJson` | 양쪽이 합의한 송·수신 카드 원문 |
| `exchangedAt`, `expiresAt?` | 교환·로컬 만료 시각 |
| `syncState` | `PENDING`, `UPLOADING`, `SYNCED`, `FAILED` 상태 |
| `retryCount`, `lastError` | WorkManager 재시도 진단 상태 |
| `payloadHash`, `protocolVersion`, `recordSignature` | 서버 검증에 필요한 합의 hash·프로토콜·기기 서명 |

### `sync_outbox`

| 필드 | 설명 |
|---|---|
| `ownerUserId`, `id` | 계정별 outbox 복합 기본 키 |
| `kind` | `OFFLINE_EXCHANGE` 또는 삭제 tombstone |
| `requestId` | 서버 중복 처리 방지용 ID |
| `payloadJson` | 전송할 교환 ID 또는 삭제 대상 |
| `createdAt`, `retryCount`, `lastError?` | 생성·재시도 상태 |

오프라인 교환은 Room transaction으로 기록과 outbox를 함께 저장합니다. 네트워크가 연결되면 WorkManager가 계정별 최대 50건을 실제 REST batch API로 전송하고, 성공한 outbox만 제거합니다. 중단된 `UPLOADING`은 다음 실행에서 복구하며 네트워크·5xx·429는 backoff 재시도하고 영구 요청 오류는 `FAILED`로 남깁니다.

Room 2→3 명시적 migration은 기존 익명 데모 행을 현재 로그인 계정에 귀속하지 않고 `__legacy__` owner로 격리합니다. 알려지지 않은 더 오래된 버전에는 destructive migration fallback이 남아 있으므로 운영 배포 전에 지원할 모든 과거 버전 migration을 추가해야 합니다.

### `DB.png` 오프라인 영역과의 직접 비교

| `DB.png` 서버 모델 | 현재 Android Room | 차이와 후속 처리 |
|---|---|---|
| 서버의 참가자별 `offline_exchange_events` | `ownerUserId`, exchange ID, 양쪽 카드, 시각·상태·서명 | 서버 관계 원본과 계정 격리는 서버가 재검증하며 Room은 현재 계정 표시·재전송만 담당 |
| `offline_credentials`의 공개키·발급·만료·폐기 시각 | 암호화 계정 캐시에 현재 인증서, Room 기록에 credential ID | 서버 서명과 만료를 Nearby 수신 시 검증하고 서버 sync 때 계정 소유·폐기를 다시 검증 |
| 서버의 `tracks` 정규화 FK | Room 문자열 snapshot | 오프라인 표시를 위한 의도적 비정규화. sync 때 서버가 track을 정규화 |
| 서버의 사용자·privacy·block 검증 | Room에 없음 | 연결 가능 여부와 계정 연결은 서버가 재검증하며 로컬 카드 표시만 독립적으로 허용 |
| 서버 동기화 결과 | `syncState`, `sync_outbox` | WorkManager가 실제 batch 응답을 반영하고 성공한 outbox만 삭제 |

교환과 outbox의 모든 조회·갱신·삭제는 `ownerUserId`를 조건으로 수행합니다. 로그아웃은 작업을 취소하고 암호화 계정 캐시와 Android Keystore 기기 키를 제거하지만 다른 계정 기록을 현재 계정으로 노출하지 않습니다.

### 후속 선택적 비민감 캐시

- 라운지 목록·카드·투표 snapshot
- 알림 목록
- 본인 프로필·공개 범위의 표시용 사본
- 공개 범위가 적용된 주변 버블의 짧은 캐시

주변 캐시를 두더라도 만료형 `nearbyHandle`, 가상 `displayPosition`, 공개 음악 요약만 허용하고 앱 재시작 또는 짧은 TTL 후 폐기합니다.

대표곡·최애 아티스트와 섹션별 공개 범위의 로컬 편집본은 활성 계정 ID가 포함된 키로 격리합니다. 재연결 시 해당 계정의 dirty 상태만 서버에 전송하고, `profileRevision` 충돌이 발생하면 자동 덮어쓰지 않고 사용자가 다시 확인하도록 합니다.

## 3. Room에 저장하지 않는 데이터

- 위도·경도, geohash, 실제 bearing, 정확 거리
- 위치 업데이트 배열, 이동 경로, 방문 장소 이력
- Android 위치 provider와 원시 Location 객체
- 다른 사용자의 영구 계정 UUID와 이메일·전화번호
- password, password hash, 평문 JWT·refresh token
- MediaSession 원본 객체와 음악 앱 계정 정보
- NotificationListener에서 얻은 원본 앱 패키지·알림 전체 내용의 장기 이력
- Presence heartbeat, 입력 중 상태, Music Pulse
- 서버 차단·팔로우·권한 판정의 독립 복사본

## 4. `DB.png` 대비 서버 스키마 갭

원본 DB 다이어그램은 넓은 제품 범위를 보여주지만 구현 전에 다음을 보완해야 합니다.

| 영역 | 다이어그램 상태·갭 | 서버 구현 전 결정 |
|---|---|---|
| 사용자 공개 프로필 | `profile_handle`, 색상, 미디어 object key, 멜로디 별칭 | `profileHandle`만 외부 식별자로 사용하고 사용자 UUID·이메일은 비공개. 차단 관계에서는 공개 프로필도 404 처리 |
| 교환 기반 취향 | `VERIFIED` 교환의 받은 카드 태그를 서버 집계 | 개별 상대·정확한 장소는 공개하지 않고 태그 빈도·비율과 요청자-대상 간 확인된 횟수만 제공 |
| 공개 범위 | `user_privacy`가 discoverability·music visibility를 가짐 | enum 값, 기본값, `updated_at`, 수신자별 필터 규칙 확정 |
| 반응 권한 | 다이어그램의 `allow_reactions`와 기획 모델의 이름이 다를 수 있음 | API 필드와 DB 컬럼을 하나로 매핑 |
| 취향 | `taste_profile_items`는 정규화 항목 모델 | 장르·아티스트·대표곡·mood의 `item_type` enum과 가중치 버전 확정 |
| 주변 익명 ID | 서버 테이블에 `nearbyHandle`이 없음 | Presence별 불투명 handle 발급·회전 방식과 영구 UUID 역매핑을 서버 내부에 정의 |
| 버블 배치 | DB에 `displayPosition`이 없음 | 응답 시 계산하는 비영속 가상 값으로 유지, 실제 GIS 방향에서 직접 변환 금지 |
| 위치 | PostGIS `user_locations`가 최신 위치·TTL을 표현 | PK/세션 관계, TTL 삭제, 정밀도 축소, 접근 감사 정책 확정 |
| 현재 음악 | `music_statuses`가 track FK와 snapshot을 가짐 | album/image/platform/source/isPlaying/genre·mood가 필요한 경우 컬럼 또는 JSON 계약 보강 |
| 채팅 | `chat_messages`에 idempotency 키는 보이나 시각·read 상태가 다이어그램에서 충분히 명확하지 않음 | `sent_at`, `read_at` 또는 별도 per-user receipt, content 길이, 보존 기간 확정 |
| 알림 | `notifications.payload JSONB` | type별 payload schema/version, 민감 필드 금지, 보존·삭제 정책 확정 |
| 라운지 | room/card/reaction/vote와 TTL이 있음 | 카드 track snapshot, vote 집계 기준, 수정·중복·만료 정책 확정 |
| 오프라인 교환 | token hash와 exchange가 분리됨 | 로컬 `syncStatus`는 서버 컬럼이 아니므로 sync 응답 매핑, 토큰 만료·재사용·삭제 정책 확정 |
| 지역 집계 | `areas`는 있으나 trend/pulse 이력 테이블은 없음 | Redis/메모리 집계로 둘지, 익명 임계값과 TTL 확정 |
| 신고 | reports는 있으나 증거·운영 이력이 제한적 | 상태 전이, 감사 로그, 보존·접근 권한 확정 |
| 스키마 공통 | 일부 테이블의 created/updated/TTL 및 FK delete 정책이 일관되지 않음 | 시간대, index, cascade/restrict, soft delete, enum/check constraint, migration 규칙 확정 |

이 갭을 이유로 Android가 서버 정책을 추측해 독자적으로 권한을 허용하면 안 됩니다. 실제 모드에서 서버 응답이 없으면 민감 기능은 실패 닫힘(fail closed)이 원칙입니다.

## 5. 개인정보 원칙

### 정확 거리·방향 미노출

- 주변 검색은 최대 15m로 제한하고 UI는 `5m 안쪽`, `10m 안쪽`, `15m 안쪽` 구간만 표시합니다.
- 정확한 미터값은 API와 UI에 포함하지 않습니다.
- 레이더형 Canvas는 사회적 발견을 위한 가상 배치이며 나침반 방향과 연결하지 않습니다.
- `displayPosition.x/y`의 반지름은 거리 구간만 반영하고 각도와 구간 내부 위치는 익명 handle에서 생성합니다.
- 소수 사용자만 있는 구역의 개별 음악·반응은 공개 Topic으로 발행하지 않습니다.

### 수집 최소화

- 사용자가 `주변 공유 시작`을 누르기 전에는 위치를 전송하지 않습니다.
- 공유 중에는 Foreground Service를 사용합니다. Android 13+에서 알림 권한이 허용된 경우 지속 알림과 중지 액션을 표시하고, 권한이 없을 때도 앱 안의 중지 경로를 유지합니다.
- 후속 실제 서버 모드에서는 공유 중지·TTL 만료 시 서버 최신 위치와 Presence를 폐기합니다.
- 백그라운드 자동 재시작과 기기 재부팅 후 자동 공유는 하지 않습니다.
- 원시 좌표와 이동 경로를 분석·로그·Room 백업 대상으로 만들지 않습니다.

### 음악·알림 접근

- 자동 음악 감지는 보조 기능이며 권한 거부 시 수동 선택으로 전환합니다.
- 현재 폴백은 제목이 없는 transport 알림을 무시하고, 표시 문자열이 없으면 아티스트 자리에 `감지된 음악`을 사용합니다. 실제 서버 공유 전에는 제목·아티스트 정규화 정책을 강화합니다.
- 공개 범위가 `TITLE_ARTIST`, `REPRESENTATIVE_ONLY`, `MUTUAL_ONLY`, `PRIVATE` 중 무엇인지 서버가 적용합니다.
- NotificationListener는 사용자가 접근을 허용한 알림 중 `CATEGORY_TRANSPORT`만 후보로 처리합니다. 현재는 음악 앱 여부까지 검증하지 않으므로 화면에서 수동 선택으로 바꿀 수 있어야 하며, 패키지·알림 key·전체 이력은 저장하지 않습니다.

### 소셜 안전

- 맞팔 전에는 자유 텍스트 대신 정해진 음악 리액션만 허용합니다.
- 맞팔·차단·정지·공개 범위·rate limit은 서버에서 검증합니다.
- 라운지에는 자유 텍스트 그룹 채팅을 넣지 않습니다.
- 신고·차단 결과는 공개 Topic이 아니라 개인 응답으로 전달합니다.

## 6. 현재 정리 동작과 후속 삭제·백업 기준

- 현재 앱은 시작 시 현재 계정의 만료 레코드를 정리하고, 서버 동기화 성공 시 해당 계정의 `sync_outbox`만 제거합니다.
- 현재 Manifest는 `android:allowBackup="false"`로 앱 자동 백업을 비활성화합니다. 향후 백업을 켜더라도 토큰, 오프라인 인증서·기기 키, 민감 캐시는 반드시 제외합니다.
- 디버그 로그에도 URL 자격 증명, Authorization 헤더, 좌표, 메시지 본문 전체를 출력하지 않습니다.
- 서버 서명 오프라인 인증서는 암호화된 계정 캐시에 보관하고 Room에는 credential ID만 남깁니다. 로그아웃 시 인증서 캐시와 기기 개인키를 즉시 제거합니다.
- 실패 outbox는 재시도 횟수와 마지막 오류를 보존하며, 기록 삭제는 서버 연결 전에는 tombstone으로 재전송됩니다.
