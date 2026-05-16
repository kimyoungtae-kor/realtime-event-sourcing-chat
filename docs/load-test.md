# Load Test

무거운 외부 도구 없이 로컬에서 바로 재현할 수 있는 작은 부하 테스트 스크립트를 둔다.

스크립트:

```text
scripts/load-test.ps1
```

## 실행 방법

애플리케이션과 MySQL이 실행 중이어야 한다.

```powershell
docker compose --env-file .env up -d mysql
.\gradlew.bat bootRun
```

다른 터미널에서 실행한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\load-test.ps1 `
  -BaseUrl http://localhost:8080 `
  -SessionCount 5 `
  -MessagesPerSession 20 `
  -OutputPath build\load-test-result.json
```

## 시나리오

세션마다 아래 요청을 순서대로 수행한다.

1. `POST /sessions`
2. `POST /sessions/{sessionId}/join`
3. `POST /sessions/{sessionId}/events` message N건
4. 첫 번째 message 재전송으로 duplicate 확인
5. `POST /sessions/{sessionId}/snapshots`
6. `GET /sessions/{sessionId}/timeline?at=...`

확인하는 내용:

- event append latency
- duplicate request가 `duplicate=true`로 처리되는지
- snapshot 생성이 성공하는지
- snapshot 이후 timeline restore가 성공하는지
- 전체 request 성공/실패 수

## 2026-05-16 로컬 실행 결과

실행 조건:

- Spring Boot local server: `http://localhost:8080`
- MySQL: Docker Compose local MySQL
- Session count: `5`
- Messages per session: `20`
- Total requests: `125`

결과:

```json
{
  "totalRequests": 125,
  "successfulRequests": 125,
  "failedRequests": 0,
  "elapsedMs": 4943.04,
  "requestsPerSecond": 25.29,
  "latencyMs": {
    "min": 12.04,
    "p50": 35.27,
    "p95": 51.2,
    "max": 171.89
  }
}
```

Endpoint별 p95:

| Step | Count | Failed | p95 latency |
| --- | ---: | ---: | ---: |
| create-session | 5 | 0 | 128.31 ms |
| join | 5 | 0 | 42.52 ms |
| message | 100 | 0 | 50.53 ms |
| duplicate-message | 5 | 0 | 27.68 ms |
| snapshot | 5 | 0 | 41.17 ms |
| timeline | 5 | 0 | 83.55 ms |

## 결과 파일

기본 결과 파일은 `build/load-test-result.json`이다. `build/`는 커밋하지 않는다.

결과 형식:

```json
{
  "sessionCount": 5,
  "messagesPerSession": 20,
  "totalRequests": 120,
  "successfulRequests": 120,
  "failedRequests": 0,
  "requestsPerSecond": 25.3,
  "latencyMs": {
    "p50": 12.4,
    "p95": 41.8
  }
}
```

## 해석 기준

이 스크립트는 정식 벤치마크가 아니라 로컬 재현용 smoke load test다.

운영 성능 측정으로 확장한다면 k6, Gatling, JMeter 중 하나를 사용하고, 아래 지표를 함께 본다.

- DB connection pool usage
- event append p95/p99 latency
- duplicate event count
- timeline replay latency
- snapshot restore hit ratio
- WebSocket active connection count
