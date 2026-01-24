package com.domain.taskflow.admin;

import com.domain.taskflow.heartbeat.HeartbeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * worker heartbeat 삭제 (워커 죽음 시뮬레이션)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/workers")
public class AdminWorkerController {

    private final HeartbeatService heartbeatService;

    @DeleteMapping("/{workerId}/heartbeat")
    public ResponseEntity<?> deleteHeartbeat(@PathVariable("workerId") String workerId) {
        heartbeatService.delete(workerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{workerId}/alive")
    public ResponseEntity<?> isAlive(@PathVariable("workerId") String workerId) {
        return ResponseEntity.ok("{\"alive\":" + heartbeatService.isAlive(workerId) + "}");
    }
}
