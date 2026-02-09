package com.domain.taskflow.chaos;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/chaos")
public class ChaosController {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong dbDelayMs = new AtomicLong(0);

    // DB에 직접 지연을 발생시키는 엔드포인트(즉시 sleep)
    @PostMapping("/db/sleep")
    public ResponseEntity<?> dbSleep(@RequestParam("ms") long ms) {
        double sec = ms / 1000.0;
        jdbcTemplate.queryForObject("select pg_sleep(?)", Object.class, sec);
        return ResponseEntity.ok().build();
    }

    // 워커가 주기적으로 참조할 '지연 설정값' (지속 주입용)
    @PostMapping("/db/delay-ms")
    public ResponseEntity<?> setDbDelay(@RequestParam("ms") long ms) {
        dbDelayMs.set(ms);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/db/delay-ms")
    public ResponseEntity<?> getDbDelay() {
        return ResponseEntity.ok("{\"delayMs\":" + dbDelayMs.get() + "}");
    }

    public long currentDbDelayMs() {
        return dbDelayMs.get();
    }
}
