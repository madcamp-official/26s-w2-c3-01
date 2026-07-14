# Melody Bubble Android MVP

Melody Bubble은 주변 사용자를 정확한 위치 대신 익명 음악 버블로 표현하고, 음악 취향·리액션·라운지·1:1 대화를 통해 연결하는 Android 앱입니다. 이 저장소의 MVP는 `Information architecture.png`, `wireframe.png`, `DB.png`, `기획서 초안.md`를 Android에서 시연 가능한 흐름으로 좁혀 구현합니다.

> 개인정보 기준: 다른 사용자에게 GPS 좌표, 정확한 거리, 실제 방향, 이동 경로를 노출하지 않습니다. 와이어프레임의 `4m`, `7m` 같은 숫자와 레이더상의 방향은 초기 시각 참고이며 제품 계약이 아닙니다.

## 현재 MVP

- 홈/근처 탐색, 사용자 상세, 라운지, 인박스·채팅, 마이 화면을 실제 REST/STOMP 서버와 연결합니다.
- 로컬 영속 데이터와 서버 소유 데이터를 분리하며, 좌표와 위치 이력은 Room에 저장하지 않습니다.
- 실제 연동용 값은 `API_BASE_URL`, `STOMP_WS_URL` 두 개뿐입니다. 저장소에는 실제 값이나 토큰을 커밋하지 않습니다.

구현 상태와 후속 범위는 [MVP 구현 기준](docs/MVP_GUIDE.md), 화면별 데이터 출처와 서버 계약은 [API·실시간 계약](docs/API_CONTRACT.md), DB·Room 차이는 [데이터 저장 및 개인정보 기준](docs/DATA_POLICY.md)에서 확인할 수 있습니다.

## 실행 환경

- Android Studio에서 프로젝트를 열어 실행하는 방식을 권장합니다.
- Gradle Wrapper: 9.4.1
- Gradle JVM: 21
- Android `minSdk 24`, `targetSdk 35`, `compileSdk 35`
- 세로형 Android 스마트폰 또는 에뮬레이터

## 빠른 실행

1. Android Studio에서 프로젝트를 열고 Gradle Sync를 실행해 로컬 SDK 경로가 든 `local.properties`를 생성합니다. 기존 파일을 `local.properties.example`로 덮어쓰지 않습니다.

2. 배포 기본값 대신 로컬 서버를 사용할 때만 `local.properties`에 다음 값을 추가합니다.

   ```properties
   API_BASE_URL=http://10.0.2.2:8080
   STOMP_WS_URL=ws://10.0.2.2:8080/ws
   ```

3. Android Studio에서 `app` 구성을 실행하거나 터미널에서 JDK 21을 선택해 빌드합니다. macOS에서 Android Studio 번들 JBR을 쓰는 예시는 다음과 같습니다.

   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew assembleDebug
   ```

4. 생성된 디버그 앱을 에뮬레이터 또는 기기에 설치해 실행합니다.

## 서버 연결 모드

| 구성 | 설정 | 현재 앱 동작 |
|---|---|---|
| 로컬 Android 에뮬레이터 | `http://10.0.2.2:8080`, `ws://10.0.2.2:8080/ws` | debug 빌드에서 로컬 Spring 서버와 통신 |
| 배포 서버 | `https://`, `wss://` URL | REST snapshot·명령과 STOMP 실시간 이벤트를 배포 서버와 교환 |

실제 연동 예시는 다음과 같습니다. 값은 배포 환경에서만 채우고 저장소에 커밋하지 않습니다.

```properties
API_BASE_URL=https://api.example.com/
STOMP_WS_URL=wss://api.example.com/ws
```

- `API_BASE_URL`: 인증, 초기 화면 조회, 설정 변경, 과거 기록 조회에 사용하는 HTTPS REST 기준 URL
- `STOMP_WS_URL`: 개인 Queue와 라운지·지역 Topic을 구독하는 WSS/STOMP 연결 URL
- 빌드 값 우선순위는 Gradle `-P` 속성 → 같은 이름의 환경 변수 → `local.properties`입니다. 어느 경로를 쓰더라도 필요한 키는 이 두 개뿐입니다.
- release 빌드는 HTTPS/WSS만 사용합니다. `http://10.0.2.2` 로컬 연결은 debug 빌드에서만 허용합니다.
- 저장소에는 운영 계정, JWT, DB 비밀번호, 음악 사업자 API 키를 커밋하지 않습니다.

## 권장 시연 시나리오

1. 앱을 실행하고 데모 안내를 확인합니다.
2. 홈에서 주변 공유 상태와 익명 버블, 주변 음악 요약을 확인합니다.
3. 근처 탭에서 유사도 `60%+`, `75%+`, `90%+` 필터를 바꾸고 익명 사용자를 선택합니다.
4. 사용자 상세에서 공개가 허용된 음악 정보만 보고 정해진 리액션 4종 중 하나 또는 팔로우를 실행합니다.
5. 라운지에 입장해 추천곡 카드와 집계 투표가 즉시 갱신되는 흐름을 확인합니다.
6. 인박스에서 알림을 확인하고, 맞팔로 가정된 대화방에서 메시지 전송 흐름을 확인합니다.
7. 팔로우 목록에서 상대의 공개 음악 프로필을 열고 공통 취향을 확인합니다.

시연 중 버블의 각도는 UI 배치를 위한 가상 값이며 상대의 방위나 실제 좌표로 해석하면 안 됩니다. 실제 서버 모드는 최대 15m 안의 사용자를 `5m 안쪽`, `10m 안쪽`, `15m 안쪽` 구간으로만 표현하고 정확한 미터값은 제공하지 않습니다.

## 검증

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug

# 실행 중인 에뮬레이터 또는 기기가 있을 때
./gradlew connectedDebugAndroidTest

```

위치·알림 접근처럼 실제 기기 기능이 필요한 항목은 권한을 승인한 테스트 기기에서 별도로 확인합니다. Android 13+에서 알림 권한을 거부하면 서비스는 실행돼도 알림 서랍의 지속 알림은 보장되지 않으므로 앱의 `중지`를 사용합니다. 사용자가 주변 공유를 직접 시작하기 전에는 위치 공유를 시작하지 않으며, 종료 후에는 좌표를 보관하지 않는 것이 기준입니다.

현재 자동 테스트는 인증, 공개 프로필·취향 지문·안정적인 profile handle·프로필 팔로우, 주변 탐색, 라운지, 채팅 흐름을 확인합니다. 알림 접근, 서비스 알림의 중지 액션, 프로세스 복귀는 실제 기기에서 추가 확인해야 합니다.

## 원본 설계 산출물

- [Information architecture](Information%20architecture.png)
- [Wireframe](wireframe.png)
- [DB diagram](DB.png)
- [기획서 초안](%EA%B8%B0%ED%9A%8D%EC%84%9C%20%EC%B4%88%EC%95%88.md)

원본 산출물은 전체 제품 방향을 담습니다. 현재 앱에 포함된 범위와 서버가 필요한 후속 범위는 동일하지 않으므로, 구현·시연 판단에는 `docs/`의 MVP 기준을 우선 적용합니다.
