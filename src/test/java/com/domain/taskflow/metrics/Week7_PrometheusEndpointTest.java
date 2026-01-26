package com.domain.taskflow.metrics;

import com.domain.taskflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=prometheus",
        "management.endpoint.prometheus.access=read-only",
        "management.prometheus.metrics.export.enabled=true"
})
@Tag("integration")
@AutoConfigureMockMvc
public class Week7_PrometheusEndpointTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void prometheusEndpoint_containsCustomMetrics() throws Exception {
        var res = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("taskflow_job_total");
    }
}
