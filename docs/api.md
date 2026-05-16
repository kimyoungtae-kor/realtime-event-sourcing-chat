# REST API Design

이 문서는 현재 구현한 REST API의 의도와 검증 기준을 정리한다.

## 기본 원칙

- 외부 API에는 내부 DB PK를 노출하지 않고 `sessions.public_id`를 `sessionId`로 사용한다.
- 세션 상태를 바꾸는 요청은 모두 `session_events`에 이벤트를 append한다.
- 클라이언트 재시도는 `clientEventId`로 멱등 처리한다.
- 새 이벤트 저장은 `201`, 중복 재전송은 기존 이벤트를 반환하면서 `200`으로 응답한다.
- DB 저장과 replay 비교는 UTC 기준으로 처리한다.
- API 응답 시간은 사람이 확인하기 쉽도록 `Asia/Seoul` offset으로 반환한다.

## 구현된 Endpoint

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/sessions` | 세션 생성 |
| `GET` | `/sessions` | 상태/참여자/기간 기준 세션 목록 조회 |
| `POST` | `/sessions/{sessionId}/join` | 참여자 입장 이벤트 수집 |
| `POST` | `/sessions/{sessionId}/leave` | 참여자 퇴장 이벤트 수집 |
| `POST` | `/sessions/{sessionId}/events` | 메시지와 접속 상태 이벤트 수집 |
| `GET` | `/sessions/{sessionId}/events` | 재연결 또는 디버깅용 이벤트 조회 |
| `GET` | `/sessions/{sessionId}/timeline` | 특정 시점 상태 복원 |
| `POST` | `/sessions/{sessionId}/snapshots` | 현재 이벤트 기준 snapshot 생성 |
| `POST` | `/sessions/{sessionId}/end` | 세션 종료 이벤트 수집 |

## 이벤트 수집 규칙

`POST /sessions/{sessionId}/events`는 아래 이벤트만 직접 받는다.

- `MESSAGE_SENT`
- `DISCONNECTED`
- `RECONNECTED`

도메인 의미가 더 분명한 이벤트는 별도 endpoint로 분리했다.

- `JOINED`: `POST /sessions/{sessionId}/join`
- `LEFT`: `POST /sessions/{sessionId}/leave`
- `SESSION_ENDED`: `POST /sessions/{sessionId}/end`
- `SESSION_CREATED`: `POST /sessions` 처리 중 서버가 생성

## 재연결 Resume

클라이언트는 마지막으로 처리한 `serverSequence`를 가지고 있다가 재연결 후 누락 이벤트를 요청한다.

```http
GET /sessions/{sessionId}/events?afterSequence={lastSeenServerSequence}&limit=100
```

서버는 `serverSequence` 오름차순으로 이벤트를 반환한다. 이 응답을 적용한 뒤 WebSocket 구독을 재개하면 누락 메시지를 복구할 수 있다.

## 상태 복원

Timeline API는 기준 시점 `at`까지 저장된 이벤트를 replay한다.

```http
GET /sessions/{sessionId}/timeline?at=2026-12-31T23:59:59Z&messageLimit=100
```

현재 구현은 snapshot이 있으면 기준 시점 이전 최신 snapshot을 먼저 읽고, 이후 이벤트만 replay한다. snapshot이 없으면 전체 이벤트 replay로 복원한다.

`restoredFromSnapshot = true`일 때 `appliedEventCount`는 snapshot 이후 실제로 replay한 delta 이벤트 수를 의미한다.

Snapshot은 복원 최적화 캐시라서 snapshot read에 실패하면 event log 전체 replay로 fallback한다.

```http
POST /sessions/{sessionId}/snapshots
```

Snapshot은 최신 `serverSequence` 기준으로 생성한다. 같은 sequence의 snapshot이 이미 있으면 새 row를 만들지 않고 기존 snapshot을 `200`으로 반환한다.

## OpenAPI

요청/응답 schema는 [openapi/openapi.yaml](../openapi/openapi.yaml)을 기준으로 한다.
