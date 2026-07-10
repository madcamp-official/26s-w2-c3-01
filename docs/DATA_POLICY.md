# DB, Android 저장소, 개인정보 기준

## 1. 저장 책임 분리

`DB.png`는 PostgreSQL/PostGIS를 전제로 한 서버 전체 모델입니다. Android Room은 그 스키마를 복제하는 데이터베이스가 아닙니다. 앱은 오프라인 UX와 재전송에 필요한 최소 데이터만 저장하고, 권한·관계·정합성의 원본은 서버가 소유합니다.

| 데이터 | 서버 원본 | Android Room | 이유 |
|---|---|---|---|
| 계정, 이메일, password hash, 계정 상태 | 저장 | 저장 안 함 | 인증·운영 원본이며 비밀번호 자료는 기기에 두지 않음 |
| access/refresh token | 발급·검증 | Room 저장 안 함 | Android 보안 저장소 사용 대상 |
| 프로필·멜로디 별칭·취향 | 저장 | 필요한 표시 캐시만 선택 | 서버 원본, 오프라인 화면 보조 |
| 공개 범위, 차단, 팔로우·맞팔 | 저장 | UI 캐시만 선택 | 권한 판단은 반드시 서버가 수행 |
| Presence 세션 | TTL 저장 | 저장 안 함 | 실시간 임시 상태 |
| 원시 좌표, 정확도, area 판정 | 최신값 TTL 저장 | 저장 안 함 | 민감 위치 이력 방지 |
| 주변 사용자 snapshot·Delta | 계산·전송 | 영속 저장하지 않거나 짧은 비민감 캐시 | 재접속 시 서버 snapshot으로 교체 |
| 현재 음악 상태 | TTL·정규화 track 저장 | 현재 표시 캐시 선택 | 장기 청취 이력으로 확장하지 않음 |
| 알림 | 저장 | 읽기 UX용 캐시 선택 | 서버가 원본 |
| 채팅 메시지 | 저장 | 최근 대화 캐시·미전송 outbox 선택 | 오프라인 표시와 멱등 재전송 |
| 라운지 멤버·카드·투표·리액션 | 저장 또는 TTL | 화면 캐시·미전송 outbox 선택 | Topic 재구독 후 서버 상태로 정합화 |
| 오프라인 음악 카드 교환 | 동기화 후 저장 | 저장 | Room의 핵심 소유 데이터 |
| 오프라인 contact token | hash·사용 상태 저장 | 원문은 만료 전 최소 보관 | 만료·동기화 후 삭제 |
| 신고·운영 상태 | 저장 | 저장 안 함 | 서버 운영 원본 |
| 지역·행사·라운지 정의 | 저장 | 읽기 캐시 선택 | 서버가 활성 상태와 만료를 결정 |

현재 Android 구현에서 공유 서비스가 쓰는 비민감 운영 상태는 Room과 별개입니다. `melody_bubble_sharing_state` SharedPreferences에는 공유 활성 여부, 마지막 갱신 시각, `excellent/good/fair/poor/unknown` 정확도 품질만 저장합니다. 위·경도, provider, 이동 경로는 저장하거나 로그하지 않으며 현재 데모에서는 서버로도 전송하지 않습니다. `melody_bubble_now_playing_fallback`에는 transport 알림에서 길이를 제한·정리한 제목, 표시 문자열, 갱신 시각, 활성 여부만 저장하고, 앱이 다시 활성화될 때 이를 현재 음악의 수동 폴백 모델로 읽습니다. 원본 앱 패키지·알림 key·장기 청취 이력은 남기지 않습니다. `melody-bubble-session`에는 온보딩 완료 여부만 둡니다.

## 2. 현재 Room 모델

현재 Room DB 이름은 `melody-bubble-local.db`이고, 아래 두 테이블만 가집니다. 이는 서버 `DB.png`의 축소 복사본이 아니라 데모 오프라인 교환과 멱등 동기화 경계를 검증하기 위한 로컬 모델입니다.

### `offline_exchange_local`

| 필드 | 설명 |
|---|---|
| `id` | 로컬 레코드 기본 키 |
| `localSessionId` | 기기에서 만든 교환 세션 멱등 키 |
| `peerDisplayAlias` | 상대의 비식별 표시 별칭 |
| `trackTitle`, `trackArtist` | 사용자가 데모 교환한 음악 카드 |
| `melodyAlias` | 공개용 멜로디 별칭 표시 문자열 |
| `exchangedAt`, `expiresAt?` | 교환·만료 시각; 현재 7일 후 정리 |
| `syncState` | 현재 `PENDING`, `SYNCED`, `FAILED` 도메인 상태 |

### `sync_outbox`

| 필드 | 설명 |
|---|---|
| `id` | outbox 기본 키 |
| `kind` | 현재 `OFFLINE_EXCHANGE` |
| `requestId` | 서버 중복 처리 방지용 ID |
| `payloadJson` | 현재는 비민감 `exchangeId`만 포함 |
| `createdAt`, `retryCount`, `lastError?` | 생성·재시도 상태 |

현재 채팅, 알림, 주변 snapshot, 프로필은 Room에 저장하지 않고 메모리 데모 상태를 사용합니다. 오프라인 교환 `syncExchange`도 실제 REST 호출이 아니라 500ms 뒤 레코드를 `SYNCED`로 바꾸고 outbox를 제거하는 데모입니다.

Room은 개발 단계에서 destructive migration fallback을 사용합니다. 운영 데이터가 생기기 전 명시적 migration과 삭제·내보내기 정책으로 교체해야 합니다.

### `DB.png` 오프라인 영역과의 직접 비교

| `DB.png` 서버 모델 | 현재 Android Room | 차이와 후속 처리 |
|---|---|---|
| `offline_exchanges.id`, owner/user, peer user, peer token, track FK, local session, TTL | id, localSessionId, peer 표시 별칭, 곡 제목·아티스트 snapshot, 멜로디 표시, 시각·상태·만료 | Room에는 계정 FK가 없고 서버의 관계 원본이 아님. 실제 sync 시 localSessionId 멱등 제약과 서버 ID 매핑 필요 |
| `offline_exchange_tokens`의 token hash, 발급·만료·사용 시각 | 없음 | 현재 Nearby·token 발급은 미구현. 후속에서 원문 token을 보관해야 한다면 보안 저장소와 즉시 삭제 정책 필요 |
| 서버의 `tracks` 정규화 FK | Room 문자열 snapshot | 오프라인 표시를 위한 의도적 비정규화. sync 때 서버가 track을 정규화 |
| 서버의 사용자·privacy·block 검증 | Room에 없음 | 연결 가능 여부와 계정 연결은 서버가 재검증하며 로컬 카드 표시만 독립적으로 허용 |
| 서버 동기화 결과 | `syncState`, `sync_outbox` | 현재는 데모 ACK. 실제 adapter는 성공한 outbox만 삭제하고 오류 코드·재시도 횟수를 갱신 |

현재 만료된 `offline_exchange_local`은 앱 시작 시 정리되지만 연관 outbox 정리는 별도 cascade가 아닙니다. 실제 동기화 전에는 orphan outbox 정리와 `localSessionId` unique index를 보완해야 합니다.

### 후속 선택적 비민감 캐시

- 라운지 목록·카드·투표 snapshot
- 알림 목록
- 본인 프로필·공개 범위의 표시용 사본
- 공개 범위가 적용된 주변 버블의 짧은 캐시

주변 캐시를 두더라도 만료형 `nearbyHandle`, 가상 `displayPosition`, 공개 음악 요약만 허용하고 앱 재시작 또는 짧은 TTL 후 폐기합니다.

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
| 사용자 기본 시각 | `users.profile_color`는 있으나 프로필 이미지 정책은 불명확 | MVP는 색상·멜로디 별칭 중심, 이미지가 필요하면 별도 nullable URL과 공개 정책 추가 |
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

- 와이어프레임에 적힌 미터 값은 구현 계약에서 제거합니다.
- UI는 `탐색 범위 안`, `가까운 편`처럼 넓은 상태만 표시합니다.
- 레이더형 Canvas는 사회적 발견을 위한 가상 배치이며 나침반 방향과 연결하지 않습니다.
- `displayPosition.x/y`는 중첩 방지용 정규화 값입니다. 매 업데이트마다 실제 좌표 변화와 동일하게 움직이지 않도록 안정화합니다.
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

- 현재 앱은 시작 시 만료된 `offline_exchange_local`을 삭제하고, 데모 동기화 성공 시 해당 `sync_outbox`를 제거합니다.
- 현재 Manifest는 `android:allowBackup="false"`로 앱 자동 백업을 비활성화합니다. 향후 백업을 켜더라도 토큰, 오프라인 contact token, 민감 캐시는 반드시 제외합니다.
- 디버그 로그에도 URL 자격 증명, Authorization 헤더, 좌표, 메시지 본문 전체를 출력하지 않습니다.
- 후속 Nearby 구현에서 오프라인 contact token을 기기에 임시 보관한다면 동기화 성공, 만료 또는 사용자 삭제 시 즉시 제거합니다. 현재 Room에는 token을 저장하지 않습니다.
- 후속 실제 동기화에서는 실패 outbox의 재시도·삭제 UI, orphan 정리, 기록 전체 삭제 경로를 추가합니다. 현재는 이 기능들이 없습니다.
