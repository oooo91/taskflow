package com.domain.taskflow.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redis;
    private final RedisScript<Long> unlockScript;

    public boolean tryLock(String key, String owner, Duration ttl) {
        // SET key owner NX PX ttl
        Boolean ok = redis.opsForValue().setIfAbsent(key, owner, ttl);
        log.info("tryLock key={}, owner={}, ok={}", key, owner, ok);

        return Boolean.TRUE.equals(ok);
    }

    public void unlock(String key, String owner) {
        // KEYS[1] = key, ARGV[1] = owner
        Long result = redis.execute(unlockScript, List.of(key), owner);

        // result: 1 -> 삭제됨(내 락 맞음), 0 -> 내 락 아니거나 이미 만료/없음
        // 운영 시 디버깅 필요하면 로그 정도만 추가해도 충분할듯
        if (result == null || result == 0L) {
            log.warn("이미 다른 락이 점유 중이거나 TTL이 만료되었습니다.");
        }
    }

    public String jobLockKey(String jobId) {
        return "lock:job:" + jobId;
    }
}
