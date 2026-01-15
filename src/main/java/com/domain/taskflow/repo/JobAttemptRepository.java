package com.domain.taskflow.repo;

import com.domain.taskflow.domain.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, UUID> {
    long countByJobId(UUID jobId);
}
