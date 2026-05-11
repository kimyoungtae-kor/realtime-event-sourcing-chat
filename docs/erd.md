# ERD

```mermaid
erDiagram
    sessions ||--o{ session_participants : has
    sessions ||--o{ session_events : records
    sessions ||--o{ session_snapshots : snapshots

    sessions {
        bigint id PK
        char public_id UK
        varchar status
        bigint next_sequence
        datetime created_at
        datetime updated_at
        datetime ended_at
    }

    session_participants {
        bigint id PK
        bigint session_id FK
        varchar user_id
        varchar display_name
        varchar state
        datetime joined_at
        datetime left_at
        datetime last_seen_at
    }

    session_events {
        bigint id PK
        bigint session_id FK
        bigint server_sequence
        varchar type
        varchar sender_id
        varchar client_event_id
        datetime client_sent_at
        datetime server_received_at
        json payload_json
    }

    session_snapshots {
        bigint id PK
        bigint session_id FK
        bigint server_sequence
        datetime snapshot_at
        json state_json
        datetime created_at
    }
```

## Design Notes

- `public_id`는 API 외부 노출용 UUID다.
- `session_events.payload_json`은 이벤트 타입별 payload 확장을 위해 JSON으로 둔다.
- 중복 방지는 `(session_id, sender_id, client_event_id)` unique key로 처리한다.
- replay 정렬은 `(session_id, server_sequence)`를 기준으로 한다.
