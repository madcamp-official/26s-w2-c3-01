# Melody Bubble 서버 MVP

Android 앱과 분리된 Spring Boot Kotlin 서버입니다. REST 기준은 [`../docs/API_CONTRACT.md`](../docs/API_CONTRACT.md)이며, 주변 사용자에게는 원본 좌표·거리·방향을 절대 내려주지 않습니다.

## 실행

1. `cp .env.example .env` 후 `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `JWT_SECRET`(32바이트 이상)을 채웁니다.
2. `docker compose up -d`로 PostGIS와 RabbitMQ를 실행합니다.
3. JDK 21에서 `./gradlew bootRun` 또는 설치된 Gradle로 `gradle bootRun`을 실행합니다.

Docker가 현재 설치되어 있지 않은 환경이라면 Docker Desktop 설치 후 위 명령을 실행하면 됩니다. 앱 서버는 `http://localhost:8080`, RabbitMQ 관리 화면은 `http://localhost:15672`입니다.

Apple Silicon에서는 공식 PostGIS 이미지가 amd64만 제공하므로 Compose가 Docker Desktop emulation으로 실행합니다. 첫 실행은 이미지 다운로드와 에뮬레이션 초기화 때문에 다소 오래 걸릴 수 있습니다.

초기 개발 계정은 `demo@melody.local` / `demo1234`입니다. 시작 후 즉시 시드되며, 운영 배포에서는 이 시더를 비활성화하고 별도 가입 흐름으로 교체해야 합니다.

## 제공 범위

- `POST /api/v1/auth/login` JWT 발급
- 인증된 `GET /api/v1/nearby/snapshot`, `GET /api/v1/nearby/{handle}`
- `GET /api/v1/rooms`, `GET /api/v1/rooms/{roomId}`
- STOMP `/ws`: `location/update`, `room/vote`; 개인 `/user/queue/nearby|ack`, 라운지 `/topic/room/{roomId}/votes`
- Flyway 기반 PostGIS 스키마: 사용자, 개인정보 설정, 최신 위치 TTL, 음악 상태, 채팅, 라운지·투표

`current_locations`에는 세션당 최신 위치만 유지하며 90초 TTL 뒤 주변 조회에서 제외됩니다. `displayPosition`은 `nearbyHandle`에서 결정되는 추상 좌표로, 실제 위치와 방향을 표현하지 않습니다.
