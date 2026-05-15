# Operations Design

## Observability

로그 필드:

- `traceId`
- `sessionId`
- `serverSequence`
- `clientEventId`
- `eventType`
- `senderId`

메트릭:

- event append latency
- duplicate event count
- WebSocket active sessions
- reconnect count
- timeline replay latency
- DB connection pool usage
- projection lag

## Async Processing

MVP에서는 event write와 최소 상태 갱신을 같은 트랜잭션에서 처리한다.

현재 구현:

- `session_events` append가 source of truth다.
- `session_participants`는 join/leave/disconnect/reconnect 시 같은 트랜잭션에서 갱신하는 current-state projection이다.
- timeline restore는 projection이 아니라 event replay를 사용한다.

확장 설계:

- `session_events` insert
- outbox insert
- worker가 outbox를 읽어 projection/snapshot 갱신
- 실패 시 exponential backoff
- 재시도 한도 초과 시 DLQ로 이동
- worker도 `event_id` 기준 idempotent하게 처리

## Reconnect Consistency

클라이언트는 마지막으로 처리한 `serverSequence`를 로컬에 저장한다.

재연결 시:

1. WebSocket 재연결
2. `GET /sessions/{id}/events?afterSequence=...` 호출
3. 서버가 누락 이벤트를 `serverSequence asc`로 반환
4. 클라이언트가 누락 이벤트를 적용한 뒤 다시 실시간 구독을 이어간다.

중복 이벤트는 `clientEventId` unique key로 저장 단계에서 방지한다.

## AWS Direction

AWS로 운영한다면 아래 구성을 기본안으로 둔다.

- ALB: HTTP/WebSocket ingress, health check
- ECS/Fargate 또는 EKS: Spring Boot container
- Amazon RDS for MySQL: event store
- ElastiCache Redis: presence cache, cross-node broadcast
- CloudWatch: logs, metrics, alarms
- X-Ray 또는 OpenTelemetry: distributed tracing

로컬의 `docker-compose.yml` MySQL은 개발/검증 편의를 위한 것이다. 운영 DB를 직접 EC2 또는 애플리케이션 컨테이너 옆에서 self-managed MySQL로 운영할 수도 있지만, 본 과제의 운영 설계에서는 backup, patching, monitoring, Multi-AZ 구성을 쉽게 가져갈 수 있는 RDS를 우선 선택한다.

## GCP/GKE Direction

공고 기술스택의 Kubernetes(GKE)를 기준으로 맞춘다면 아래 구성이 자연스럽다.

- GKE: Spring Boot container
- Cloud SQL for MySQL 또는 PostgreSQL: event store
- Memorystore for Redis: presence cache, cross-node broadcast
- Cloud Logging/Monitoring/Trace: observability
