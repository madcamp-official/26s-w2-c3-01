# 화면 데이터 출처와 API·STOMP 계약

이 문서는 Android 앱과 Spring 서버가 사용하는 버전 `v1` 통신 계약입니다. 인증된 세션에서는 실제 REST API를 사용하며, 주변 Presence와 관계·안전 기능은 서버 응답을 기준으로 화면 상태를 구성합니다.

### 현재 Android에 선언된 계약 경계

- REST: 로그인, 토큰 갱신, 내 프로필·Presence 공개 범위, 주변 snapshot·인기 음악·리액션, 팔로우·차단·신고, 맞팔 대화방·메시지·읽음
- STOMP SEND: Presence, 위치·음악, 리액션, 채팅·읽음, 라운지 입장·퇴장·카드·리액션·투표
- STOMP SUBSCRIBE: 개인 nearby/chat/notifications/reactions/errors Queue와 참가 중인 하위 라운지 Topic
- 후속 계약: 채팅 typing, 지역 trend·pulse, 라운지 reaction Topic, 행사 Topic

좌표는 Presence TTL 동안 최신값 한 건만 저장하며 주변 응답에는 실제 좌표·방향·정확 거리를 포함하지 않습니다.

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
| 홈 | 시드 주변 요약·인기 음악, Foreground Service 공유 상태 | `GET /api/v1/nearby/snapshot`, `GET /api/v1/nearby/popular-tracks` | `/user/queue/nearby`; 음악 상태 변경과 인기 음악 집계 수신 | 익명 버블, 가상 배치, 유사도, 공개 허용 음악, 익명 집계 |
| 근처 | 홈과 같은 시드 목록 | `GET /api/v1/nearby/snapshot` | `/user/queue/nearby` | 정확 거리·방향 없는 주변 사용자 카드 |
| 사용자 상세 | 선택한 시드 사용자 | snapshot 또는 `GET /api/v1/nearby/{nearbyHandle}`; `GET /api/v1/profiles/{profileHandle}` | 수신 리액션 `/user/queue/reactions` | 임시 handle과 안정적인 공개 profile handle, 공개 음악·취향 요약 |
| 팔로우 | 서버 관계 상태 | `PUT`·`DELETE /api/v1/nearby/{nearbyHandle}/follow`, `PUT`·`DELETE /api/v1/profiles/{profileHandle}/follow` | 결과 `/user/queue/notifications` 확장 가능 | 팔로우·맞팔 상태와 맞팔 대화방 ID |
| 차단·신고 | 서버 차단 목록·신고 접수 | `PUT /api/v1/nearby/{nearbyHandle}/block`, `GET /api/v1/me/blocks`, `DELETE /api/v1/me/blocks/{blockId}`, `POST /api/v1/nearby/{nearbyHandle}/reports` | 처리 결과는 요청자에게만 반환 | 공개 메시지 없이 본인 처리 결과만 표시 |
| 라운지 목록 | 위치 기반 건물 라운지 | `GET /api/v1/building-lounges/nearby` | 없음 | 실제 좌표는 서버 입장 검증에만 사용 |
| 라운지 상세 | 서버 하위 라운지 snapshot | 참가·퇴장·청취·카드·리액션·투표 REST | `/topic/sub-lounges/{subLoungeId}` | 별칭·공개 음악·추천 카드·집계 투표 |
| 인박스 | 서버 리액션 이력·대화방 | `GET /api/v1/nearby/reactions`, `GET /api/v1/notifications`, `GET /api/v1/chat/rooms` | `/user/queue/notifications`, `/user/queue/reactions`, `/user/queue/chat` | 본인에게 전달된 알림·대화 미리보기 |
| 1:1 채팅 | 서버 대화 | `GET /api/v1/chat/rooms`, `GET`·`POST /api/v1/chat/rooms/{roomId}/messages`, `PUT /api/v1/chat/rooms/{roomId}/read` | `/user/queue/chat`의 생성·메시지·읽음·방 갱신 이벤트 | 맞팔이며 차단되지 않은 대화방의 텍스트와 전송 상태 |
| 마이·설정 | 캐시된 계정별 프로필 | `GET /api/v1/me`, `PATCH /api/v1/me`, `PUT /api/v1/me/privacy`, `PUT /api/v1/me/melody-alias` | 설정 반영 알림은 개인 Queue 선택 | 본인 프로필·취향·공개 범위·멜로디 별칭·검증된 교환 통계 |
| 현재 음악 선택 | 앱 공용 `PresenceSyncCoordinator`가 MediaSession 감지, 세션 미제공 앱만 알림 문자열 폴백 | `POST /api/v1/nearby/music` | 변경 수신은 `/user/queue/nearby` | 제목·아티스트·source·재생 여부; 앱 계정·원본 알림 전체 제외 |
| 오프라인 기록 | 계정별 Room `offline_exchange_local`·`sync_outbox` | `POST /api/v1/offline-credentials`, `POST /api/v1/offline-exchanges/batch`, `GET`·`DELETE /api/v1/offline-exchanges/**` | Google Nearby Connections로 1:1 카드·서명 교환; STOMP와 별도 | 양쪽이 승인한 음악 카드, 검증 상태, 동기화 상태 |

`nearbyHandle`은 현재 Presence 범위에서만 쓰는 불투명 식별자입니다. 공개 프로필 이동에는 별도의 안정적인 `profileHandle`을 사용하며 서버의 영구 UUID는 노출하지 않습니다. 교환 기록에서 프로필로 이동할 때는 클라이언트 표시 이름이 아니라 서버의 `VERIFIED` 교환과 peer credential 소유자를 기준으로 해석합니다.

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

### 프로필·취향·공개 범위 — 구현됨

```http
GET /api/v1/me
PATCH /api/v1/me
PUT /api/v1/me/privacy
PUT /api/v1/me/profile-curation
PUT /api/v1/me/profile-privacy
PUT /api/v1/me/melody-alias
GET /api/v1/profiles/{profileHandle}
GET /api/v1/profiles/exchange/{exchangeId}
GET /api/v1/me/presence-settings
PUT /api/v1/me/presence-settings
```

```json
{
  "profileHandle": "listener_a1b2c3d4e5f6",
  "genres": ["INDIE", "RNB"],
  "moods": ["CALM", "NIGHT"],
  "stats": {
    "followingCount": 4,
    "followerCount": 3,
    "verifiedExchangeCount": 2,
    "uniqueExchangeUserCount": 2,
    "receivedCardCount": 2
  },
  "tasteFingerprint": {
    "genres": [{"label": "R&B", "count": 2, "ratio": 0.667}],
    "moods": [{"label": "Night", "count": 1, "ratio": 1.0}]
  },
  "profileRevision": 3,
  "signatureTracks": [
    {
      "rank": 1,
      "provider": "MANUAL",
      "title": "새벽의 온도",
      "artist": "Clouded Steps",
      "artworkUrl": null
    }
  ],
  "favoriteArtists": [
    {"rank": 1, "provider": "MANUAL", "name": "루엘", "imageUrl": null}
  ],
  "privacy": {
    "currentMusicVisibility": "MUTUALS",
    "listeningInsightsEnabled": false,
    "listeningInsightsVisibility": "PRIVATE",
    "exchangeInsightsVisibility": "EXCHANGED",
    "bubblePresenceVisibility": "PARTICIPANTS_ONLY"
  }
}
```

공개 프로필은 인증된 요청자와 대상의 차단·팔로우·음악 공개 범위를 적용합니다. `sharedVerifiedExchangeCount`는 양쪽 서명 기록이 일치한 교환만 포함하고, 개별 상대 목록이나 정확한 장소는 공개하지 않습니다.

공개 프로필 응답은 공개가 허용된 `nowPlaying`, 사용자 지정 `signatureTracks`·`favoriteArtists`, 요청자와 대상 사이에서 계산한 `commonTaste`를 포함할 수 있습니다. `sectionStates`는 각 섹션을 `VISIBLE`, `NO_DATA`, `PRIVATE`, `INSUFFICIENT_DATA`, `NOT_APPLICABLE`로 구분합니다. 현재 음악은 TTL이 유효한 `music_statuses` 한 건만 반환하며 청취 이력으로 간주하지 않습니다.

`profile-curation`은 대표곡·최애 아티스트를 각각 최대 3개까지 순서대로 저장합니다. 요청의 `profileRevision`이 서버와 다르면 `409 Conflict`를 반환합니다. `profile-privacy`는 현재 음악, 청취 분석, 교환 기반 취향, 버블 참여 상태의 공개 범위를 독립적으로 저장하며 서버가 수신자별로 집행합니다.

Presence 설정은 `discoverabilityScope`(`NEARBY`, `MUTUALS`, `HIDDEN`),
`musicVisibility`(`TITLE_ARTIST`, `MUTUALS`, `HIDDEN`), `discoveryRadiusMeters`(50–2000),
`allowReactions`를 저장합니다. 서버 주변 검색은 요청자의 저장된 반경과 상대의 공개 범위를 함께 집행합니다.

```json
{
  "discoverability": "NEARBY",
  "musicVisibility": "TITLE_ARTIST",
  "minMatchScore": 60,
  "allowReactions": true,
  "offlineExchangeEnabled": false
}
```

### 팔로우

```http
PUT /api/v1/nearby/{nearbyHandle}/follow
DELETE /api/v1/nearby/{nearbyHandle}/follow
PUT /api/v1/profiles/{profileHandle}/follow
DELETE /api/v1/profiles/{profileHandle}/follow
```

```json
{
  "following": true,
  "mutual": false,
  "updatedAt": "2026-07-10T15:30:00Z"
}
```

### 차단·신고

```http
PUT /api/v1/nearby/{nearbyHandle}/block
GET /api/v1/me/blocks
DELETE /api/v1/me/blocks/{blockId}
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

### 주변 음악 리액션 — 구현됨

```http
POST /api/v1/nearby/{nearbyHandle}/reactions
GET /api/v1/nearby/reactions?limit=100
```

전송은 `clientReactionId`로 멱등 처리합니다. 수신 이력 조회는 WebSocket 재연결 뒤 누락된 `/user/queue/reactions` 이벤트를 복구하는 용도이며 최신순으로 반환합니다.

### 건물·하위 라운지 — 구현됨

```http
GET /api/v1/building-lounges/nearby?latitude={lat}&longitude={lng}
POST /api/v1/building-lounges/{loungeId}/enter
POST /api/v1/building-lounges/{loungeId}/heartbeat
POST /api/v1/building-lounges/{loungeId}/leave
GET /api/v1/building-lounges/{loungeId}/sub-lounges
POST /api/v1/building-lounges/{loungeId}/sub-lounges
GET /api/v1/building-lounges/sub-lounges/{subLoungeId}
POST /api/v1/building-lounges/sub-lounges/{subLoungeId}/join
POST /api/v1/building-lounges/sub-lounges/{subLoungeId}/leave
PUT /api/v1/building-lounges/sub-lounges/{subLoungeId}/listening
POST /api/v1/building-lounges/sub-lounges/{subLoungeId}/cards
POST /api/v1/building-lounges/cards/{cardId}/reactions
PUT /api/v1/building-lounges/sub-lounges/{subLoungeId}/vote
```

```json
{
  "id": "sub-lounge-uuid",
  "buildingLoungeId": "building-lounge-uuid",
  "title": "사용자가 만든 재즈방",
  "style": "Jazz",
  "memberCount": 2,
  "joined": true,
  "cards": [
    {
      "id": "card-uuid",
      "trackTitle": "Blue Night",
      "artistName": "Wave to Earth",
      "reactionCount": 12
    }
  ],
  "poll": {
    "options": [
      { "key": "CHILL", "voteCount": 1 },
      { "key": "FOCUS", "voteCount": 0 },
      { "key": "ENERGY", "voteCount": 1 }
    ],
    "myVote": "CHILL"
  }
}
```

주변 조회는 OSM ID를 기준으로 실제 건물을 24시간 캐시합니다. OSM 장애 시 기존 캐시만 반환하며 테스트 fixture나 기본 하위 라운지를 생성하지 않습니다.

### 채팅 — 구현됨

```http
GET /api/v1/chat/rooms
GET /api/v1/chat/rooms/{roomId}/messages?limit=50
POST /api/v1/chat/rooms/{roomId}/messages
PUT /api/v1/chat/rooms/{roomId}/read
```

```json
[
  {
    "messageId": "server-message-uuid",
    "clientMessageId": "client-message-uuid",
    "roomId": "room-uuid",
    "isMine": false,
    "content": "이 노래 저도 좋아해요.",
    "sentAt": "2026-07-10T15:30:01Z",
    "readByPeer": false
  }
]
```

### 오프라인 교환 인증서와 동기화

온라인 상태에서 계정과 Android Keystore 기기 공개키를 묶은 30일짜리 서버 서명 인증서를 받습니다. 서버의 `publicSubject`는 계정 UUID를 그대로 노출하지 않는 안정적인 불투명 값입니다.

```http
POST /api/v1/offline-credentials
```

```json
{
  "devicePublicKey": "base64-x509-ec-public-key"
}
```

오프라인에서는 두 기기가 Nearby Connections로 인증서와 공개 음악 카드를 교환하고, 동일한 숫자 인증 코드를 사용자가 양쪽에서 승인합니다. 각 기기는 합의된 payload hash와 교환 ID를 기기 키로 서명한 뒤 양방향 ACK까지 완료된 기록만 Room에 저장합니다.

인터넷 연결이 복구되면 WorkManager가 계정별 outbox를 최대 50건씩 전송합니다.

```http
POST /api/v1/offline-exchanges/batch
```

```json
{
  "items": [
    {
      "exchangeId": "exchange-uuid",
      "credentialId": "my-credential-uuid",
      "peerCredentialId": "peer-credential-uuid",
      "sentCardJson": "{...}",
      "receivedCardJson": "{...}",
      "deviceOccurredAt": 1783693800000,
      "payloadHash": "sha256-hex",
      "protocolVersion": 1,
      "recordSignature": "base64-ecdsa-signature"
    }
  ]
}
```

```json
[
  { "exchangeId": "exchange-uuid", "state": "UNCONFIRMED" }
]
```

첫 번째 참가자의 업로드는 `UNCONFIRMED`, 반대편의 동일한 hash·역방향 credential 쌍까지 도착하면 양쪽 기록은 `VERIFIED`가 됩니다. 같은 `(exchangeId, participant)`의 동일 요청은 멱등 처리하며 내용이 다른 재사용은 `400`으로 거절합니다.

```http
GET /api/v1/offline-exchanges
DELETE /api/v1/offline-exchanges/{exchangeId}
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
  "type": "CHAT_MESSAGE_CREATED",
  "version": 1,
  "timestamp": "2026-07-10T15:30:00Z",
  "payload": {}
}
```

`eventId`는 클라이언트 중복 제거에 사용합니다. 알 수 없는 `type` 또는 상위 `version`은 무시하고 재연결 뒤 REST 전체 동기화로 복구합니다.

## 5. Android → 서버 SEND 계약

| Destination | 핵심 payload | 저장 여부 |
|---|---|---|
| `/app/presence/start` | `requestId`, `clientSessionId`, `clientTimestamp` | 서버 Presence/TTL |
| `/app/presence/stop` | `requestId`, `clientSessionId` | 세션 종료 |
| `/app/presence/heartbeat` | `requestId`, `clientSessionId`, `clientTimestamp` | TTL 갱신 |
| `/app/location/update` | `requestId`, `clientSessionId`, `latitude`, `longitude`, `accuracyMeters`, `clientTimestamp` | 최신값만 TTL 저장; 이력 금지 |
| `/app/music/update` | `requestId`, `track`, `isPlaying`, `sourceType`, `clientTimestamp` | 현재 상태 TTL·정규화 track |
| `/app/reaction/send` (후속) | `requestId`, `receiverNearbyHandle`, `reactionType`, `musicStatusId?` | 현재 구현은 REST `POST /api/v1/nearby/{nearbyHandle}/reactions` 사용 |
| `/app/chat/send` (후속) | `requestId`, `clientMessageId`, `roomId`, `content`, `clientSentAt` | 현재 구현은 REST 메시지 저장 API 사용 |
| `/app/chat/read` (후속) | `requestId`, `roomId`, `lastReadMessageId` | 현재 구현은 REST `PUT /api/v1/chat/rooms/{roomId}/read` 사용 |
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
| `/user/queue/nearby` | `NEARBY_MUSIC_UPDATED`, `POPULAR_TRACKS_UPDATED` | 임시 nearbyHandle·공개 음악 또는 익명 집계 |
| `/user/queue/chat` | `CHAT_ROOM_CREATED`, `CHAT_MESSAGE_CREATED`, `CHAT_MESSAGE_READ`, `CHAT_ROOM_UPDATED` | 대화방·메시지·읽음 상태 |
| `/user/queue/notifications` | `NOTIFICATION_CREATED` | 알림 ID·종류·비민감 표시 정보 |
| `/user/queue/reactions` | `NEARBY_REACTION_CREATED` | sender 별칭·reactionType·공개 곡·시각 |
| `/user/queue/ack` | `REQUEST_ACK` | `requestId`, `status`, 서버 생성 ID·시각 |
| `/user/queue/errors` | `ERROR` | `requestId?`, `code`, `message` |

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
| `/topic/sub-lounges/{subLoungeId}` | 참가 인원, 청취 상태, 추천 카드·리액션, 분위기 투표 |
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
