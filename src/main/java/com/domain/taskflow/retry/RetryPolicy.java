package com.domain.taskflow.retry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RetryPolicy {

    private final RetryProperties properties;
    private final Set<String> retryableCodes = Set.of("HTTP_5XX", "TIMEOUT", "EXEC_ERROR");

    /**
     * 초반엔 빠르게 재시도 -> 계속 실패할 시 천천히 재시도하여 부하 적게하기 위함
     * 재시도 는 최대 30초
     *
     * @param attemptNo
     * @param now
     * @return
     */
    public OffsetDateTime computeNextRunAt(int attemptNo, OffsetDateTime now) {
        // attemptNo가 1부터 시작이면: 1,2,4,8... 만들려면 (attemptNo - 1)
        long delay = getBaseMs() * (1L << Math.min(attemptNo - 1, 10));

        long capped = Math.min(delay, getCapMs());
        return now.plus(Duration.ofMillis(capped));
    }

    // backoff: base 1s, 2s, 4s, 8s (최대 30s cap)
    public long getBaseMs() {
        return properties.getBaseMs();
    }

    // maxMs: 최대 wait
    public long getCapMs() {
        return properties.getCapMs();
    }

    public boolean isRetryable(String errorCode) {
        return retryableCodes.contains(errorCode);
    }

}
