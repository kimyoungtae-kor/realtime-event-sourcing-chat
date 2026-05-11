# Query And Index Plan

## 1. Timeline Restore

```sql
select *
from session_events
where session_id = ?
  and server_received_at <= ?
order by server_sequence asc;
```

Indexes:

```sql
key idx_events_session_received_sequence (session_id, server_received_at, server_sequence)
unique key uk_events_session_sequence (session_id, server_sequence)
```

Potential bottleneck:

- 오래된 세션에서 이벤트가 매우 많으면 full replay 비용이 커진다.
- 최신 snapshot을 먼저 찾고 snapshot 이후 이벤트만 replay하도록 개선한다.

## 2. Reconnect Resume

```sql
select *
from session_events
where session_id = ?
  and server_sequence > ?
order by server_sequence asc
limit ?;
```

Index:

```sql
unique key uk_events_session_sequence (session_id, server_sequence)
```

Potential bottleneck:

- 클라이언트가 너무 오래 끊겨 있으면 반환 이벤트가 많아진다.
- 서버는 page size를 제한하고, 필요하면 snapshot 기반 timeline API로 유도한다.

## 3. Duplicate Detection

```sql
select *
from session_events
where session_id = ?
  and sender_id = ?
  and client_event_id = ?;
```

Index:

```sql
unique key uk_events_idempotency (session_id, sender_id, client_event_id)
```

Potential bottleneck:

- hot session에서 insert 경합이 발생할 수 있다.
- session row lock으로 sequence 정합성을 확보하되, 추후 Redis/Kafka partition key를 `session_id`로 두어 순서 보장을 확장한다.
