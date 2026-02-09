package com.domain.taskflow.heartbeat;

import com.domain.taskflow.worker.WorkerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final HeartbeatService heartbeatService;
    private final WorkerProperties workerProperties;
    private final HeartbeatProperties heartbeatProperties;
    private final HeartbeatControlService heartbeatControlService;

    @Scheduled(fixedDelayString = "${taskflow.heartbeat.interval-ms:2000}")
    public void tick() {
        String workerId = workerProperties.getId();

        // pause 상태면 beat를 아예 하지 않음 → TTL 만료 유도 가능
        if (heartbeatControlService.isPaused(workerId)) {
            return;
        }

        heartbeatService.beat(
                workerProperties.getId(),
                Duration.ofMillis(heartbeatProperties.getTtlMs())
        );
    }
}
