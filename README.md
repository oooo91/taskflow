# TaskFlow  
실패, 중복, 장애를 전제로 설계한 JOB 실행 및 운영 플랫폼


## 1. 문제 정의
실무에서 병렬/비동기 작업 시스템은 다음 문제를 반복적으로 겪음

- 동일 작업이 중복 실행된다
- 실패가 발생하면 작업이 유실되거나 RUNNING 상태로 고착된다
- 워커가 죽으면 누가 어디까지 실행했는지 알 수 없다
- 재시도 기준이 불명확해 운영자가 수동 개입하게 된다
- 상태 변경이 유실되어 현재 상황을 한눈에 파악할 수 없다
- 장애를 재현, 측정, 검증할 방법이 없다


## 2. 프로젝트 배경
이전 실무에서는 로컬 실행기에서 브라우저/디바이스 기반 작업을 무인으로 실행하고  
중앙 서버가 실행 결과와 실패 원인을 수집/저장하는 구조를 운영했다. (중앙 서버와 로컬 실행기(Agent)가 분리된 구조)

이 과정에서 다음과 같은 한계를 반복적으로 경험했다.
- 실패 원인이 네트워크, 세션, 외부 의존성 등으로 매우 다양함
- 실패 처리/재시도/복구 로직이 실행기와 서버에 분산되어 유지보수 비용이 누적됨
- 문제 발생 시 로그를 확인하고 재현을 시도해야만 원인을 추적할 수 있음
- 전체 시스템 상태를 중앙에서 요약해 관측하기 어려움

TaskFlow는 이러한 경험을 바탕으로,  
분산 실행 환경에서 발생하는 실패를 안정적으로 관측하고,  
실행과 운영 책임을 분리해 유지보수 비용을 줄이는 실행 플랫폼을 목표로 한다.


## 3. 프로젝트 목표

- 실패를 예외가 아닌 정상 흐름으로 처리
- 동시 요청/병렬 실행 환경에서도 정합성 보장
- 워커/인프라 장애 상황에서도 자동 복구 가능
- 운영자가 시스템 상태를 실시간으로 관측
- 설계를 부하/장애 실험으로 검증 가능



## 4. 전체 아키텍처

```
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


## 5. 데이터 모델 (ERD)

```
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
```


## 6. 상태 흐름도
```
PENDING
   |
   | (worker DB CAS)
   v
RUNNING
   |
   | success
   v
SUCCESS
--------------------------------------
RUNNING
   |
   | failure (retryable)
   v
RETRY_WAIT --(scheduler)--> PENDING
--------------------------------------
RUNNING
   |
   | failure (non-retryable or max attempts)
   v
FAILED
--------------------------------------
PENDING / RUNNING
   |
   | cancel request
   v
CANCELED

```
- 상태 전이는 서비스 계층 단일 지점에서만 수행
- RUNNING 전환은 DB CAS (UPDATE … WHERE status = PENDING) 로 강제
- 모든 상태 변경은 Outbox 이벤트로 기록


## 7. 기능
- Job 생성 / 조회 / 취소 API
- job_key 기반 멱등성 보장
- 병렬 Worker 실행
- 재시도 정책 (exponential backoff + maxAttempts)
- heartbeat 기반 stale job 회수
- Outbox + SSE 실시간 상태 스트리밍


## 8. 정합성 고려
- Job 생성 시 job_key UNIQUE 제약으로 중복 생성 방지
- Job 실행 시 DB CAS로 RUNNING 상태 획득
- Redis Lock으로 실행 중복 방지
- Redis 장애 시에도 DB CAS 기반 정합성 유지


## 9. 장애 복구
- Worker는 주기적으로 heartbeat를 Redis에 갱신
- heartbeat age + RUNNING duration 기준으로 stale job 판단
- stale job을 RETRY_WAIT 또는 FAILED 상태로 회수
- 워커 재기동 시 작업 자동 복구


## 10. 이벤트 전달
- 모든 상태 변경을 Outbox 테이블에 이벤트로 저장
- Publisher가 Outbox를 polling하여 SSE로 전송
- published_at 컬럼으로 이벤트 중복 전송 방지
- 운영자는 Admin UI에서 실시간 상태 관측 가능


## 11. 실험/검증
- 부하 테스트
  - k6 기반 동시 Job 생성 시나리오
  - 실패 + 재시도 혼합 시나리오

- 장애 실험
  - 워커 강제 종료 → stale job 회수
  - Redis 일시 중단 → DB CAS 기반 실행 유지
  - DB 지연 → 재시도 및 상태 안정성 유지

<details>
        <summary>접기/펼치기
        </summary> 



### 11.1 부하 테스트
#### 1) 동시 Job 생성 부하 테스트

- k6를 이용해 짧은 시간 동안 다수의 Job 생성 요청을 동시에 발생시켰다.
- 동일한 `job_key`를 포함한 요청을 병렬로 전송하여,
  동시 요청 환경에서도 Job이 중복 생성되지 않는지 확인했다.
- 테스트 결과, `job_key` 기반 멱등성 제약으로 Job은 단일로 생성되었으며,
  상태는 `PENDING → RUNNING → 종료 상태`로 정상 전이됨을 확인했다.

동시 요청 환경에서도
중복 실행 없이 정합성이 유지됨


#### 2) 실패 + 재시도 혼합 시나리오

- 일부 Job은 의도적으로 실패하도록 설정하여
  성공 Job과 실패 Job이 혼합된 상황을 만들었다.
- 실패한 Job 중 retryable error로 분류된 작업은
  `RETRY_WAIT` 상태로 전환되고,
  설정된 backoff 이후 재시도되는지 확인했다.
- 재시도 횟수를 초과한 Job은 `FAILED` 상태로 확정되었고,
  성공 Job은 실패 Job의 영향 없이 정상 종료되었다.

실패가 발생하더라도
시스템 전체 흐름이 중단되지 않고,
재시도 정책과 상태 머신이 정상 동작함


### 11.2 장애 실험 (Failure / Chaos Test)
#### 1) 워커 강제 종료 (Worker Failure)

- Job이 `RUNNING` 상태인 동안,
  해당 작업을 수행 중인 워커 프로세스를 강제로 종료했다.
- 워커의 heartbeat 갱신이 중단된 이후,
  일정 시간이 지나자 해당 Job이 stale 상태로 판단되었다.
- stale Job은 자동으로 회수되어
  `RETRY_WAIT` 또는 `FAILED` 상태로 전환됨을 확인했다.

워커 장애 상황에서도
RUNNING 상태 고착 없이 작업이 자동 복구됨

#### 2) Redis 일시 중단 (Auxiliary Infrastructure Failure)

- 실행 중 Redis를 일시적으로 중단시켜,
  락 및 heartbeat 기능이 동작하지 않는 상황을 만들었다.
- Redis 중단 상태에서도
  DB CAS 기반 상태 전이는 정상적으로 수행되었으며,
  중복 실행이나 상태 불일치가 발생하지 않음을 확인했다.

#### 3) DB 지연 상황 (Database Latency)

- DB에 인위적인 지연을 발생시켜
  일부 Job 실행이 timeout 또는 실패하도록 유도했다.
- 실패한 Job은 error code 기준으로 분류되었고,
  retryable error의 경우 재시도 흐름으로 정상 진입했다.
- 상태 전이가 꼬이거나 RUNNING 상태로 고착되는 현상은 발생하지 않았다.

### 11.3 검증 결과 요약

- 동시 요청 환경에서도 멱등성과 정합성이 유지됨
- 실패가 발생해도 시스템 흐름이 중단되지 않음
- 워커 및 보조 인프라 장애 상황에서도 자동 복구 가능
- 상태 전이와 재시도 정책이 운영 상황에서도 유효함


</details>

## 12. 실행 
- 로컬
```
docker compose up -d
./gradlew bootRun
```

- 운영
  - local → develop → main PR merge
  - GitHub Actions 자동 배포
  - Docker 이미지 빌드 → GHCR push → AWS EC2 배포
 

## 13. 데모 시나리오 
1. /admin/stream 접속 (SSE 실시간 상태 확인)
2. 정상 Job 생성 → RUNNING → SUCCESS
3. 실패 Job 생성 → RETRY_WAIT → 재시도 → SUCCESS
4. 워커 강제 종료 → stale job 회수 확인
5. /actuator/prometheus 메트릭 확인


## 14. 트러블슈팅
- 환경: 단일 EC2 + Docker Compose + Nginx Reverse Proxy
- 목표: 외부 노출은 80만 허용하고, 스트리밍(SSE)과 관측 도구는 최소 노출 원칙으로 구성

<details>
        <summary>접기/펼치기
        </summary> 



### 14.1 SSE 무한 로딩 (Admin Stream 연결 유지 실패)
#### 1) 증상
- /admin/stream에 접속해 connect 버튼을 눌러도 무한 로딩(연결이 유지되지 않음)

#### 2) 원인
- SSE는 “긴 연결 + 실시간 스트리밍” 특성상 프록시가 버퍼링/캐싱하거나 타임아웃을 걸면 연결이 끊기기 쉬움
- Nginx 기본 동작(버퍼링/읽기 타임아웃) 때문에 SSE 연결이 안정적으로 유지되지 않았음

#### 3) 해결
- Nginx에서 /stream/ (또는 스트림 관련 경로)에 대해 버퍼링/캐시를 끄고, read timeout을 길게 설정
- location /stream/ 블록을 location /보다 우선 적용되도록 위치 조정
```location /stream/ {
  proxy_pass http://taskflow_app;
  proxy_http_version 1.1;
  proxy_set_header Connection "";
  proxy_buffering off;
  proxy_cache off;
  proxy_read_timeout 3600s;

  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto $scheme;
}
```
#### 4) 확인
- Nginx reload/restart 후 브라우저에서 연결 유지 및 이벤트 수신 확인



### 14.2 Grafana/Prometheus UI 접근 (외부 노출 없이 안전하게)
#### 1) 증상
- 서비스 인바운드는 80만 열어둔 상태
- Grafana/Prometheus는 컨테이너 내부 포트만 열려 있어 외부에서 바로 접속 불가

#### 2) 원인
- Grafana/Prometheus는 같은 Docker 네트워크 내부에서만 접근하도록 구성
  - 예: Prometheus가 app:8081로 scrape 가능 (EC2 호스트 포트 노출 불필요)

#### 3) 해결
- Grafana/Prometheus는 외부 노출을 하지 않고, EC2 루프백(127.0.0.1)에만 바인딩해서 “서버 내부에서만 접근 가능”하게 구성
- 필요 시 로컬 PC에서 SSH 터널링으로 안전하게 접속
```# compose.prod.yml 예시
grafana:
  ports:
    - "127.0.0.1:3000:3000"
prometheus:
  ports:
    - "127.0.0.1:9090:9090"
```
로컬 pc 에서 터널링
```
ssh -i my-key.pem -L 3000:localhost:3000 -L 9090:localhost:9090 ubuntu@<EC2_PUBLIC_IP>
```
접속:

http://localhost:3000
 (Grafana)

http://localhost:9090
 (Prometheus)

왜 127.0.0.1 바인딩인가?

0.0.0.0:9090처럼 전체 인터페이스에 열면, 보안그룹 설정 실수 시 외부 노출 위험이 커짐
127.0.0.1은 루프백만 허용되어 외부에서 물리적으로 접근 불가(안전장치가 강함)


</details>


## 15. 수치/실험 6종 (정의 -> 실행 -> 기록)
### 1. 동시 실행 1000건(단일 JVM)
  - 목표: 워커 풀/폴링/페치/락 lease를 튜닝하여, 순간적으로 1000개 이상의 Job이 동시에 처리되는 상황에서 정합성(중복 실행 없음)과 관측을 확보
```
max_over_time(taskflow_jobs_running[10m])
```
### 2. 평균 처리 시간
  - 정의: attempt.started_at → attempt.ended_at (또는 job.running_started_at → terminal) 평균 + p95를 함께 보고
```
1000 * increase(taskflow_job_processing_seconds_sum{result="SUCCESS"}[10m])
     / increase(taskflow_job_processing_seconds_count{result="SUCCESS"}[10m])
```
### 3. 재시도 성공률
  - db 집계
     ```
        with scope as (
        select id
        from jobs
        where job_key like 'k6-retry-run1-%'
        ),
        retry_jobs as (
        select distinct a.job_id
        from job_attempts a
        join scope s on s.id = a.job_id
        where a.attempt_no >= 2
        )
        select
        count(*) as retry_targets,
        count(*) filter (where j.status='SUCCESS') as retry_success,
        count(*) filter (where j.status='FAILED') as retry_failed,
        round(100.0 * count(*) filter (where j.status='SUCCESS') / nullif(count(*),0), 2) as retry_success_rate_percent
        from retry_jobs r
        join jobs j on j.id = r.job_id;
        ```
  
  - PromQL
        ```
        increase(taskflow_job_retry_success_jobs_total[10m]) / clamp_min(increase(taskflow_job_retry_jobs_total[10m]), 1)
        ```

### 4. stale 회수 시간
   - 정의: RUNNING 시작 시각(job.running_started_at)부터 stale reaper가 'STALE 회수 이벤트'를 남길 때까지
   - 실험 방법: worker 2개 띄우고 한쪽 heartbeat를 끊기(또는 프로세스 kill) → reaper가 회수하는 시점을 측정.
        ```
        1000 *
        rate(taskflow_stale_reap_overshoot_seconds_sum[5m]) /
        clamp_min(rate(taskflow_stale_reap_overshoot_seconds_count[5m]), 1)
        ```



### 5. TPS 측정
   - (a) Ingress TPS(POST /jobs), (b) Processing TPS(성공+실패 완료)
        ```
        sum(rate(http_server_requests_seconds_count{uri="/jobs",method="POST",status=~"2.."}[1m]))
        sum(rate(taskflow_job_succeeded_total[1m])) + sum(rate(taskflow_job_failed_total[1m]))
        ```


