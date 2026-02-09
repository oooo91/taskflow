package com.domain.taskflow.sse;

import com.domain.taskflow.domain.JobEvent;
import com.domain.taskflow.repo.JobEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SseController {

    private final JobEventRepository jobEventRepository;
    private final SseHub sseHub;

    @GetMapping("/stream/jobs")
    public SseEmitter streamJobs(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        long lastSeq = 0L;
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                lastSeq = Long.parseLong(lastEventId.trim());
            } catch (NumberFormatException ignored) {

            }
        }
        SseEmitter emitter = sseHub.createEmitter();

        // 리플레이(유실 방지): lastSeq 이후 이벤트를 먼저 보내고 실시간으로 이어지게 하기
        List<JobEvent> backlog = jobEventRepository.findAfterSeq(lastSeq, PageRequest.of(0, 1000));
        for (JobEvent e : backlog) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(e.getSeq()))
                        .name(e.getEventType())
                        .data(e.getPayload()));
            } catch (IOException ex) {
                // 연결 끊기면 바로 종료
                emitter.complete();
                return emitter;
            }
        }
        return emitter;
    }

}
