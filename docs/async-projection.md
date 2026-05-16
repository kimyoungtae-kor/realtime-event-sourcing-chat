# Async Projection Design

현재 코드는 이벤트 저장과 current-state projection 갱신을 같은 트랜잭션에서 처리한다. 이 방식은 구현이 단순하고, 과제의 핵심인 중복 방지와 deterministic replay를 검증하기 좋다.

운영 규모가 커지면 projection과 snapshot 생성을 비동기 파이프라인으로 분리한다.

## 목표

- event append latency를 낮춘다.
- projection, snapshot 생성 실패가 이벤트 저장을 막지 않게 한다.
- worker 재시도와 중복 실행에도 같은 결과를 유지한다.

## 구조

```text
Spring API
  -> session_events append
  -> projection_outbox append
  -> commit

Projection Worker
  -> outbox polling or stream consume
  -> session_participants / read model update
  -> optional snapshot create
  -> processed mark
```

## Outbox

예상 컬럼:

```sql
create table projection_outbox (
    id bigint not null auto_increment,
    event_id bigint not null,
    session_id bigint not null,
    server_sequence bigint not null,
    status varchar(30) not null,
    retry_count int not null default 0,
    next_retry_at datetime(6) null,
    last_error_code varchar(100) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_projection_outbox_event (event_id),
    key idx_projection_outbox_status_retry (status, next_retry_at)
);
```

## Idempotency

- source event는 `session_events.id`다.
- projection worker는 `event_id` 단위로 처리 이력을 남긴다.
- 같은 event가 두 번 실행되어도 최종 projection은 동일해야 한다.
- snapshot은 `(session_id, server_sequence)` unique key로 중복 생성을 막는다.

## Retry / DLQ

- 일시 장애는 exponential backoff와 jitter로 재시도한다.
- 예: 1s, 2s, 4s, 8s, 30s, 1m, 5m
- 최대 재시도 횟수를 넘으면 DLQ로 이동한다.
- DLQ row에는 `event_id`, `session_id`, `server_sequence`, 실패 원인, retry count를 남긴다.

## Snapshot 자동화

자동 snapshot 기준:

- `server_sequence % 100 == 0`
- 또는 `SESSION_ENDED` 이벤트 수신
- 또는 replay latency가 임계치를 넘는 hot session

생성 방식:

1. 해당 session의 최신 snapshot을 조회한다.
2. 없으면 처음부터 replay한다.
3. 있으면 snapshot 이후 이벤트만 replay한다.
4. 새 `server_sequence`로 snapshot을 저장한다.

## 장애 처리

- worker 장애: outbox status가 pending으로 남고 다른 worker가 이어서 처리한다.
- DB 락 경합: 작은 batch size와 backoff로 완화한다.
- poison event: 반복 실패 후 DLQ로 격리하고 운영자가 event payload와 projection 코드를 확인한다.

현재 제출 구현에서는 outbox worker까지 만들지는 않았지만, snapshot API와 snapshot 기반 restore는 구현되어 있어 이 구조로 확장할 수 있다.
