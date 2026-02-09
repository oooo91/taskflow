package com.domain.taskflow.admin;

import com.domain.taskflow.heartbeat.HeartbeatControlService;
import com.domain.taskflow.heartbeat.HeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/workers")
@RequiredArgsConstructor
public class AdminWorkerHeartbeatController {

    private final HeartbeatControlService controlService;
    private final HeartbeatService heartbeatService;

    @PostMapping("/{workerId}/heartbeat/pause")
    public ResponseEntity<?> pause(@PathVariable("workerId") String workerId) {
        controlService.pause(workerId);
        // 즉시 만료시키기 위해 키도 같이 삭제
        heartbeatService.delete(workerId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{workerId}/heartbeat/resume")
    public ResponseEntity<?> resume(@PathVariable("workerId") String workerId) {
        controlService.resume(workerId);
        return ResponseEntity.ok().build();
    }

}
