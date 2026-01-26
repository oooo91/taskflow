package com.domain.taskflow.metrics;

import com.domain.taskflow.support.IntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Tag("integration")
@AutoConfigureMockMvc
public class Week7_ChaosDbSleepTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Test
    void chaosDbSleep_shouldReturn200() throws Exception {
        mockMvc.perform(post("/admin/chaos/db/sleep?ms=50"))
                .andExpect(status().isOk());
    }
}
