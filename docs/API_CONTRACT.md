# 화면 데이터 출처와 API·STOMP 계약

이 문서는 원본 기획의 통신 방향을 Android MVP가 교체 가능한 데이터 소스로 사용할 수 있도록 정리한 기준입니다. 저장소에 백엔드나 네트워크 어댑터는 포함되지 않으므로 아래 경로는 서버 연동 시 합의할 버전 `v1` 계약이며, 현재 호출된다는 뜻이 아닙니다. 현재 앱은 항상 데모 저장소를 사용합니다. 코드에 선언된 endpoint/destination과 기획상 후속 항목을 구분해 적습니다.

### 현재 Android에 선언된 계약 경계

- REST: 로그인, 토큰 갱신, 내 프로필·공개 범위, 주변 snapshot·상세, 라운지 목록·상세, 대화방·과거 메시지, 알림, 오프라인 교환 동기화
- STOMP SEND: Presence, 위치·음악, 리액션, 채팅·읽음, 라운지 입장·퇴장·카드·리액션·투표
- STOMP SUBSCRIBE: 개인 nearby/chat/notifications/reactions/ack/errors Queue와 라운지 state/cards/votes Topic
- 후속 계약: 회원가입, 취향 저장, 팔로우·차단·신고 REST, 채팅 typing, 지역 trend·pulse, 라운지 reaction Topic, 행사 Topic

선언된 계약도 아직 HTTP/WebSocket 클라이언트에 연결되지 않았습니다. URL이 설정되면 현재는 `DEMO FALLBACK` 라벨만 사용합니다.

## 1. 데이터 소스 우선순위

| 상태 | 초기 데이터 | 실시간 변화 | 실패 시 처리 |
|---|---|---|---|
| 데모 | 번들 시드·로컬 상태 | 앱 내부 데모 이벤트 | 해당 없음 |
| 실제 연결 정상 | REST snapshot | STOMP Queue·Topic | 마지막 비민감 캐시와 재연결 상태 표시 |
| REST만 정상 | REST snapshot | 실시간 갱신 불가 | 실시간 중단을 표시하고 오래된 데이터를 최신처럼 보이지 않음 |
| STOMP만 정상 | 기준 snapshot 없음 | Delta만으로 전체 상태 구성 금지 | 설정 오류 또는 데모 폴백 |
| 재연결 | REST snapshot 재조회 | sequence 이후 Delta 적용 | snapshot 교체 전 Delta를 임시 보류 또는 폐기 |

## 2. 화면별 데이터 출처

| 화면 | 데모 출처 | 실제 초기 조회·명령 | 실시간 구독·전송 | 화면에 허용되는 데이터 |
|---|---|---|---|---|
| 시작·로그인 | 데모 세션 | `POST /api/v1/auth/login`, `/refresh`; signup은 후속 | 없음 | 인증 상태, 오류 |
| 온보딩·취향 | 시드 장르·분위기, 완료 여부는 SharedPreferences | `PUT /api/v1/me/privacy`; 취향 저장은 후속 | 없음 | 본인이 입력한 취향·공개 범위 |
| 홈 | 시드 주변 요약·인기 음악, Foreground Service 공유 상태 | `GET /api/v1/nearby/snapshot`; 지역 trend REST는 후속 | `/user/queue/nearby`; Presence·location·music SEND; 지역 trend Topic은 후속 | 익명 버블, 가상 배치, 유사도, 공개 허용 음악, 익명 집계 |
| 근처 | 홈과 같은 시드 목록 | `GET /api/v1/nearby/snapshot` | `/user/queue/nearby` | 정확 거리·방향 없는 주변 사용자 카드 |
| 사용자 상세 | 선택한 시드 사용자 | snapshot에 포함된 공개 필드 또는 `GET /api/v1/nearby/{nearbyHandle}` | 리액션 `/app/reaction/send`; 결과 `/user/queue/reactions`, `/ack` | 임시 handle, 별칭, 유사도, 공개 음악·취향 요약 |
| 팔로우 | 메모리 상태 전환 | 후속 `PUT`·`DELETE /api/v1/nearby/{nearbyHandle}/follow` | 결과 `/user/queue/notifications` | 팔로우·맞팔 상태 |
| 차단·신고 | 차단 시 메모리 목록 제거, 신고 시 데모 접수 피드백 | 후속 `PUT /api/v1/nearby/{nearbyHandle}/block`, `POST /api/v1/nearby/{nearbyHandle}/reports` | 결과는 개인 `/user/queue/notifications|ack|errors` | 공개 메시지 없이 본인 처리 결과만 표시 |
| 라운지 목록 | 시드 라운지 | `GET /api/v1/rooms?areaId={areaId}` | 선택 사항: 지역 notice | 이름, 상태, 익명 참여자 수, 장르·분위기 |
| 라운지 상세 | 시드 카드·투표 | `GET /api/v1/rooms/{roomId}` | `/app/room/join|leave|card|reaction|vote`; 현재 구독은 `state|cards|votes`, reaction Topic은 후속 | 집계·추천곡 카드·정해진 리액션·투표 |
| 인박스 | 시드 알림·대화방 | `GET /api/v1/notifications`, `GET /api/v1/chat/rooms` | `/user/queue/notifications`, `/user/queue/reactions`, `/user/queue/chat` | 본인에게 전달된 알림·대화 미리보기 |
| 1:1 채팅 | 시드 대화 | `GET /api/v1/chat/rooms/{roomId}/messages?cursor=...` | 현재 `/app/chat/send|read`; typing은 후속, 수신은 `/user/queue/chat|ack|errors` | 맞팔 대화방의 텍스트와 전송 상태 |
| 마이·설정 | 로컬 데모 프로필, 알림 접근 설정 진입 | `GET /api/v1/me`, `PATCH /api/v1/me`, `PUT /api/v1/me/privacy` | 설정 반영 알림은 개인 Queue 선택 | 본인 프로필·취향·공개 범위 |
| 현재 음악 선택 | `DemoCatalog` 최근 곡 또는 알림 listener의 최소 폴백 문자열 | 후속 음악 검색 REST가 필요할 수 있음 | `/app/music/update` | 제목·아티스트·source; 앱 계정·원본 알림 전체 제외 |
| 오프라인 기록 | Room `offline_exchange_local`·`sync_outbox` | `POST /api/v1/offline-exchanges/sync` | Nearby Connections는 후속이며 STOMP와 별도 | 직접 승인한 음악 카드와 동기화 상태 |

`nearbyHandle`은 현재 Presence 범위에서만 쓰는 불투명 식별자여야 합니다. 사용자 상세에서 서버의 영구 UUID를 주변 사용자에게 그대로 노출하지 않습니다. 기획 초안의 `temporaryUserId` 개념은 Android·API 계약에서 `nearbyHandle`로 통일합니다.

## 3. REST 공통 규칙

- 기준 URL: `API_BASE_URL`
- 전송 형식: `application/json; charset=utf-8`
- 인증: `Authorization: Bearer {accessToken}`
- 시간: UTC ISO-8601 문자열
- 페이지 목록: `items`, `nextCursor`
- 변경 요청: 가능한 경우 `requestId`로 멱등 처리
- 오류 응답은 좌표, 토큰, 내부 스택을 포함하지 않음

공통 오류 예시:

```json
{
  "requestId": "client-request-uuid",
  "code": "NOT_ALLOWED",
  "message": "현재 공개 범위에서는 요청을 처리할 수 없습니다.",
  "retryable": false
}
```

### 인증 — 선언됨, 어댑터 미연결

```http
POST /api/v1/auth/login
```

```json
{
  "email": "demo@example.com",
  "password": "user-entered-password",
  "deviceName": "Android"
}
```

```json
{
  "accessToken": "opaque-jwt-value",
  "refreshToken": "opaque-refresh-value",
  "expiresAt": "2026-07-10T16:00:00Z",
  "user": {
    "id": "user-uuid",
    "nickname": "JH Melody",
    "profileColor": "#248A55"
  }
}
```

비밀번호와 토큰은 로그, Room, 데모 fixture에 기록하지 않습니다.

### 주변 snapshot — 선언됨, 어댑터 미연결

```http
GET /api/v1/nearby/snapshot
```

```json
{
  "snapshotVersion": 23,
  "sequence": 1081,
  "generatedAt": "2026-07-10T15:30:00Z",
  "area": {
    "areaId": "campus-main",
    "label": "캠퍼스",
    "activeListenerCount": 24
  },
  "users": [
    {
      "nearbyHandle": "nearby-mint-session",
      "melodyAlias": "C-E-G",
      "profileColor": "#248A55",
      "displayPosition": { "x": 0.68, "y": 0.31 },
      "proximity": "VERY_CLOSE",
      "matchScore": 82,
      "visualType": "WAVE",
      "isPlaying": true,
      "primaryGenre": "INDIE",
      "mood": "CALM",
      "track": {
        "title": "Blue Night",
        "artist": "Wave to Earth",
        "platform": "MANUAL"
      }
    }
  ]
}
```

`displayPosition`은 서버 또는 앱이 만든 충돌 방지용 정규화 좌표이며 실제 방위를 뜻하지 않습니다. `proximity`는 `VERY_CLOSE`, `CLOSE`, `AROUND`처럼 넓은 상태만 제공합니다. `distanceMeters`, bearing, 위·경도는 응답에 넣지 않습니다.

### 프로필·취향·공개 범위 — 취향 endpoint는 후속

```http
GET /api/v1/me
PUT /api/v1/me/taste-profile
PUT /api/v1/me/privacy
```

```json
{
  "preferredGenres": ["INDIE", "RNB"],
  "preferredArtists": ["Wave to Earth"],
  "representativeTrackIds": ["track-uuid"],
  "moodTags": ["CALM", "NIGHT"]
}
```

```json
{
  "discoverability": "NEARBY",
  "musicVisibility": "TITLE_ARTIST",
  "minMatchScore": 60,
  "allowReactions": true,
  "offlineExchangeEnabled": false
}
```

### 팔로우 — 후속 계약

```http
PUT /api/v1/nearby/{nearbyHandle}/follow
DELETE /api/v1/nearby/{nearbyHandle}/follow
```

```json
{
  "following": true,
  "mutual": false,
  "updatedAt": "2026-07-10T15:30:00Z"
}
```

### 차단·신고 — 후속 계약

```http
PUT /api/v1/nearby/{nearbyHandle}/block
POST /api/v1/nearby/{nearbyHandle}/reports
```

```json
{
  "requestId": "client-request-uuid",
  "reason": "SPAM",
  "description": null
}
```

차단·신고 요청의 handle→사용자 UUID 해석, 중복 방지, 제재 상태는 서버 내부에서 처리합니다. 처리 결과를 공개 Topic으로 발행하지 않습니다.

### 라운지 목록·상세 — 선언됨, 어댑터 미연결

```http
GET /api/v1/rooms?areaId={areaId}
GET /api/v1/rooms/{roomId}
```

```json
{
  "id": "room-campus",
  "name": "캠퍼스 라운지",
  "status": "LIVE",
  "participantCount": 42,
  "moodTags": ["INDIE", "CALM", "NIGHT"],
  "cards": [
    {
      "cardId": "card-uuid",
      "title": "Blue Night",
      "artist": "Wave to Earth",
      "reactionCount": 12
    }
  ],
  "vote": {
    "voteType": "MOOD",
    "options": [
      { "targetKey": "INDIE", "percentage": 46 },
      { "targetKey": "RNB", "percentage": 31 },
      { "targetKey": "POP", "percentage": 23 }
    ]
  }
}
```

### 과거 채팅 — 선언됨, 어댑터 미연결

```http
GET /api/v1/chat/rooms
GET /api/v1/chat/rooms/{roomId}/messages?cursor={cursor}&limit=30
```

```json
{
  "items": [
    {
      "messageId": "server-message-uuid",
      "clientMessageId": "client-message-uuid",
      "senderId": "user-uuid",
      "content": "이 노래 저도 좋아해요.",
      "messageType": "TEXT",
      "sentAt": "2026-07-10T15:30:01Z",
      "readAt": null
    }
  ],
  "nextCursor": null
}
```

### 오프라인 교환 동기화 — 선언됨, 현재는 로컬 데모 ACK

```http
POST /api/v1/offline-exchanges/sync
```

```json
{
  "exchanges": [
    {
      "localSessionId": "local-session-uuid",
      "offlineContactToken": "signed-opaque-token-or-null",
      "musicCard": {
        "title": "Blue Night",
        "artist": "Wave to Earth",
        "platform": "MANUAL",
        "externalTrackId": null
      },
      "exchangedAt": "2026-07-10T15:30:00Z"
    }
  ]
}
```

```json
{
  "results": [
    {
      "localSessionId": "local-session-uuid",
      "status": "SYNCED",
      "serverExchangeId": "exchange-uuid"
    }
  ]
}
```

## 4. STOMP 연결

- 연결 URL: `STOMP_WS_URL`
- 프로토콜: STOMP over WSS
- 연결 헤더: `Authorization: Bearer {accessToken}`
- 권장 STOMP heartbeat: 10~20초
- Presence heartbeat: 30초, 서버 TTL 기준 90초
- 클라이언트 SEND: `/app/**`만 허용
- 클라이언트가 `/topic/**`, `/queue/**`, `/user/**`로 직접 SEND하지 않음
- 재연결 후 재구독하고 REST snapshot을 다시 받은 뒤 Delta를 적용

공통 서버 이벤트:

```json
{
  "eventId": "event-uuid",
  "requestId": "optional-client-request-uuid",
  "type": "NEARBY_USERS_DELTA",
  "version": 1,
  "sequence": 1082,
  "timestamp": "2026-07-10T15:30:00Z",
  "payload": {}
}
```

`eventId`는 중복 제거, `requestId`는 ACK 연결, `sequence`는 Queue 또는 Topic 스트림 내부 순서 역전 감지에 사용합니다. 서로 다른 destination의 sequence를 하나의 전역 순서처럼 비교하지 않습니다.

## 5. Android → 서버 SEND 계약

| Destination | 핵심 payload | 저장 여부 |
|---|---|---|
| `/app/presence/start` | `requestId`, `clientSessionId`, `clientTimestamp` | 서버 Presence/TTL |
| `/app/presence/stop` | `requestId`, `clientSessionId` | 세션 종료 |
| `/app/presence/heartbeat` | `requestId`, `clientSessionId`, `clientTimestamp` | TTL 갱신 |
| `/app/location/update` | `requestId`, `clientSessionId`, `latitude`, `longitude`, `accuracyMeters`, `clientTimestamp` | 최신값만 TTL 저장; 이력 금지 |
| `/app/music/update` | `requestId`, `track`, `isPlaying`, `sourceType`, `clientTimestamp` | 현재 상태 TTL·정규화 track |
| `/app/reaction/send` | `requestId`, `receiverNearbyHandle`, `reactionType`, `musicStatusId?` | 서버가 handle을 내부 사용자로 해석한 뒤 리액션 저장 |
| `/app/chat/send` | `requestId`, `clientMessageId`, `roomId`, `content`, `clientSentAt` | 검증 후 메시지 저장 |
| `/app/chat/read` | `requestId`, `roomId`, `lastReadMessageId` | read 상태 갱신 |
| `/app/chat/typing` (후속) | `roomId`, `isTyping` | 저장 안 함 |
| `/app/room/join` | `requestId`, `roomId` | 멤버십·현재 접속 갱신 |
| `/app/room/leave` | `requestId`, `roomId` | 퇴장 시각 갱신 |
| `/app/room/card` | `requestId`, `roomId`, `track` | 카드 TTL 저장 |
| `/app/room/reaction` | `requestId`, `roomId`, `cardId?`, `reactionType` | 중복 방지 후 저장 |
| `/app/room/vote` | `requestId`, `roomId`, `voteType`, `targetKey` | 사용자·voteType별 upsert |
| `/app/area/pulse` (후속) | `requestId`, `pulseType`, `trackKey?` | 임시 집계만 |

위·경도는 기기에서 서버로 보내는 민감 입력일 뿐, 주변 사용자 응답이나 공개 Topic payload로 되돌려 보내지 않습니다.

음악 상태 요청 예시:

```json
{
  "requestId": "client-request-uuid",
  "track": {
    "title": "Blue Night",
    "artist": "Wave to Earth",
    "album": null,
    "platform": "MEDIA_SESSION",
    "externalTrackId": null
  },
  "isPlaying": true,
  "sourceType": "MEDIA_SESSION",
  "clientTimestamp": "2026-07-10T15:30:00Z"
}
```

채팅 요청 예시:

```json
{
  "requestId": "client-request-uuid",
  "clientMessageId": "client-message-uuid",
  "roomId": "room-481",
  "content": "이 노래 저도 좋아해요.",
  "clientSentAt": "2026-07-10T15:30:00Z"
}
```

## 6. 서버 → Android 구독 계약

### 개인 Queue

| Destination | 이벤트 type | payload |
|---|---|---|
| `/user/queue/nearby` | `NEARBY_USERS_DELTA` | `entered[]`, `updated[]`, `left[]` |
| `/user/queue/chat` | `CHAT_MESSAGE`, `CHAT_READ`, `CHAT_TYPING` | 대화방·메시지 또는 일시 상태 |
| `/user/queue/notifications` | `FOLLOWED`, `MUTUAL_FOLLOW`, `SYSTEM_NOTICE` | 알림 ID·종류·비민감 표시 정보 |
| `/user/queue/reactions` | `MUSIC_REACTION_RECEIVED` | sender 표시용 임시 ID·reactionType·시각 |
| `/user/queue/ack` | `REQUEST_ACK` | `requestId`, `status`, 서버 생성 ID·시각 |
| `/user/queue/errors` | `REQUEST_ERROR` | `requestId?`, `code`, `message`, `retryable` |

주변 Delta 예시:

```json
{
  "eventId": "event-uuid",
  "type": "NEARBY_USERS_DELTA",
  "version": 1,
  "sequence": 1082,
  "timestamp": "2026-07-10T15:30:00Z",
  "payload": {
    "entered": [],
    "updated": [
      {
        "nearbyHandle": "nearby-mint-session",
        "displayPosition": { "x": 0.52, "y": 0.42 },
        "proximity": "VERY_CLOSE",
        "matchScore": 84,
        "isPlaying": true,
        "primaryGenre": "INDIE",
        "mood": "CALM"
      }
    ],
    "left": ["temp-old"]
  }
}
```

ACK 예시:

```json
{
  "eventId": "event-uuid",
  "requestId": "client-request-uuid",
  "type": "REQUEST_ACK",
  "version": 1,
  "sequence": 220,
  "timestamp": "2026-07-10T15:30:01Z",
  "payload": {
    "status": "ACCEPTED",
    "resourceType": "CHAT_MESSAGE",
    "resourceId": "server-message-uuid"
  }
}
```

### 다중 사용자 Topic

| Destination | payload 원칙 |
|---|---|
| `/topic/area/{areaId}/trend` (후속) | 최소 인원 기준을 통과한 장르·아티스트·곡·분위기 집계 |
| `/topic/area/{areaId}/pulse` (후속) | `pulseType`, 정규화 track key, 집계 count, 짧은 만료 시간 |
| `/topic/room/{roomId}/state` | 익명 참여자 수, 현재 분위기, 라운지 상태 |
| `/topic/room/{roomId}/cards` | 카드 ID, 공개 가능한 곡 정보, 익명 반응 집계 |
| `/topic/room/{roomId}/reactions` (후속) | 카드별 reactionType과 갱신된 집계 |
| `/topic/room/{roomId}/votes` | voteType, option별 count·percentage |
| `/topic/event/{eventId}/live` (후속) | 개인 식별자가 없는 행사 집계 상태 |
| `/topic/event/{eventId}/notice` (후속) | 운영 공지 |

공개 Topic에는 영구 사용자 ID, 닉네임, 좌표, 정확 거리, 실제 방향, 개별 청취 이력을 넣지 않습니다. 적은 인원으로 개인을 추정할 수 있는 집계는 서버가 발행하지 않아야 합니다.

## 7. 권한·오류 기준

- 개인 Queue는 본인만 구독합니다.
- 라운지 Topic은 가입 또는 입장 권한 확인 후 구독합니다.
- 채팅 SEND는 맞팔·차단·계정 상태를 서버에서 다시 검증합니다.
- `requestId`와 `clientMessageId`로 재전송 중복을 제거합니다.
- 입력 중 상태, 위치, heartbeat, pulse는 유실되어도 재전송 큐에 영구 저장하지 않습니다.
- 채팅, 음악 리액션, 투표는 서버 DB 저장 성공 후 발행하고 ACK 합니다.
- 토큰 만료 시 REST refresh → STOMP 재연결 → 재구독 → REST snapshot 순으로 복구합니다.
