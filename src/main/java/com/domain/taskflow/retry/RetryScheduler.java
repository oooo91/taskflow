package com.domain.taskflow.retry;

import com.domain.taskflow.repo.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class RetryScheduler {

    private final JobRepository jobRepository;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void tick() {
        jobRepository.releaseRetryWaitToPending(OffsetDateTime.now());
    }
}
