package com.domain.taskflow.worker;

import com.domain.taskflow.domain.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 배치 작업 시뮬레이션 Job (테스트 용도)
 */
@Component("BATCH_SIM")
public class BatchSimHandler implements JobHandler {

    private final ObjectMapper om = new ObjectMapper();

    /**
     * payload 예: {"sleepMs":1500,"shouldFail":false}
     *
     * @param job
     * @throws Exception
     */
    @Override
    public void execute(Job job, int attemptNo) throws Exception {
        JsonNode root = om.readTree(job.getPayload());
        long sleepMs = root.has("sleepMs") ? root.get("sleepMs").asLong() : 200;
        int failTimes = root.has("failTimes") ? root.get("failTimes").asInt() : 0;

        Thread.sleep(sleepMs);

        // attemptNo가 failTimes 이하인 동안 실패
        if (attemptNo <= failTimes) {
            throw new RuntimeException("BATCH_SIM forced failure. attemptNo=" + attemptNo);
        }
    }
}
