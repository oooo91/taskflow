package com.domain.taskflow.retry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.retry")
@Getter
@Setter
public class RetryProperties {
    private long baseMs = 1000;
    private long capMs = 30_000;
}
