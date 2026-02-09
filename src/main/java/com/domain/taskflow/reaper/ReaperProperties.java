package com.domain.taskflow.reaper;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.reaper")
@Getter
@Setter
public class ReaperProperties {
    private long intervalMs = 1_000;
    private long staleRunningMs = 15_000;
}
