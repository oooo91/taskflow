package com.domain.taskflow.admin;

import com.domain.taskflow.reaper.StaleJobReaper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod")
@ConditionalOnProperty(
        name = "taskflow.chaos.enabled",
        havingValue = "true"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/reaper")
public class AdminReaperController {

    private final StaleJobReaper reaper;

    @PostMapping("/run")
    public ResponseEntity<?> runOnce() {
        reaper.tick();
        return ResponseEntity.ok().build();
    }
}
