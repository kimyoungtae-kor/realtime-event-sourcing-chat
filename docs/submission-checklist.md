# Submission Checklist

과제 요구사항과 현재 제출물을 매핑한 문서다.

## 4.1 필수 구현

| 요구사항 | 상태 | 근거 |
| --- | --- | --- |
| 실시간 기반 메시지 송수신 | DONE | Spring WebSocket + STOMP, `/ws`, `/app/sessions/{id}/messages`, `/topic/sessions/{id}` |
| 사용자 join / leave 처리 | DONE | `POST /sessions/{id}/join`, `POST /sessions/{id}/leave`, `JOINED`, `LEFT` 이벤트 |
| 기본 presence 처리 | DONE | `DISCONNECTED`, `RECONNECTED`, participant `ONLINE/OFFLINE/LEFT`, session `ACTIVE/INTERRUPTED/COMPLETED` |
| 이벤트 또는 메시지 수집 API | DONE | `POST /sessions/{id}/events`, WebSocket message/presence send |
| 중복 이벤트 방지 또는 중복 반영 최소화 | DONE | `(session_id, sender_id, client_event_id)` unique key, duplicate request는 기존 이벤트 반환 |
| 순서 뒤바뀜 처리 기준 정의 및 반영 | DONE | `sessions.next_sequence` + pessimistic lock으로 `serverSequence` 할당, replay는 `serverSequence asc` |
| 특정 시점 기준 세션 상태 복원 API | DONE | `GET /sessions/{id}/timeline?at=...`, full replay 또는 snapshot + delta replay |

## DB 설계 제출물

| 요구사항 | 상태 | 파일 |
| --- | --- | --- |
| ERD 또는 테이블 관계 설명 | DONE | `docs/erd.md` |
| 핵심 테이블 DDL | DONE | `docs/ddl.sql`, `src/main/resources/db/migration/V1__init_schema.sql` |
| 주요 조회 인덱스 설계 근거 | DONE | `docs/queries.md` |
| 정규화/비정규화/JSON 선택 근거 | DONE | `docs/erd.md`, `docs/event-sourcing.md` |

## REST API 설계 제출물

| 요구사항 | 상태 | 파일 |
| --- | --- | --- |
| OpenAPI | DONE | `openapi/openapi.yaml` |
| 세션 생성 | DONE | `POST /sessions` |
| 참여 | DONE | `POST /sessions/{id}/join` |
| 이벤트/메시지 수집 | DONE | `POST /sessions/{id}/events` |
| 종료 | DONE | `POST /sessions/{id}/end` |
| 세션 목록 조회 | DONE | `GET /sessions` |
| 특정 시점 상태 복원 | DONE | `GET /sessions/{id}/timeline?at=...` |
| Snapshot 생성 | DONE | `POST /sessions/{id}/snapshots` |
| 이벤트 조회 | DONE | `GET /sessions/{id}/events?afterSequence=...` |

## 설계 문서

| 요구사항 | 상태 | 파일 |
| --- | --- | --- |
| 재연결 시 데이터 정합성 | DONE | `docs/api.md`, `docs/operations.md` |
| 서버 수평 확장 시 세션 분산 및 상태 저장 | DONE | `docs/architecture.md`, `docs/operations.md` |
| 관측 가능성 설계 | DONE | `docs/operations.md` |
| 비동기 처리 구조 | DONE | `docs/async-projection.md`, `docs/operations.md` |
| 장애 대응 시나리오 | DONE | `docs/troubleshooting.md`, `docs/operations.md` |

## 이벤트 기반 상태 복원

| 요구사항 | 상태 | 근거 |
| --- | --- | --- |
| 참여자 목록 복원 | DONE | timeline replay에서 `JOINED`, `LEFT`, `DISCONNECTED`, `RECONNECTED` 반영 |
| 메시지 목록 복원 | DONE | `MESSAGE_SENT` payload 기반 복원 |
| 메시지 상태 | DONE | 현재 구현은 `SENT` 상태 |
| 전체 replay 전략 | DONE | snapshot이 없으면 full replay |
| snapshot + replay 전략 | DONE | snapshot이 있으면 snapshot 이후 event만 replay |
| 중복/순서 뒤바뀜 처리 | DONE | idempotency key와 `serverSequence` 기준 |
| 복원 비용과 성능 고려 | DONE | `docs/queries.md`, `docs/event-sourcing.md` |

## 6. 제출물 체크리스트

| 제출물 | 상태 | 파일 |
| --- | --- | --- |
| README | DONE | `README.md` |
| API 명세 | DONE | `openapi/openapi.yaml` |
| ERD + 핵심 DDL | DONE | `docs/erd.md`, `docs/ddl.sql` |
| 주요 쿼리 2~3개 + 인덱스 근거 + 병목 설명 | DONE | `docs/queries.md` |
| 설계 문서 | DONE | `docs/architecture.md`, `docs/operations.md`, `docs/troubleshooting.md`, `docs/async-projection.md` |
| 이벤트 기반 상태 복원 설계 또는 구현 결과 | DONE | `docs/event-sourcing.md`, `TimelineReplayService` |
| Snapshot/Projection 고도화 | PARTIAL/DONE | Snapshot API 구현, async projection은 문서 설계 |
| 부하 테스트 결과 | DONE | `docs/load-test.md`, `scripts/load-test.ps1` |
| 대시보드 | NOT INCLUDED | 비목표로 제외 |
| 추가 통신 방식 검토 | NOT INCLUDED | WebSocket/STOMP에 집중 |

## ERD 확인 방법

`docs/erd.md`는 Mermaid ER diagram으로 작성되어 있다.

확인 방법:

1. GitHub에서 `docs/erd.md`를 열면 Mermaid ERD가 자동 렌더링된다.
2. VS Code에서는 Markdown Preview Mermaid Support 확장을 설치하거나 GitHub 웹 화면에서 확인한다.
3. 필요하면 Mermaid Live Editor에 코드 블록 내용을 붙여 PNG/SVG로 export한다.

Mermaid Live Editor:

```text
https://mermaid.live/
```

현재 제출은 `docs/erd.md` 텍스트 자체가 ERD 원본이므로 별도 이미지 파일이 없어도 된다.
