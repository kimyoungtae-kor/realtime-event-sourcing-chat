# Architecture

## Goal

1:1 채팅 세션에서 발생하는 모든 상태 변화를 이벤트로 저장하고, REST/WebSocket 양쪽에서 같은 event store를 사용한다.

## Request Flow

```text
Client
  -> REST/WebSocket
  -> Spring Boot API
  -> SessionEventService
  -> MySQL event store
  -> Timeline replay / WebSocket broadcast
```

## Components

- `session`: 세션 생성, 종료, 상태 변경
- `participant`: join, leave, disconnect, reconnect에 따른 참여 상태
- `event`: append-only 이벤트 저장, idempotency, server sequence 할당
- `timeline`: 특정 시점 기준 replay와 snapshot 복원
- `realtime`: STOMP over WebSocket 설정과 실시간 broadcast

## Implemented Backend Flow

현재 구현은 REST API 기준으로 먼저 동작한다.

```text
POST /sessions
  -> sessions insert
  -> SESSION_CREATED event append

POST /sessions/{id}/join
  -> session row pessimistic lock
  -> duplicate check by clientEventId
  -> serverSequence allocation
  -> JOINED event append
  -> session_participants current state update

POST /sessions/{id}/events
  -> MESSAGE_SENT / DISCONNECTED / RECONNECTED event append
  -> participant/session current state update

GET /sessions/{id}/timeline?at=...
  -> events where server_received_at <= at
  -> order by server_sequence asc
  -> in-memory replay
```

WebSocket endpoint `/ws`는 골격 설정이 되어 있으며, 다음 단계에서 REST event append service와 연결한다.

## Scaling Direction

MVP는 단일 Spring Boot 인스턴스와 MySQL event store로 구현한다.

수평 확장 시:

- WebSocket 연결은 ALB 뒤 여러 인스턴스에 분산
- event store는 managed RDB를 source of truth로 유지
  - AWS 기준: Amazon RDS for MySQL
  - GCP/GKE 기준: Cloud SQL for MySQL 또는 PostgreSQL
- 인스턴스 간 broadcast는 Redis Pub/Sub 또는 Kafka topic으로 확장
- presence는 Redis TTL key로 캐싱하고, 최종 정합성은 event store로 복구

`docker-compose.yml`의 MySQL은 로컬 개발과 평가 재현을 위한 선택이다. 클라우드 운영에서는 DB를 애플리케이션 컨테이너와 같은 compose/kubernetes workload로 직접 띄우기보다 managed DB를 사용해 backup, patching, failover, monitoring 부담을 줄이는 쪽을 기본안으로 둔다.
