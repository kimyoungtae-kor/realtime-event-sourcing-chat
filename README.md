# Realtime Event Sourcing Chat

1:1 실시간 채팅/통신 과제를 위한 Spring Boot 백엔드입니다.

현재 구현의 중심은 두 가지입니다.

- 채팅 세션에서 발생하는 join, leave, message, disconnect, reconnect, end 이벤트를 append-only 방식으로 저장한다.
- 저장된 이벤트를 `serverSequence` 순서로 replay해서 특정 시점의 대화 상태를 복원한다.

프론트엔드는 과제 범위에서 제외하고, REST API와 문서화된 검증 절차로 동작을 확인합니다.

## Tech Stack

- Java 21
- Spring Boot 4.0.6
- Gradle Wrapper
- MySQL 8.4
- Flyway
- Spring Web MVC, WebSocket, Validation, Data JPA, Actuator

로컬 검증에는 Docker Compose MySQL을 사용합니다. 운영 설계에서는 DB를 애플리케이션 컨테이너와 함께 띄우지 않고 AWS RDS 또는 GCP Cloud SQL 같은 managed database를 사용하는 방향으로 정리했습니다.

## Local Run

```powershell
copy .env.example .env
docker compose --env-file .env up -d mysql
.\gradlew.bat bootRun
```

기본 로컬 포트는 아래와 같습니다.

```text
Spring Boot: http://localhost:8080
Docker MySQL: localhost:3307 -> resc-mysql:3306
```

로컬 PC에 이미 MySQL이 `3306`으로 설치되어 있어도 충돌하지 않도록 Docker MySQL은 호스트 `3307` 포트로 노출합니다.

`bootRun`은 로컬 `.env` 파일을 읽어 DB URL, username, password를 프로세스 환경 변수로 주입합니다.

Health check:

```powershell
curl http://localhost:8080/actuator/health
```

## Implemented Scope

- Session, Participant, Event, Snapshot JPA Entity
- Flyway 기반 핵심 DDL
- 세션 생성/목록 조회 API
- join, leave, end 이벤트 API
- message, disconnect, reconnect 이벤트 수집 API
- 이벤트 조회 API
- `clientEventId` 기반 중복 이벤트 방지
- `serverSequence` 기반 일관된 이벤트 정렬
- 특정 시점 timeline replay API
- Snapshot 생성 API와 snapshot + delta replay 복원 최적화
- REST API 수동 검증 기록
- WebSocket STOMP message/presence event 수신 및 topic broadcast
- H2 기반 통합 테스트
- PowerShell 기반 로컬 부하 테스트 스크립트

## API Summary

- `POST /sessions`
- `GET /sessions`
- `POST /sessions/{sessionId}/join`
- `POST /sessions/{sessionId}/leave`
- `POST /sessions/{sessionId}/events`
- `GET /sessions/{sessionId}/events`
- `GET /sessions/{sessionId}/timeline?at=...`
- `POST /sessions/{sessionId}/snapshots`
- `POST /sessions/{sessionId}/end`

자세한 스펙은 [openapi/openapi.yaml](openapi/openapi.yaml)을 기준으로 합니다.

## Design Docs

- [Architecture](docs/architecture.md)
- [API Design](docs/api.md)
- [Event Sourcing](docs/event-sourcing.md)
- [ERD](docs/erd.md)
- [DDL](docs/ddl.sql)
- [Queries](docs/queries.md)
- [Operations](docs/operations.md)
- [Async Projection](docs/async-projection.md)
- [Load Test](docs/load-test.md)
- [Troubleshooting](docs/troubleshooting.md)
- [WebSocket](docs/websocket.md)
- [Manual Verification Checklist](docs/manual-test-checklist.md)

## Verification Status

2026-05-15 기준으로 REST 기반 MVP 흐름을 Postman과 DBeaver로 수동 검증했고, WebSocket은 테스트 페이지와 STOMP 프레임으로 확인했습니다.

확인한 내용:

- session 생성
- join 중복 요청 방지
- message 중복 요청 방지
- event query 정렬
- reconnect resume query
- timeline restore
- disconnect/reconnect 상태 반영
- completed session 이후 event append 거절
- WebSocket publish / subscribe
- snapshot 생성과 snapshot 기반 timeline restore

상세 결과는 [Manual Verification Checklist](docs/manual-test-checklist.md)에 남겼습니다.

자동화된 검증:

```powershell
.\gradlew.bat test
```

로컬 부하 테스트:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\load-test.ps1 -BaseUrl http://localhost:8080 -SessionCount 5 -MessagesPerSession 20
```

## Security

- `.env`, `.env.*`, key/pem/jks 파일은 커밋하지 않습니다.
- 공개 가능한 환경 변수 예시는 `.env.example`만 사용합니다.
- 로컬 기획 문서는 `planning-private/`에 두고 `.gitignore`로 제외합니다.
