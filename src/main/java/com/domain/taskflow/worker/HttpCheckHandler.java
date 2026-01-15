package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCheckHandler implements JobHandler {

    private final ObjectMapper om = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    /**
     * payload 예: {{"url":"https://example.com","method":"GET"}}
     *
     * @param job
     * @throws Exception
     */
    @Override
    public void execute(Job job) throws Exception {
        JsonNode root = om.readTree(job.getPayload());
        String url = root.get("url").asText();
        String method = root.has("method") ? root.get("method").asText() : "GET";

        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

        ResponseEntity<Void> resp = restClient.method(httpMethod).uri(url).retrieve().toBodilessEntity();
        int status = resp.getStatusCode().value();

        if (status < 200 || status >= 300) {
            throw new RuntimeException("HTTP_CHECK failed: status=" + status);
        }

    }
}
