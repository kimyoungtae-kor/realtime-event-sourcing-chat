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

## Normalization And JSON Trade-Off

정규화한 데이터:

- `sessions`: 세션 상태와 sequence allocator 역할
- `session_participants`: 현재 참여자/presence projection
- `session_events`: append-only event store
- `session_snapshots`: replay 최적화용 snapshot 저장소

JSON으로 둔 데이터:

- `session_events.payload_json`
- `session_snapshots.state_json`

선택 근거:

- 이벤트 타입별 payload 구조가 달라질 수 있으므로 event payload는 JSON으로 두어 확장성을 확보한다.
- message content, displayName, endedBy 같은 이벤트별 속성은 payload에 둔다.
- hot path 필터링 대상인 `session_id`, `server_sequence`, `sender_id`, `client_event_id`, `server_received_at`은 별도 column으로 정규화해 인덱스를 건다.

트레이드오프:

- JSON payload는 스키마 변경에 강하지만, payload 내부 필드 조건 검색에는 약하다.
- 핵심 조회 조건은 JSON 내부에 넣지 않고 column으로 승격한다.
- projection table인 `session_participants`는 비정규화된 현재 상태 캐시지만, source of truth는 `session_events`다.
