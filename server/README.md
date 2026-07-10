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

## VM 배포

VM에서는 `.env.production.example`을 `.env`로 복사해 빈 secret을 채운 뒤 실행합니다.

```bash
docker compose --env-file .env -f compose.production.yaml up -d --build
curl http://127.0.0.1:8080/actuator/health
```

PostgreSQL과 애플리케이션 포트는 VM의 loopback에만 바인딩됩니다. 외부 API 접속은 Cloudflare Tunnel, DB 개발자 접속은 SSH tunnel을 통해서만 허용합니다. RabbitMQ는 Docker 내부 네트워크에만 존재합니다.

운영 대시보드는 `/internal/ops`에서 확인합니다. HTTP Basic 인증을 사용하며 `.env`의 `OPS_USERNAME`, `OPS_PASSWORD`로만 접근할 수 있습니다. 사용자 비밀번호, JWT, 위치 좌표, 메시지 본문은 표시하지 않습니다.

## DBeaver로 운영 DB 구조 확인

먼저 VPN을 연결한 Mac에서 SSH tunnel을 유지합니다.

```bash
./scripts/open-db-tunnel.sh
```

DBeaver의 PostgreSQL 연결값은 다음과 같습니다.

```text
Host: localhost
Port: 15432
Database: melody_bubble
Username: melody
Password: VM의 server/.env에 있는 POSTGRES_PASSWORD
```

비밀번호는 저장소나 채팅에 복사하지 않습니다. `15432`는 Mac의 loopback 포트이고 VM에서는 PostgreSQL이 `127.0.0.1:5432`에만 열리므로 인터넷에서 DB로 직접 접근할 수 없습니다.

DBeaver 연결 후 `public` schema에서 테이블·컬럼·인덱스·외래 키를 확인하거나 ER Diagram을 열 수 있습니다. Flyway 적용 상태는 아래 쿼리로 확인합니다.

```sql
SELECT installed_rank, version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Android Room 확인

앱을 debug로 실행한 뒤 Android Studio의 `View → Tool Windows → App Inspection → Database Inspector`에서 `offline_exchange_local`, `sync_outbox`를 확인합니다. 이 데이터는 서버 PostgreSQL과 별개입니다.
