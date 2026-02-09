package com.domain.taskflow.repo;

import com.domain.taskflow.domain.JobAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobAttemptRepository extends JpaRepository<JobAttempt, UUID> {
    long countByJobId(UUID jobId);
    Optional<JobAttempt> findByJobIdAndAttemptNo(UUID jobId, int attemptNo);
    List<JobAttempt> findByJobIdOrderByAttemptNoAsc(UUID jobId);
}
