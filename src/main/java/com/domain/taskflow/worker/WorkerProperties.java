package com.domain.taskflow.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskflow.worker")
@Getter
@Setter
public class WorkerProperties {
    // 클라우드 올릴 떈 host/pod 로 변경
    private String id = "worker-1";

    // 동시 스레드 수
    private int poolSize = 4;

    // 폴링 주기 (ms)
    private int pollMs = 5000;

    // 한 번에 가져올 후보 개수
    private int fetchSize = 10;

    // Redis 락 lease (ms)
    private long lockLeaseMs = 30_000;
}
