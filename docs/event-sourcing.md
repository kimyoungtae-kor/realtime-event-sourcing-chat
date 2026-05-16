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

- 이벤트 저장 시 `sessions` row를 pessimistic lock으로 잠그고 `next_sequence`를 증가시킨다.
- replay, resume, timeline 조회는 모두 `server_sequence asc`로 처리한다.
- `client_sent_at`은 디버깅과 UX 보조 정보이며 정합성 기준으로 사용하지 않는다.

이 선택의 트레이드오프:

- 장점: 서버 기준으로 deterministic replay가 가능하고, 클라이언트 시계 오차에 영향을 받지 않는다.
- 단점: 사용자가 실제로 입력한 순서와 서버 수신 순서가 다를 수 있다.
- 보완: `client_sent_at`은 저장하되 canonical state에는 사용하지 않는다.

## Replay

기본 replay 절차:

1. 기준 시점 `at` 이하의 이벤트 조회
2. `server_sequence asc` 정렬
3. 빈 `TimelineState`에 이벤트를 순서대로 적용
4. participants, messages, session status를 반환

현재 구현 기준:

- 기준 시점 필터: `server_received_at <= at`
- 참여자 상태: `JOINED`, `LEFT`, `DISCONNECTED`, `RECONNECTED` 이벤트로 계산
- 메시지 목록: `MESSAGE_SENT` 이벤트의 `payload.content`를 반영
- 메시지 정렬: `server_sequence asc`
- 최근 N개 제한: `messageLimit` query parameter 사용

시간 기준:

- DB 저장과 replay 비교는 UTC 기준으로 처리한다.
- API 응답은 사람이 확인하기 쉽도록 `Asia/Seoul` offset으로 반환한다.
- 클라이언트는 `at` 값을 UTC `Z` 또는 명시적 offset이 포함된 ISO-8601 형식으로 전달한다.

## Snapshot + Replay

`POST /sessions/{sessionId}/snapshots`는 최신 이벤트 sequence 기준으로 상태를 저장한다.

저장 내용:

- session status
- participants current state
- messages
- snapshot 기준 `server_sequence`

복원 시에는 `session_snapshots`에서 기준 시점 이전 최신 snapshot을 찾고, snapshot 이후 이벤트만 replay한다.

```text
latest snapshot where snapshot_at <= at
  -> restore state_json
  -> replay events where server_sequence > snapshot.server_sequence and server_received_at <= at
```

같은 sequence의 snapshot은 `uk_snapshots_session_sequence`로 중복 생성을 막는다.

Snapshot은 최적화 캐시이며 source of truth가 아니다. snapshot JSON을 읽지 못하거나 schema가 맞지 않으면 snapshot을 버리고 `session_events` 전체 replay로 복구한다.

현재 자동 snapshot worker는 구현하지 않았다. 운영 확장안은 이벤트 100개마다 생성하거나 세션 종료 시 최종 snapshot을 생성하는 방식이다.
