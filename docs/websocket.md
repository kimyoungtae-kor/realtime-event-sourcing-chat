# WebSocket Design

현재 WebSocket은 Spring WebSocket + STOMP로 구성한다.

## Endpoint

```text
ws://localhost:8080/ws
```

STOMP destination:

| Direction | Destination | 설명 |
| --- | --- | --- |
| subscribe | `/topic/sessions/{sessionId}` | 세션에 append된 realtime event 수신 |
| subscribe | `/topic/sessions/{sessionId}/errors` | WebSocket 처리 중 발생한 에러 수신 |
| send | `/app/sessions/{sessionId}/messages` | `MESSAGE_SENT` 이벤트 전송 |
| send | `/app/sessions/{sessionId}/events` | `DISCONNECTED`, `RECONNECTED` 이벤트 전송 |

## 처리 흐름

WebSocket으로 들어온 메시지도 REST API와 같은 `SessionEventService`를 사용한다.

```text
STOMP send
  -> RealtimeMessageController
  -> SessionEventService.appendEvent()
  -> session_events append
  -> current projection update
  -> /topic/sessions/{sessionId} broadcast
```

따라서 WebSocket과 REST API는 같은 idempotency, ordering, replay 기준을 공유한다.

## Message 예시

Send:

```text
/app/sessions/{sessionId}/messages
```

Payload:

```json
{
  "type": "MESSAGE_SENT",
  "senderId": "user-a",
  "clientEventId": "ws-message-user-a-001",
  "payload": {
    "messageId": "ws-msg-001",
    "content": "hello over websocket"
  }
}
```

Subscribe:

```text
/topic/sessions/{sessionId}
```

수신 payload는 REST `EventResponse`와 같은 형태다.

## Presence 예시

Send:

```text
/app/sessions/{sessionId}/events
```

Payload:

```json
{
  "type": "DISCONNECTED",
  "senderId": "user-a",
  "clientEventId": "ws-disconnect-user-a-001",
  "payload": {}
}
```

`RECONNECTED`도 같은 destination을 사용한다.

## Error Topic

WebSocket 처리 중 session이 없거나, 완료된 세션에 이벤트를 보내거나, 지원하지 않는 event type을 보내면 아래 topic으로 에러가 전달된다.

```text
/topic/sessions/{sessionId}/errors
```

Payload:

```json
{
  "code": "SESSION_COMPLETED",
  "message": "Completed session cannot accept new events",
  "timestamp": "2026-05-15T21:47:09+09:00"
}
```

## 검증 상태

구현은 완료했고, `ClientWebSocket` 기반 STOMP 프레임 전송과 브라우저 테스트 페이지로 서버 동작을 확인했다.

2026-05-15 PowerShell STOMP 검증 세션:

```text
9d38fd3b-d213-41f4-a238-51a958d03f7a
```

PowerShell 확인 결과:

- STOMP `CONNECTED` 프레임 수신
- `/topic/sessions/{sessionId}` 구독 후 `MESSAGE_SENT` 이벤트 수신
- 같은 `clientEventId` 재전송 시 `duplicate = true`
- `DISCONNECTED`, `RECONNECTED` 이벤트 수신
- REST `GET /sessions/{sessionId}/events` 기준 persisted event count `5`

2026-05-15 브라우저 테스트 페이지 검증 세션:

```text
8390020a-f313-4404-a71c-48877481cf93
```

브라우저 확인 결과:

- 테스트 페이지에서 `POST /sessions`, `join`, `CONNECT`, `SUBSCRIBE` 흐름 확인
- 한글 메시지 `안녕`을 `MESSAGE_SENT`로 전송하고 topic 수신 확인
- `ws-message-001` 재전송 시 같은 `eventId`, 같은 `serverSequence`와 함께 `duplicate = true` 수신
- `RECONNECTED`, `DISCONNECTED` presence 이벤트 수신
- `DISCONNECTED` 이후 projection 기준 세션 상태가 `INTERRUPTED`로 변경되는 것 확인

브라우저에서 직접 확인할 때는 `src/main/resources/static/ws-test.html`을 사용한다.

앱 실행 후 아래 주소를 연다.

```text
http://localhost:8080/ws-test.html
```

검증 순서:

1. `세션 생성`을 눌러 `POST /sessions`를 호출한다.
2. `Join`을 눌러 참여자를 만든다.
3. `Connect`를 눌러 `ws://localhost:8080/ws`에 STOMP 연결한다.
4. `SUBSCRIBED` 상태를 확인한다.
5. `Send Message`를 눌러 `/app/sessions/{sessionId}/messages`로 메시지를 보낸다.
6. Event Log에 `/topic/sessions/{sessionId}` 수신 이벤트가 찍히는지 확인한다.
7. `Duplicate`를 눌러 같은 `clientEventId`를 재전송하고 `duplicate = true`를 확인한다.
8. `DISCONNECTED`, `RECONNECTED`를 보내고 Event Log에서 presence 이벤트를 확인한다.
9. 필요하면 REST `GET /sessions/{sessionId}/events`와 timeline API로 같은 이벤트가 저장됐는지 확인한다.
