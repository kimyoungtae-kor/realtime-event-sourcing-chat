## Tech Stack

- Java 21
- Spring Boot 4.0.6
- Gradle Wrapper
- MySQL 8.4
- Flyway
- Spring Web MVC, WebSocket, Validation, Data JPA, Actuator
- AWS 운영 설계 문서화 예정: ALB, ECS, RDS MySQL, ElastiCache Redis, CloudWatch

## Local Run
```powershell
copy .env.example .env
docker compose --env-file .env up -d mysql
.\gradlew.bat bootRun
```

Health check:

```powershell
curl http://localhost:8080/actuator/health
```

## Current Scope

현재는 프로젝트 골격 단계입니다.

- Spring Boot/Gradle 프로젝트 생성
- MySQL docker compose 구성
- Flyway V1 DDL 초안
- WebSocket endpoint 골격: `/ws`
- 공개 제출용 문서 구조 생성
- 민감정보와 로컬 기획 문서 ignore 처리

## Planned API

- `POST /sessions`
- `POST /sessions/{sessionId}/join`
- `POST /sessions/{sessionId}/leave`
- `POST /sessions/{sessionId}/events`
- `POST /sessions/{sessionId}/end`
- `GET /sessions`
- `GET /sessions/{sessionId}/events`
- `GET /sessions/{sessionId}/timeline?at=...`
- `POST /sessions/{sessionId}/snapshots`

자세한 초안은 [openapi/openapi.yaml](openapi/openapi.yaml)을 참고합니다.

## Design Docs

- [Architecture](docs/architecture.md)
- [Event Sourcing](docs/event-sourcing.md)
- [ERD](docs/erd.md)
- [DDL](docs/ddl.sql)
- [Queries](docs/queries.md)
- [Operations](docs/operations.md)
- [Troubleshooting](docs/troubleshooting.md)

## Security

- `.env`, `.env.*`, key/pem/jks 파일은 커밋하지 않습니다.
- 공개 가능한 예시는 `.env.example`만 사용합니다.
- 로컬 기획 문서는 `planning-private/`에 두며 `.gitignore`로 제외합니다.
