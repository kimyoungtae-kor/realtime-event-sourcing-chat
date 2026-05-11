# Troubleshooting Scenarios

## 1. Server Down

Detect:

- ALB health check failure
- 5xx rate 증가
- WebSocket disconnect spike

Mitigate:

- unhealthy instance를 load balancer에서 제외
- Auto Scaling 또는 ECS service desired count로 재기동

Recover:

- 클라이언트 reconnect
- 마지막으로 받은 `serverSequence` 이후 이벤트 replay
- event store 기준으로 누락 메시지 복구

## 2. DB Failure Or Slowdown

Detect:

- Hikari pool saturation
- slow query 증가
- lock wait timeout
- Flyway migration failure

Mitigate:

- API timeout과 backpressure 적용
- read query limit 강제
- connection pool size와 slow query index 점검

Recover:

- client retry를 idempotency key로 안전하게 수용
- failed outbox는 재시도
- projection은 event store에서 재생성

## 3. Data Consistency Issue

Detect:

- duplicate key violation count 증가
- sequence gap 검사
- projection lag 또는 replay 결과 불일치

Mitigate:

- event store를 source of truth로 유지
- projection write 실패는 본 이벤트 저장을 롤백하지 않고 재처리 큐로 보낸다.

Recover:

- 특정 session의 이벤트를 `server_sequence asc`로 replay
- snapshot/projection 삭제 후 재생성
- 문제가 된 clientEventId 기준으로 중복 요청 원인 분석
