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

확장 설계:

- `session_events` insert
- outbox insert
- worker가 outbox를 읽어 projection/snapshot 갱신
- 실패 시 exponential backoff
- 재시도 한도 초과 시 DLQ로 이동
- worker도 `event_id` 기준 idempotent하게 처리

## AWS Direction

- ALB: HTTP/WebSocket ingress, health check
- ECS/Fargate: Spring Boot container
- RDS MySQL: event store
- ElastiCache Redis: presence cache, cross-node broadcast
- CloudWatch: logs, metrics, alarms
- X-Ray 또는 OpenTelemetry: distributed tracing
