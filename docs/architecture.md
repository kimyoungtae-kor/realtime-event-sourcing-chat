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

## Scaling Direction

MVP는 단일 Spring Boot 인스턴스와 MySQL event store로 구현한다.

수평 확장 시:

- WebSocket 연결은 ALB 뒤 여러 인스턴스에 분산
- event store는 RDS MySQL을 source of truth로 유지
- 인스턴스 간 broadcast는 Redis Pub/Sub 또는 Kafka topic으로 확장
- presence는 Redis TTL key로 캐싱하고, 최종 정합성은 event store로 복구
