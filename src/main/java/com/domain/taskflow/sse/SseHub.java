package com.domain.taskflow.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseHub {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter() {
        // 0 = timeout 없음 무제한
        SseEmitter emitter = new SseEmitter(0L);
        String id = UUID.randomUUID().toString();
        emitters.put(id, emitter);

        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(e -> emitters.remove(id));

        // 최초 연결 확인용 ping
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (IOException ignored) {

        }
        return emitter;
    }

    public void broadcast(long seq, String eventType, String payloadJson) {
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(
                        SseEmitter.event()
                                .id(String.valueOf(seq)) // 재연결 커서
                                .name(eventType) // STATUS_CHANGED 등
                                .data(payloadJson)
                );
            } catch (IOException e) {
                // 끊긴 emitter 정리하기
                emitters.remove(entry.getKey());
            }
        }
    }
}
