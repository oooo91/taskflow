package com.domain.taskflow.repo;

import com.domain.taskflow.domain.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, UUID> {
    long countByJobId(UUID jobId);

    List<JobAttempt> findByJobIdOrderByAttemptNoAsc(UUID jobId);
}
