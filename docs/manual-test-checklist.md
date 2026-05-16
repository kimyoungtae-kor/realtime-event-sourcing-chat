# Manual Verification Checklist

이 문서는 과제 요구사항을 기준으로 직접 확인한 항목과 남은 항목을 기록한다.

## 검증 범위

현재 검증 대상은 REST API 기반 MVP와 WebSocket publish / subscribe 흐름이다.

확인한 범위:

- 세션 생성과 조회
- 참여자 join / leave
- 메시지 이벤트 수집
- disconnect / reconnect presence 이벤트 수집
- `clientEventId` 기반 중복 이벤트 방지
- `serverSequence` 기준 이벤트 정렬
- `afterSequence` 기반 재연결 replay 조회
- 특정 시점 timeline 복원
- 완료된 세션에 대한 추가 이벤트 거절
- DB에 이벤트와 projection이 저장되는지 확인
- WebSocket publish / subscribe
- Snapshot 생성과 snapshot 기반 restore
- 통합 테스트

아직 이 문서에서 검증하지 않은 범위:

- 비동기 projection worker 구현
- 부하 테스트
- 장애 주입 테스트

## 시간 처리

- DB 저장과 replay 비교 기준은 UTC다.
- API 응답은 `Asia/Seoul` offset으로 반환한다.
- timeline의 `at` query parameter는 UTC `Z` 또는 명시적 offset이 있는 ISO-8601 값을 사용한다.

KST offset을 URL에 직접 넣을 때는 `+09:00`의 `+`를 `%2B`로 인코딩한다.

```http
GET /sessions/{sessionId}/timeline?at=2026-05-15T21:30:00%2B09:00&messageLimit=100
```

## DB 확인

DBeaver 연결값:

| 항목 | 값 |
| --- | --- |
| Host | `localhost` |
| Port | `3307` |
| Database | `resc_chat` |
| Username | `.env`의 `DB_USERNAME` |
| Password | `.env`의 `DB_PASSWORD` |

확인한 테이블:

- `sessions`
- `session_participants`
- `session_events`
- `session_snapshots`
- `flyway_schema_history`

DB에서 본 내용:

- Flyway가 schema를 생성했다.
- 세션 생성 시 `sessions` row가 생성됐다.
- 세션 생성 시 `SESSION_CREATED` 이벤트가 `session_events`에 저장됐다.
- join / disconnect / reconnect / leave 이벤트가 projection에 반영됐다.
- 중복 요청을 보내도 같은 `client_event_id`가 중복 저장되지 않았다.
- `server_sequence`가 세션 안에서 순서대로 증가했다.

## Postman 환경

| 변수 | 값 |
| --- | --- |
| `baseUrl` | `http://localhost:8080` |
| `sessionId` | `POST /sessions` 응답의 `sessionId` |

## 검증 요청 순서

### Health Check

```http
GET {{baseUrl}}/actuator/health
```

기대 결과:

- HTTP `200`
- `status = UP`

### 세션 생성

```http
POST {{baseUrl}}/sessions
Content-Type: application/json

{}
```

기대 결과:

- HTTP `201`
- `sessionId` 반환
- `status = ACTIVE`
- `SESSION_CREATED` 이벤트 저장

### Join

```http
POST {{baseUrl}}/sessions/{{sessionId}}/join
Content-Type: application/json

{
  "userId": "user-a",
  "displayName": "User A",
  "clientEventId": "join-user-a-001"
}
```

기대 결과:

- HTTP `201`
- `type = JOINED`
- `duplicate = false`
- participant state는 `ONLINE`

### Duplicate Join

같은 Join 요청을 한 번 더 보낸다.

기대 결과:

- HTTP `200`
- `duplicate = true`
- 같은 `client_event_id`가 추가 저장되지 않음

### Message

```http
POST {{baseUrl}}/sessions/{{sessionId}}/events
Content-Type: application/json

{
  "type": "MESSAGE_SENT",
  "senderId": "user-a",
  "clientEventId": "message-user-a-001",
  "payload": {
    "messageId": "msg-001",
    "content": "hello from postman"
  }
}
```

기대 결과:

- HTTP `201`
- `type = MESSAGE_SENT`
- `duplicate = false`
- `serverSequence` 증가

### Duplicate Message

같은 Message 요청을 한 번 더 보낸다.

기대 결과:

- HTTP `200`
- `duplicate = true`
- timeline에 같은 메시지가 두 번 반영되지 않음

### Event Query

```http
GET {{baseUrl}}/sessions/{{sessionId}}/events?limit=100
```

기대 결과:

- HTTP `200`
- `serverSequence` 오름차순
- `SESSION_CREATED`, `JOINED`, `MESSAGE_SENT` 포함

### Reconnect Resume Query

```http
GET {{baseUrl}}/sessions/{{sessionId}}/events?afterSequence=2&limit=100
```

기대 결과:

- HTTP `200`
- `serverSequence > 2`인 이벤트만 반환

### Timeline Restore

```http
GET {{baseUrl}}/sessions/{{sessionId}}/timeline?at=2026-12-31T23:59:59Z&messageLimit=100
```

기대 결과:

- HTTP `200`
- 참여자 목록 복원
- 메시지 목록 복원
- duplicate message는 중복 반영되지 않음
- `restoredFromSnapshot = false`

### Snapshot 생성

```http
POST {{baseUrl}}/sessions/{{sessionId}}/snapshots
```

기대 결과:

- HTTP `201`
- `serverSequence`가 최신 이벤트 sequence와 일치
- 같은 sequence에서 다시 호출하면 HTTP `200`
- `reused = true`

Snapshot 생성 이후 같은 세션에 새 메시지를 추가하고 timeline을 조회하면:

- HTTP `200`
- `restoredFromSnapshot = true`
- snapshot 이후 이벤트만 replay됨

### Disconnect

```http
POST {{baseUrl}}/sessions/{{sessionId}}/events
Content-Type: application/json

{
  "type": "DISCONNECTED",
  "senderId": "user-a",
  "clientEventId": "disconnect-user-a-001",
  "payload": {}
}
```

기대 결과:

- HTTP `201`
- `type = DISCONNECTED`
- replay 시 offline 상태가 반영됨

### Reconnect

```http
POST {{baseUrl}}/sessions/{{sessionId}}/events
Content-Type: application/json

{
  "type": "RECONNECTED",
  "senderId": "user-a",
  "clientEventId": "reconnect-user-a-001",
  "payload": {}
}
```

기대 결과:

- HTTP `201`
- `type = RECONNECTED`
- replay 시 online 상태가 반영됨

### Leave

```http
POST {{baseUrl}}/sessions/{{sessionId}}/leave
Content-Type: application/json

{
  "userId": "user-a",
  "clientEventId": "leave-user-a-001"
}
```

기대 결과:

- HTTP `201`
- `type = LEFT`
- replay 시 left 상태가 반영됨

### End Session

```http
POST {{baseUrl}}/sessions/{{sessionId}}/end
Content-Type: application/json

{
  "endedBy": "user-a",
  "clientEventId": "end-session-001"
}
```

기대 결과:

- HTTP `201`
- `type = SESSION_ENDED`
- replay 시 session status는 `COMPLETED`

### Completed Session Reject

```http
POST {{baseUrl}}/sessions/{{sessionId}}/events
Content-Type: application/json

{
  "type": "MESSAGE_SENT",
  "senderId": "user-a",
  "clientEventId": "message-after-end-001",
  "payload": {
    "messageId": "msg-after-end",
    "content": "should fail"
  }
}
```

기대 결과:

- HTTP `409`
- error code는 `SESSION_COMPLETED`
- 새 이벤트가 저장되지 않음

## WebSocket 수동 검증

테스트 페이지:

```http
GET {{baseUrl}}/ws-test.html
```

확인할 내용:

- `Connect` 후 STOMP `CONNECTED` 프레임이 수신되는지
- `/topic/sessions/{sessionId}` 구독 후 메시지 이벤트가 실시간으로 들어오는지
- 같은 `clientEventId`를 다시 보내면 `duplicate = true`로 수신되는지
- `DISCONNECTED`, `RECONNECTED` 이벤트도 같은 topic으로 수신되는지
- WebSocket으로 보낸 이벤트가 REST `GET /sessions/{sessionId}/events` 조회에도 보이는지

검증 순서:

1. `세션 생성`
2. `Join`
3. `Connect`
4. `SUBSCRIBED` 상태 확인
5. `Send Message`
6. `Duplicate`
7. `DISCONNECTED`
8. `RECONNECTED`
9. REST event query 또는 timeline으로 저장 결과 확인

## 2026-05-15 검증 기록

1차 검증 세션:

```text
5e8f938f-d0c1-4463-9949-090aae4e5b8b
```

2차 검증 세션:

```text
74253500-4822-4900-9521-bd5ec5750e75
```

PowerShell STOMP 검증 세션:

```text
9d38fd3b-d213-41f4-a238-51a958d03f7a
```

브라우저 테스트 페이지 검증 세션:

```text
8390020a-f313-4404-a71c-48877481cf93
```

| 항목 | 결과 | 메모 |
| --- | --- | --- |
| Health check | PASS | `status = UP` |
| 세션 생성 | PASS | `sessionId` 반환, `status = ACTIVE`, `nextSequence = 2` |
| Join | PASS | `JOINED`, `duplicate = false` |
| Duplicate Join | PASS | 같은 `clientEventId` 재전송 시 `duplicate = true` |
| Message | PASS | `MESSAGE_SENT`, 한글 payload 저장 확인 |
| Duplicate Message | PASS | 같은 `clientEventId` 재전송 시 `duplicate = true` |
| Event Query | PASS | `serverSequence` 순서로 조회됨 |
| Resume Query | PASS | `afterSequence=2` 요청 시 이후 이벤트만 조회됨 |
| Timeline Restore | PASS | 참여자, 메시지, `appliedEventCount`, `restoredFromSnapshot = false` 확인 |
| Disconnect | PASS | 2차 세션에서 disconnect/reconnect 흐름 확인 |
| Reconnect | PASS | 2차 세션 timeline에서 participant `ONLINE`, session `ACTIVE` 확인 |
| Leave | PARTIAL | 1차 세션에서 replay 결과 `LEFT` 확인. Postman 전용 재검증은 optional |
| End Session | PASS | 2차 세션에서 `SESSION_ENDED` 확인 |
| Completed Reject | PASS | 종료 후 message append 시 HTTP `409`, code `SESSION_COMPLETED` 확인 |
| WebSocket Test Page | READY | `GET /ws-test.html`로 브라우저 수동 검증 가능 |
| WebSocket Publish / Subscribe | PASS | `ClientWebSocket`과 브라우저 테스트 페이지에서 STOMP 연결, 구독, message/presence 송수신 확인 |
| Snapshot API | PASS | 통합 테스트에서 snapshot 생성, 중복 생성 방지, snapshot 이후 delta replay 확인 |
| Integration Test | PASS | `.\gradlew.bat test` 성공 |

메모:

- 최초 timeline 실패는 timezone 문제가 아니라 잘못된 `at` 문자열 때문이었다.
- 잘못된 날짜 파라미터는 `400 INVALID_REQUEST_PARAMETER`로 응답하도록 예외 처리를 보강했다.
- 2차 세션에서 API 응답 시간이 `+09:00` offset으로 반환되는 것을 확인했다.
- REST 기반 MVP의 핵심 흐름은 수동 검증을 완료했다.
- WebSocket 검증 세션 `9d38fd3b-d213-41f4-a238-51a958d03f7a`에서 `CONNECTED`, `MESSAGE_SENT`, duplicate message, `DISCONNECTED`, `RECONNECTED`를 확인했다.
- WebSocket 이벤트도 REST 이벤트 조회 기준 총 5건으로 저장되는 것을 확인했다.
- 브라우저 테스트 페이지 검증 세션 `8390020a-f313-4404-a71c-48877481cf93`에서 `CONNECTED`, `SUBSCRIBE`, 한글 메시지 payload, duplicate message, presence event 수신을 확인했다.
- 같은 `clientEventId`인 `ws-message-001` 재전송 시 `eventId = 13`, `serverSequence = 3`이 유지되고 `duplicate = true`로 내려오는 것을 확인했다.
- `DISCONNECTED` 이후 세션 상태가 `INTERRUPTED`로 바뀌는 것을 확인했다.
- 종료된 이전 세션 `74253500-4822-4900-9521-bd5ec5750e75`에 join을 시도했을 때 `SESSION_COMPLETED`가 반환되는 것도 확인했다. 이는 완료 세션에 이벤트를 받지 않는 기대 동작이다.
- 2026-05-16 통합 테스트에서 duplicate event, completed reject, snapshot reuse, snapshot 기반 timeline restore를 확인했다.

## 남은 항목

- 비동기 projection worker 구현
- 부하 테스트
- 장애 주입 테스트
