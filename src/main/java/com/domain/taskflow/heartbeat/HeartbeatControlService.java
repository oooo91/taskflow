package com.domain.taskflow.heartbeat;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HeartbeatControlService {

    private final Set<String> pausedWorkers = ConcurrentHashMap.newKeySet();

    public void pause(String workerId) {
        pausedWorkers.add(workerId);
    }

    public void resume(String workerId) {
        pausedWorkers.remove(workerId);
    }

    public boolean isPaused(String workerId) {
        return pausedWorkers.contains(workerId);
    }
}
