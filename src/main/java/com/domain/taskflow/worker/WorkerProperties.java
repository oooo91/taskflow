package com.domain.taskflow.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.worker")
@Getter
@Setter
public class WorkerProperties {
    private String id = "worker-1";
}
