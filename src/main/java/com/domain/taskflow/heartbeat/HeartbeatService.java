package com.domain.taskflow.heartbeat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class HeartbeatService {

    private final StringRedisTemplate redis;

    public String key(String workerId) {
        return "hb:worker:" + workerId;
    }

    public void beat(String workerId, Duration ttl) {
        String k = key(workerId);
        String v = OffsetDateTime.now().toString();
        redis.opsForValue().set(k, v, ttl);
    }

    public boolean isAlive(String workerId) {
        return Boolean.TRUE.equals(redis.hasKey(key(workerId)));
    }

    public void delete(String workerId) {
        redis.delete(key(workerId));
    }

}
