# Event Sourcing Design

## Source Of Truth

`session_events`가 세션 상태의 source of truth다. `session_participants`와 snapshot/projection은 조회 최적화 또는 현재 상태 캐시로 취급한다.

## Idempotency

클라이언트는 모든 이벤트 요청에 `clientEventId`를 포함한다.

서버는 아래 unique key로 중복 저장을 방지한다.

```sql
unique key uk_events_idempotency (session_id, sender_id, client_event_id)
```

동일 요청이 재전송되면 새 이벤트를 만들지 않고 기존 이벤트를 반환한다.

## Ordering

canonical order는 `server_sequence`다.

- 이벤트 저장 시 세션 row를 잠그고 `next_sequence`를 증가시킨다.
- replay, resume, timeline 조회는 모두 `server_sequence asc`로 처리한다.
- `client_sent_at`은 디버깅과 UX 보조 정보이며 정합성 기준으로 사용하지 않는다.

## Replay

MVP replay 절차:

1. 기준 시점 `at` 이하의 이벤트 조회
2. `server_sequence asc` 정렬
3. 빈 `TimelineState`에 이벤트를 순서대로 적용
4. participants, messages, session status를 반환

## Snapshot Extension

최적화 단계에서는 `session_snapshots`에서 기준 시점 이전 최신 snapshot을 찾고, snapshot 이후 이벤트만 replay한다.

Snapshot 주기 초안:

- 이벤트 100개마다 자동 생성
- 또는 세션 종료 시 최종 snapshot 생성
