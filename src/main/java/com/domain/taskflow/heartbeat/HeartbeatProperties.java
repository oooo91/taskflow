package com.domain.taskflow.heartbeat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.heartbeat")
@Getter
@Setter
public class HeartbeatProperties {
    private long ttlMs = 10_000;
    private long intervalMs = 2_000;
}
