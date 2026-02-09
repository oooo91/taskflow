# TaskFlow  
### 운영 가능한 Job 실행 플랫폼  
*(Concurrency · Retry · Recovery · Observability)*

> 단순한 비동기 처리기가 아닌,  
> **실패·중복·장애를 전제로 설계된 Job 실행 및 운영 플랫폼**

---

## 1. 문제 정의 (Why)

실무에서 병렬/비동기 작업 시스템은 다음 문제를 반복적으로 겪는다.

- 동일 작업이 **중복 실행**된다
- 실패가 발생하면 **작업이 유실되거나 RUNNING 상태로 고착**된다
- 워커가 죽으면 **누가 어디까지 실행했는지 알 수 없다**
- 재시도 기준이 불명확해 **운영자가 수동 개입**하게 된다
- 상태 변경이 유실되어 **운영자가 현재 상황을 파악할 수 없다**
- 장애를 **재현·측정·검증할 방법이 없다**

TaskFlow는 이 문제들을  
**“기능 추가”가 아니라 “구조적 설계”로 해결하는 것**을 목표로 한다.

---

## 2. 프로젝트 목표

- 실패를 예외가 아닌 **정상 흐름으로 다룬다**
- 동시 요청/실행에서도 **정합성을 보장한다**
- 워커/인프라 장애 상황에서도 **자동으로 복구된다**
- 운영자가 시스템 상태를 **실시간으로 관측**할 수 있다
- 설계가 **부하·장애 실험으로 검증**된다

---

## 3. 전체 아키텍처

```text
[Client / Internal Service / Admin]
        |
        | REST API
        v
+---------------------+
| API Server          |
| (Spring Boot)       |
|---------------------|
| - Idempotency       |
| - State Machine     |
| - Outbox Write      |
+----------+----------+
           |
           v
+---------------------+        +---------------------+
| PostgreSQL          |        | Redis               |
|---------------------|        |---------------------|
| jobs                |        | lock:job:{id}       |
| job_attempts        |        | hb:worker:{id}      |
| job_events(outbox)  |        |                     |
+----------+----------+        +----------+----------+
           |                              |
           | poll                         | heartbeat
           v                              v
+---------------------+        +---------------------+
| Outbox Publisher    |        | Worker              |
|---------------------|        |---------------------|
| - poll outbox       |        | - pick PENDING      |
| - SSE push          |        | - CAS RUNNING       |
| - mark published    |        | - execute job       |
+----------+----------+        | - retry / backoff   |
           |                   | - heartbeat 갱신    |
           v                   +----------+----------+
+---------------------+                   |
| Admin UI (SSE)      |                   v
+---------------------+           External Targets
```

---

## 4. 데이터 모델 (ERD)

```text
    JOBS {
        UUID id PK
        STRING job_key "UNIQUE"
        STRING type
        JSON payload
        STRING status
        TIMESTAMP scheduled_at
        TIMESTAMP next_run_at
        INT attempt_count
        INT max_attempts
        STRING worker_id
        INT version
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    JOB_ATTEMPTS {
        UUID id PK
        UUID job_id FK
        INT attempt_no
        STRING status
        STRING worker_id
        STRING error_code
        STRING error_message
        TIMESTAMP started_at
        TIMESTAMP ended_at
    }

    JOB_EVENTS {
        UUID id PK
        UUID job_id FK
        STRING event_type
        JSON payload
        TIMESTAMP published_at
        TIMESTAMP created_at
    }

    JOBS ||--o{ JOB_ATTEMPTS : has
    JOBS ||--o{ JOB_EVENTS : emits

```

--- 

## 5. 상태 흐름도
- 상태 전이는 Service Layer 단일 지점에서만 수행
- RUNNING 전환은 DB CAS (UPDATE … WHERE status = PENDING) 로 강제
- 모든 상태 변경은 Outbox 이벤트로 기록

```text
PENDING
   |
   | (worker DB CAS)
   v
RUNNING
   |
   | success
   v
SUCCESS

RUNNING
   |
   | failure (retryable)
   v
RETRY_WAIT --(scheduler)--> PENDING

RUNNING
   |
   | failure (non-retryable or max attempts)
   v
FAILED

PENDING / RUNNING
   |
   | cancel request
   v
CANCELED

```

--- 

## 6. 기능
- Job 생성 / 조회 / 취소 API
- job_key 기반 멱등성 보장
- 병렬 Worker 실행
- 재시도 정책 (exponential backoff + maxAttempts)
- heartbeat 기반 stale job 회수
- Outbox + SSE 실시간 상태 스트리밍

--- 

## 7. 정합성 고려
- Job 생성 시 job_key UNIQUE 제약으로 중복 생성 방지
- Job 실행 시 DB CAS로 RUNNING 상태 획득
- Redis Lock으로 실행 중복 방지
- Redis 장애 시에도 DB CAS 기반 정합성 유지

--- 

## 8. 장애 복구
- Worker는 주기적으로 heartbeat를 Redis에 갱신
- heartbeat age + RUNNING duration 기준으로 stale job 판단
- stale job을 RETRY_WAIT 또는 FAILED 상태로 회수
- 워커 재기동 시 작업 자동 복구

--- 

## 9. 이벤트 전달
- 모든 상태 변경을 Outbox 테이블에 이벤트로 저장
- Publisher가 Outbox를 polling하여 SSE로 전송
- published_at 컬럼으로 이벤트 중복 전송 방지
- 운영자는 Admin UI에서 실시간 상태 관측 가능

--- 

## 10. 검증
- 부하 테스트
  - k6 기반 동시 Job 생성 시나리오
  - 실패 + 재시도 혼합 시나리오

- 장애 실험
  - 워커 강제 종료 → stale job 회수
  - Redis 일시 중단 → DB CAS 기반 실행 유지
  - DB 지연 → 재시도 및 상태 안정성 유지

--- 

## 11. 실행 
- 로컬
```
docker compose up -d
./gradlew bootRun
```

- 운영
  - local → develop → main PR merge
  - GitHub Actions 자동 배포
  - Docker 이미지 빌드 → GHCR push → AWS EC2 배포
 
--- 

## 12. 데모 시나리오 
1. /admin/stream 접속 (SSE 실시간 상태 확인)
2. 정상 Job 생성 → RUNNING → SUCCESS
3. 실패 Job 생성 → RETRY_WAIT → 재시도 → SUCCESS
4. 워커 강제 종료 → stale job 회수 확인
5. /actuator/prometheus 메트릭 확인
