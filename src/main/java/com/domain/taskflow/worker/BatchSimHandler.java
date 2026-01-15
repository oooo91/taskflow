package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 배치 작업 시뮬레이션 Job (테스트 용도)
 */
@Component
public class BatchSimHandler implements JobHandler {

    private final ObjectMapper om = new ObjectMapper();

    /**
     * payload 예: {"sleepMs":1500,"shouldFail":false}
     *
     * @param job
     * @throws Exception
     */
    @Override
    public void execute(Job job) throws Exception {
        JsonNode root = om.readTree(job.getPayload());
        long sleepMs = root.has("sleepMs") ? root.get("sleepMs").asLong() : 500;
        boolean shouldFail = root.has("shouldFail") && root.get("shouldFail").asBoolean();

        Thread.sleep(sleepMs);

        if (shouldFail) {
            throw new RuntimeException("BATCH_SIM forced failure");
        }
    }
}
