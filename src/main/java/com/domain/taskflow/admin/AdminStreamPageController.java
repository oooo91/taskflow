package com.domain.taskflow.admin;


import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminStreamPageController {
    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_HTML_VALUE)
    public String page() {
        return """
        <!doctype html>
        <html lang="ko">
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1"/>
          <title>TaskFlow Admin Stream</title>
          <style>
            body { font-family: ui-sans-serif, system-ui; padding: 16px; }
            .row { display:flex; gap:12px; align-items:center; }
            #log { white-space: pre-wrap; border:1px solid #ddd; padding:12px; height: 70vh; overflow:auto; border-radius:8px; }
            button { padding:8px 12px; }
            .muted { color:#666; font-size: 12px; }
          </style>
        </head>
        <body>
          <h2>TaskFlow Admin SSE Stream</h2>
          <div class="row">
            <button id="connect">Connect</button>
            <button id="disconnect">Disconnect</button>
            <span class="muted" id="status">disconnected</span>
          </div>
          <p class="muted">Shows events from <code>/stream/jobs</code>. Reconnect uses browser Last-Event-ID automatically.</p>
          <div id="log"></div>

          <script>
            let es = null;
            const log = document.getElementById('log');
            const status = document.getElementById('status');

            function append(line) {
              log.textContent += line + "\\n";
              log.scrollTop = log.scrollHeight;
            }

            document.getElementById('connect').onclick = () => {
              if (es) return;
              status.textContent = "connecting...";
              es = new EventSource('/stream/jobs');

              es.onopen = () => {
                status.textContent = "connected";
                append("[OPEN]");
              };

              es.onerror = () => {
                status.textContent = "error (auto-retry...)";
                append("[ERROR] (browser will retry)");
              };

              // default message handler
              es.onmessage = (e) => {
                append(`[message] id=${e.lastEventId} data=${e.data}`);
              };

              // status changed 이벤트 명시 처리(서버에서 name=STATUS_CHANGED로 보내는 경우)
              es.addEventListener('STATUS_CHANGED', (e) => {
                append(`[STATUS_CHANGED] id=${e.lastEventId} data=${e.data}`);
              });

              es.addEventListener('CONNECTED', (e) => {
                append(`[CONNECTED] ${e.data}`);
              });
            };

            document.getElementById('disconnect').onclick = () => {
              if (!es) return;
              es.close();
              es = null;
              status.textContent = "disconnected";
              append("[CLOSE]");
            };
          </script>
        </body>
        </html>
        """;
    }
}
